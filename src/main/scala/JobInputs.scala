
package htrc.agent

// When an job submission is received the parameters are parsed
// (marshalled?) into this class. It can then be passed around and
// queried / filled in.

import scala.xml._
import scala.collection.mutable.HashMap
import scala.util.matching.Regex._

case class JobInputs(user: JobSubmission, system: JobProperties) {

  // Now that we have both pieces if information, combine them.

  val userInputs = user.userInputs
  val collections = user.collections
  val name = user.name

  val info = system.info
  val dependencies = system.dependencies
  val runScript = system.runScript
  val propertiesFileName = system.propertiesFileName
  val resultNames = system.resultNames

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
      properties(k) = bindVariables(v, user.userInputs)
  }
  
}

case class JobSubmission(arguments: NodeSeq) {

  // When we receive the arguments we want to parse them. The "inputs"
  // represent the parameters provided by the user. These are used to
  // fill in the holes in the properties file.

  // This is an example block that has a user input parameter. Each
  // param is a "key - value" style element.

  val name = arguments \ "name" text

  val userInputs = new HashMap[String,String]
  arguments \ "parameters" \ "param" foreach { e =>
    userInputs += ((e \ "@name" text) -> (e \ "@value" text))
  }

  val collections = arguments \ "parameters" \ "param" filter { e => 
    (e \ "@type" text) == "collection"
  } map { e => e \ "@name" text } toList
  
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

//  lazy val sampleJobInputs = JobInputs(JobSubmission(exampleUserBlock),
//                                       JobProperties(sampleAlgorithm))

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
                                       JobProperties(sampleAlgorithm))                  

   


}
           
