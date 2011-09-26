import sbt._

//By extending DefaultWebProject, we get Jetty support
//By adding the AkkaProject trait, we can easily pull in Akka modules
class AkkaWebTemplate(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
  //Adding dependencies in sbt is as simple as declaring a variable
  //For the Akka deps, we even have a method for pulling them in by a simple name
  val akkaHttp = akkaModule("http")
  val akkaKernel = akkaModule("kernel")
  val jettyWebapp = "org.eclipse.jetty" % "jetty-webapp" % "8.0.0.M2" % "test"
  val javaxServlet30 = "org.mortbay.jetty" % "servlet-api" % "3.0.20100224" % "provided"
  
  //val specs2 = "org.specs2" % "1.0" % "compile"
  val specs = "org.scala-tools.testing" % "specs_2.9.0-1" % "1.6.8" % "test" // For specs.org tests
  
  override val jettyPort = 41567
  override def unmanagedClasspath = {
      // see http://code.google.com/p/simple-build-tool/wiki/LibraryManagement#Manual_Dependency_Management
      def baseDirectories = "lib_unmanaged" +++ ("lib_unmanaged" / "registry_lib")
      def extraJars = descendents(baseDirectories,"*.jar")
      super.unmanagedClasspath +++ extraJars
  }
}

//class AkkaTemplate(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
//  // this should largely match the above, minus jetty stuff
//  val akkaHttp = akkaModule("http")
//  val akkaKernel = akkaModule("kernel")
// 
//  //val specs2 = "org.specs2" % "1.0" % "compile"
//  val specs = "org.scala-tools.testing" % "specs_2.9.0-1" % "1.6.8" % "test" // For specs.org tests
//  
//  override def unmanagedClasspath = {
//      // see http://code.google.com/p/simple-build-tool/wiki/LibraryManagement#Manual_Dependency_Management
//      def baseDirectories = "lib_unmanaged" +++ ("lib_unmanaged" / "registry_lib")
//      def extraJars = descendents(baseDirectories,"*.jar")
//      super.unmanagedClasspath +++ extraJars
//  }
//}
