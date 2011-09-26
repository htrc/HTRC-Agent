Akka Web Template
=================
This is a sbt-based, scala Akka project that sets up a web project with REST and comet support. It includes a simple hello-world style demo application and an actor boot class. It is inspired by efleming969's [akka-template-rest][]. However, it differs in that it

* uses sbt dependencies instead of setting up maven repositories
* uses the Akka sbt plugin
* targets Akka 1.1.2 and Scala 2.9.

This template is released under Apache 2 License.

Usage
-----

1. Make sure you have sbt installed and in your path. If not, see http://code.google.com/p/simple-build-tool/wiki/Setup
2. cd htrc-agent
3. sbt (which will bring you to an sbt prompt)
4. update
5. compile
6. jetty-run
7. In a browser, visit http://localhost:8080/

Notes
-----

* My version of the Akka plugin is hard-coded to 1.1.2. If you want to change that, you'll need to change project/Plugins/Plugins.Scala
* See the github link for akka-web-template, below, to view the original skeleton code.

[akka-web-template]: http://github.com/efleming969/akka-web-template "akka-web-template"
[akka-template-rest]: http://github.com/efleming969/akka-template-rest "akka-template-rest"