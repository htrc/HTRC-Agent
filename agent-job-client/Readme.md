The AgentJobClient is a process that runs as a wrapper around an HTRC job. It
updates the agent about changes in the status of the contained HTRC job, such
as when the job begins running, or when the job completes. It can also
provide the agent with other details such as job runtime, and periodic
updates of the job's stderr and stdout streams.

