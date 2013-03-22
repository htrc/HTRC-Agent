
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

// When an job submission is received the parameters are parsed
// (marshalled?) into this class. It can then be passed around and
// queried / filled in.

import scala.xml._
import scala.collection.mutable.HashMap
import scala.util.matching.Regex._

case class JobInputs(user: JobSubmission, system: JobProperties, token: String) {

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
    val r1 = aVar1 replaceAllIn 
      (exp, (m: Match) => variables.get(m.group(1)).getOrElse("HTRC_DEFAULT"))
    val r2 = aVar2 replaceAllIn 
      (r1, (m: Match) => variables.get(m.group(1)).getOrElse("HTRC_DEFAULT")) 
    r2
  }

  // Now simply map over the properties
  val properties = new HashMap[String,String]
  system.properties foreach {
    case (k,v) =>
      properties(k) = bindVariables(v, user.userInputs ++ HtrcConfig.systemVariables)
  }
  
}

case class JobSubmission(arguments: NodeSeq) {

  // When we receive the arguments we want to parse them. The "inputs"
  // represent the parameters provided by the user. These are used to
  // fill in the holes in the properties file.

  // This is an example block that has a user input parameter. Each
  // param is a "key - value" style element.

  val name = arguments \ "name" text
  
  val algorithm = arguments \ "algorithm" text

  val rawParameters = arguments \ "parameters"

  val userInputs = new HashMap[String,String]
  arguments \ "parameters" \ "param" foreach { e =>
    userInputs += ((e \ "@name" text) -> (e \ "@value" text))
  }

  val collections = arguments \ "parameters" \ "param" filter { e => 
    (e \ "@type" text) == "collection"
  } map { e =>(e \ "@value").text.split('@')(0) } toList
  
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

  val dependencies = new HashMap[String,String]
  (metadata \ "dependencies" \ "dependency") foreach { d =>
    dependencies += ((d \ "@path" text) -> (d \ "@name" text)) 
  }    

  val runScript = metadata \ "run_script" text

  val propertiesFileName = metadata \ "properties_file_name" text

  val resultNames = metadata \ "results" \ "result" map { e => e \ "@name" text }

}

object SampleXmlInputs {

  lazy val wcInputs = JobInputs(JobSubmission(wordcountUser),
                                JobProperties(wordcount),
                                "fake")

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

  lazy val sampleJobInputs = JobInputs(JobSubmission(exampleUserBlock),                 
                                       JobProperties(sampleAlgorithm),
                                     "fake")                  

   


}
           
