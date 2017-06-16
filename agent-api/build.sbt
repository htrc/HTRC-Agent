organization  := "edu.indiana.d2i.htrc"

version       := "3.2.6-SNAPSHOT"

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-language:postfixOps", "-encoding", "utf8")

resolvers ++= Seq(
  "typesafe repo"      at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo"         at "http://repo.spray.io/",
  "sbt plugin repo" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases",
  "WSO2 Nexus" at "http://maven.wso2.org/nexus/content/groups/wso2-public/",
  "htrc nexus" at "http://nexus.htrc.illinois.edu/content/groups/public",
  "storehaus repo" at "http://repo1.maven.org/maven2/com/twitter/"
)

libraryDependencies ++= Seq(
  "io.spray"                %   "spray-servlet" % "1.3.2-20140428",
  "io.spray"                %   "spray-routing" % "1.3.2-20140428",
  "io.spray"                %   "spray-testkit" % "1.3.2-20140428",
  "io.spray"                %   "spray-can"     % "1.3.2-20140428",
  "io.spray"                %   "spray-http" % "1.3.2-20140428",
  "io.spray"                %   "spray-httpx" % "1.3.2-20140428",
  "io.spray"                %   "spray-util"     % "1.3.2-20140428",
  "io.spray"                %   "spray-client"   % "1.3.2-20140428",
  "org.scala-stm"           %%  "scala-stm" % "0.7",
  "com.typesafe.akka"       %%  "akka-actor"    % "2.3.6", 
  "com.typesafe.akka"       %%  "akka-agent"    % "2.3.6",
  "com.typesafe.akka"       %%  "akka-slf4j"    % "2.3.6",
  // "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "org.apache.httpcomponents" % "httpcore" % "4.3.2",
  "com.twitter" % "storehaus-cache_2.10" % "0.10.0",
  "commons-io" % "commons-io" % "2.4",
  "com.github.tototoshi" %% "scala-csv" % "1.3.4",
  "edu.indiana.d2i.htrc" % "oauth2-servletfilter"  % "2.0-SNAPSHOT",
  "edu.indiana.d2i.htrc" % "jwt-servletfilter"  % "0.1-SNAPSHOT"
)

// disable using the Scala version in output paths and artifacts
crossPaths := false

publishMavenStyle := true

publishTo := Some("HTRC Nexus Snapshots" at "https://nexus.htrc.illinois.edu/content/repositories/snapshots/")

// credentials += Credentials("Sonatype Nexus Repository Manager", "nexus.htrc.illinois.edu", "user", "passwd")

credentials += Credentials(Path.userHome / "htrc-agent-dev" / "trunk.dummy-mode" / ".credentials")

// disable .jar publishing
publishArtifact in (Compile, packageBin) := false

// disable publishing the main API jar
publishArtifact in (Compile, packageDoc) := false

// disable publishing the main sources jar
publishArtifact in (Compile, packageSrc) := false

// create an Artifact for publishing the .war file
artifact in (Compile, packageWar) := {
  val previous: Artifact = (artifact in (Compile, packageWar)).value
  previous.copy(`type` = "war", extension = "war")
}

// add the .war file to what gets published
addArtifact(artifact in (Compile, packageWar), packageWar)

// container:start fails to start Jetty because of problems with jar file
// icu4j-2.6.1.jar, but Tomcat is able get past this error
tomcat(port = 9000)

val buildenv = settingKey[String]("buildenv")

buildenv := sys.props.getOrElse("buildenv", default = "dev")

unmanagedResourceDirectories in Compile += baseDirectory.value / "src" / "main" / "env-specific-resources" / buildenv.value
