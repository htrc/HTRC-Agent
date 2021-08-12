
/*
#
# Copyright 2013 The Trustees of Indiana University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/


package htrc.agent

// When a job submission is received the parameters are parsed
// (marshalled?) into this class. It can then be passed around and
// queried / filled in.

import scala.xml._
import scala.collection.mutable.HashMap
import scala.util.matching.Regex
import scala.util.matching.Regex._
import scala.util.Try
// import akka.event.LoggingAdapter

case class JobInputs(user: JobSubmission, system: JobProperties,
  jobResultCacheKey: Option[String], token: String,
  requestId: String, ip: String,
  lsCollectionMetadata: List[(String, Option[WorksetMetadata])] = List.empty) {

  override def toString: String = "JobInputs(name: " + name + ")"

  // Now that we have both pieces if information, combine them.

  val userInputs = user.userInputs
  val collections = user.collections
  val name = user.name
  val algorithm = user.algorithm
  val rawParameters = user.rawParameters

  val info = system.info
  val dependencies = system.dependencies
  val runScript = system.runScript
  val propertiesFileName = system.propertiesFileName
  val resultNames = system.resultNames

  // insert the token into the hashmap pile, ugly
  user.userInputs += ("auth_token" -> token)

  // We need to fill in the properties map with the user inputs. We
  // first create an ugly regex block.

  val aVar1 = """\$\{(\w+)\}""".r
  val aVar2 = """\$(\w+)""".r
  def bindVariables(exp: String, variables: HashMap[String,String]): String = {
    // log.debug("bindVariables, exp = " + exp + ", variables = " +
    //   variables.mkString("[", ", ", "]"))
    val r1 = aVar1 replaceAllIn (exp, (m: Match) =>
      Regex.quoteReplacement(variables.get(m.group(1)).getOrElse("HTRC_DEFAULT")))
      // use quoteReplacement to handle '$' in the replacement string

    val r2 = aVar2 replaceAllIn (r1, (m: Match) =>
      Regex.quoteReplacement(variables.get(m.group(1)).getOrElse("HTRC_DEFAULT")))
    r2
  }

  // Now simply map over the properties
  val properties = new HashMap[String,String]
  system.properties foreach {
    case (k,v) =>
      properties(k) = bindVariables(v, user.userInputs ++ HtrcConfig.systemVariables)
  }
  
}

case class JobSubmission(arguments: NodeSeq, submitter: String) {

  // When we receive the arguments we want to parse them. The "inputs"
  // represent the parameters provided by the user. These are used to
  // fill in the holes in the properties file.

  // This is an example block that has a user input parameter. Each
  // param is a "key - value" style element.

  // name of this job submission
  val name = arguments \ "name" text

  // algorithm to be run
  val algorithm = arguments \ "algorithm" text

  val rawParameters = arguments \ "parameters"

  val userInputs = new HashMap[String,String]
  arguments \ "parameters" \ "param" foreach { e =>
    userInputs += ((e \ "@name" text) -> (e \ "@value" text))
  }

  val collections = arguments \ "parameters" \ "param" filter { e => 
    (e \ "@type" text) == "collection"
  } map { e =>(e \ "@value").text } toList
  
  override def toString: String = {
    val resF = "JobSubmission(name=%s, algorithm=%s, params=%s)"
    resF.format(name, algorithm, userInputs.mkString("(", ", ", ")"))
  }
}

// This class parses and stores the metadata stored about an algorithm
// in the registry.

case class JobProperties(metadata: NodeSeq) {

  // Parse the input metadata from the registry.

  val properties = new HashMap[String,String]
  (metadata \ "system_properties" \ "e") foreach { e =>
    properties += ((e \ "@key" text) -> e.text)
  }

  val info = metadata \ "info"
  val algVersion = info \ "version" text

  // map associating workset parameter names to size limits defined on them
  // (-1, if no size limit defined)
  val worksetSizeLimits =
    (metadata \ "info" \ "parameters" \ "param") filter { p =>
      (p \ "@type" text) == "collection"
    } map { p =>
      ((p \ "@name" text),
        if (!(p.attribute("size_limit").isDefined))
          -1
        else
          (p \ "@size_limit" text).toInt)
    } toMap

  // the new registry stores the algorithm dependency against its name; there
  // is no "path" associated with an algorithm dependency
  val dependencies = metadata \ "dependencies" \ "dependency" map { e => e \ "@name" text }

  val runScript = metadata \ "run_script" text

  val propertiesFileName = metadata \ "properties_file_name" text

  val resultNames = metadata \ "results" \ "result" map { e => e \ "@name" text }

  // names of job results that must be present in the output of a successful
  // job; for example, a file that lists the volumes that could not be
  // retrieved from the Data API, is an optional result, which may or may not
  // be produced in a successful job; the "optional" attribute of a job
  // result is "false" by default
  val nonOptionalResults =
    metadata \ "results" \ "result" filter { e =>
      val opt = (e \ "@optional" text)
      ((opt == "") || (opt == "false"))
    } map { e => e \ "@name" text }

  // parse the <execution_info> element in the algorithm XML to determine
  // resource allocation parameterized by the total size of input worksets;
  // on success, returns a sequence of (min: Int, max: Int, ResourceAlloc)
  // tuples, where min and max are the lower and upper bounds on the total
  // input workset size
  def parseExecInfo(): Try[Seq[(Int, Int, ResourceAlloc)]] = {
    val execInfo = (metadata \ "execution_info")

    Try((execInfo \ "input_size") map { e =>
      val minStr = (e \ "@min" text)
      val min = if (minStr == "_") 0 else minStr.toInt

      val maxStr = (e \ "@max" text)
      val max = if (maxStr == "_") Int.MaxValue else maxStr.toInt

      val numNodesStr = (e \ "number_of_nodes" text)
      val numNodes =
        if (numNodesStr == "") HtrcConfig.getDefaultNumNodes
        else numNodesStr.toInt

      val numProcsStr = (e \ "number_of_processors_per_node" text)
      val numProcs =
        if (numProcsStr == "") HtrcConfig.getDefaultNumProcessorsPerNode
        else numProcsStr.toInt

      val walltimeStr = (e \ "walltime" text)
      val walltime =
        if (walltimeStr == "") HtrcConfig.getDefaultWalltime else walltimeStr

      val javaMaxHeapSizeStr = (e \ "java_max_heap_size" text)
      val javaMaxHeapSize =
        if (javaMaxHeapSizeStr == "") HtrcConfig.getDefaultJavaMaxHeapSize
        else javaMaxHeapSizeStr

      val vmemStr = (e \ "vmem" text)
      val vmem = if (vmemStr == "") HtrcConfig.getDefaultVmem else vmemStr

      (min, max,
        ResourceAlloc(numNodes, numProcs, walltime, javaMaxHeapSize, vmem))
    })
  }
}

// class that contains metadata, from the registry, for a workset
case class WorksetMetadata(metadata: NodeSeq) {
  val name = (metadata \\ "name" text)
  val author = (metadata \\ "author" text)
  val volumeCount = (metadata \\ "volumeCount" text).toInt
  val lastModifiedTime = (metadata \\ "lastModified" text)
  val isPublic = (metadata \\ "public" text).toBoolean
}

// class that contains resource allocation information for the HPC system on
// which jobs are run, e.g., number of nodes to allocate for the job, number
// of processors per node
case class ResourceAlloc(numNodes: Int, numProcsPerNode: Int,
  walltime: String, javaMaxHeapSize: String, vmem: String) {
  override def toString: String = {
    "ResourceAlloc(" + numNodes + ", " + numProcsPerNode + ", " + walltime +
    ", " + javaMaxHeapSize + ", " + vmem + ")"
  }
}

object SampleXmlInputs {

  lazy val wcInputs = JobInputs(JobSubmission(wordcountUser, "fake"),
                                JobProperties(wordcount), None,
                                "fake", "fake", "fake")

val wordcount = 
  <algorithm>
  <info>
  <name>Simple_Deployable_Word_Count</name>
  <version>1.4.1</version>
  <description>A simple word count Java client that retrieves some volumes from the Data API, and displays the top N most frequently occurred words.</description>
  <authors>
  <author name="Yiming Sun" email="yimsun@umail.iu.edu"/>
  </authors>
  
  <parameters>
  <param name="input_collection"
  type="collection"
  required="true">
  <label>The collection to be word counted</label>
  <description>This is the input collection the simple word count will be run on.</description>
  </param>
  
  <param name="concat"
  type="boolean"
  required="true">
  
  <label>Concatenate pages of a volume into a single file?</label>
  <description>False: each page is a separate file; True: entire volume is one file</description>
  </param>
  
  <param name="topN"
  type="integer"
  required="true">
  
  <label>Top N most frequently occurred words to display</label>
  <description>Top N most frequently occurred words to display</description>     
  </param>
  </parameters>
  </info>  
  
  <dependencies>
  <dependency name="runwc.sh" path="htrc/agent/dependencies/runwc.sh"/>
  <dependency name="htrc-uncamp-deplwc-1.4.1.jar" path="htrc/agent/dependencies/htrc-uncamp-deplwc-1.4.1.jar"/>
  </dependencies>
  
  <run_script>runwc.sh</run_script>
  <properties_file_name>wc.properties</properties_file_name>
  
  <system_properties>
  <e key="volume.limit">25</e>
  <e key="data.api.epr">$data_api_url</e>
  <e key="volume.list.file">$input_collection</e>
  <e key="concat">$concat</e>
  <e key="top.n">$topN</e>
  <e key="oauth2.token">$auth_token</e>
  </system_properties>  
  </algorithm>
  
  val wordcountUser =
    <job>
  <name>test_job</name>
  <username>abtodd</username>
  <algorithm>Simple_Deployable_Word_Count</algorithm>
  <parameters>
  <param
  name="topN"
  type="integer"
  value="20"/>
<param name="concat"
  type="boolean"
  value="true"/>
 <param name="input_collection"
  type="collection"
  value="cwillis_lincoln"/>
  </parameters>
  </job>


  val sampleAlgorithm = 
    <algorithm>
      <info>
        <name>Some_Name</name>
        <version>5.120</version>
        <description>Eloquent Text</description>
        <authors>
          <author name="Aaron Todd" email="fake@email.com"/>
        </authors>
        <parameters>
          <param name="foo"
                 type="string"
                 required="true">
            <label>A String</label>
            <description>Why do you need a string?</description>
          </param>
        </parameters>
      </info>
      <dependencies>
        <dependency name="bar.sh"
                    path="htrc/agent/dependencies/bar.sh"/>
      </dependencies>
      <run_script>bar.sh</run_script>
      <properties_file_name>foo.properties</properties_file_name>
      <system_properties>
        <e key="something">134</e>
        <e key="input">$foo</e>
      </system_properties>
      <results>
        <result type="text/html" name="foboar.html"/>
      </results>
    </algorithm>

  val exampleUserBlock =
    <job>
      <name>my_job</name>
      <username>drhtrc</username>
      <algorithm>Marc_Downloader</algorithm>
      <parameters>
        <param
          name="foo"
          type="collection"
          value="bar"/>
          </parameters>
    </job>

  lazy val sampleJobInputs = JobInputs(JobSubmission(exampleUserBlock, "fake"),
                                       JobProperties(sampleAlgorithm), None,
                                       "fake", "fake", "fake")

}
           
