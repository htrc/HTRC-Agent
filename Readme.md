The agent provides a REST API for launching, monitoring, and managing
jobs. At present, HTRC jobs include Meandre algorithms and Java programs for
text mining and analysis. HTRC jobs are launched on HPC systems.

agent-api contains the code for the main agent service that responds to REST
calls, and performs job management tasks. 

agent-job-client contains the code for the AgentJobClient which is a process
that runs as a wrapper around an HTRC job. The agent service launches
AgentJobClient processes, each of which in turn launches an HTRC job. The
AgentJobClient communicates job details such as changes in status to the
agent.

