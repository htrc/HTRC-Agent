
package htrc.agent

// When an job submission is received the parameters are parsed
// (marshalled?) into this class. It can then be passed around and
// queried / filled in.

import scala.xml._

case class JobSubmission(arguments: NodeSeq) {

  // When we receive the arguments we want to parse them. The "inputs"
  // represent the parameters provided by the user. These are used to
  // fill in the holes in the properties file.

  // This is an example block that has a user input parameter. Each
  // param is a "key - value" style element.

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

  val name = arguments \ "name" text

  val userInputs = new HashMap[String,String]
  arguments \ "parameters" \ "param" foreach { e =>
    userInputs += ((e \ "@name" text) -> (e \ "@value" text))
  }
  
}

// This class parses and stores the metadata stored about an algorithm
// in the registry.

case class JobProperties(algorithmMetadata: NodeSeq) {

  // Parse the input metadata from the registry.

  val properties = new HashMap[String,String]
  (algorithmMetadata \ "system_properties" \ "e") foreach { e =>
    properties += ((e \ "@key" text) -> e.text)
  }

  val info = algorithmMetadata \ "info"

  val dependencies = new HashMap[String,String]
  (algorithmMetadata \ "dependencies" \ "dependency") foreach { d =>
    dependencies += ((e \ "@path" text) -> (e \ "@name" text)) 
  }

  val sampleAlgorithm = 
    <algorithm>
      <info>
        <name>Some_Name</name>
        <version>5.120</version>
        <description>Eloquent Text</description>
        <authors>
          <author name="Aaron Todd" email="fake@email.com">
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
        <e key="input">$foo<e>
      </system_properties>
    </algorithm>

}
           
