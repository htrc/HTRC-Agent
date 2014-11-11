organization  := "htrc"

version       := "0.17.0"

scalaVersion  := "2.10.0"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-language:postfixOps", "-encoding", "utf8")

resolvers ++= Seq(
  "typesafe repo"      at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo"         at "http://repo.spray.io/",
  "WSO2 Nexus" at "http://maven.wso2.org/nexus/content/groups/wso2-public/",
  "htrc nexus" at "http://htrc.illinois.edu/nexus/content/groups/public"
)

libraryDependencies ++= Seq(
  "edu.indiana.d2i.htrc.security" % "oauth2-servletfilter" % "2.0-SNAPSHOT",
  "io.spray"                %   "spray-servlet" % "1.1-M7",
  "io.spray"                %   "spray-routing" % "1.1-M7",
  "io.spray"                %   "spray-testkit" % "1.1-M7",
  "io.spray"                %   "spray-can"     % "1.1-M7",
  "io.spray"                %   "spray-http" % "1.1-M7",
  "io.spray"                %   "spray-httpx" % "1.1-M7",
  "io.spray"                %   "spray-util"     % "1.1-M7",
  "io.spray"                %   "spray-client"   % "1.1-M7",
  "org.scala-stm"           %%  "scala-stm" % "0.7",
  "org.eclipse.jetty"       %   "jetty-webapp"  % "8.1.7.v20120910"     % "container",
  "org.eclipse.jetty.orbit" %   "javax.servlet" % "3.0.0.v201112011016" % "container"  artifacts Artifact("javax.servlet", "jar", "jar"),
  "com.typesafe.akka"       %%  "akka-actor"    % "2.1.0", 
  "com.typesafe.akka"       %%  "akka-agent"    % "2.1.0",
  "com.typesafe.akka"       %%  "akka-slf4j"    % "2.1.0",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "org.apache.httpcomponents" % "httpcore" % "4.3.2",
  "edu.indiana.d2i.htrc.security" % "oauth2-servletfilter"  % "2.0-SNAPSHOT"
)

seq(webSettings: _*)


