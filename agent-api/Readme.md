The agent provides a REST API for launching, monitoring, and managing jobs. At present, HTRC jobs include Meandre algorithms and Java programs for text mining and analysis. HTRC jobs are launched on HPC systems. 

The agent is written in Scala. It uses Spray (http://spray.io/) for receiving and dispatching HTTP requests and responses. It parallelizes handling of these requests using the Akka (http://akka.io/) framework.
