#!/usr/bin/env bash

# print the name of the host (compute node) where the AgentJobClient and
# the algorithm child process run
echo "Hostname: `hostname --fqdn`"

# set up port forwarding to the agent
ssh -f drhtrc@htrc4.pti.indiana.edu -L 19003:htrc4.pti.indiana.edu:9000 -N

$JAVA_CMD -Xmx512m -jar $HOME/leena/agent-job-client/AJC/AgentJobClient.jar -d $HTRC_WORKING_DIR -s $SHELL -t $HTRC_TIME_LIMIT -j $HTRC_JOBID -a $HTRC_AGENT_ENDPOINT $HTRC_WORKING_DIR/$HTRC_ALG_SCRIPT

# remove port forwarding
SSHTUNNEL=`ps aux | grep "ssh -f drhtrc" | grep -v "grep" | awk '{print $2}'`
echo "SSH tunnel pid = $SSHTUNNEL"
kill -9 $SSHTUNNEL
echo "Result of kill ssh tunnel = $?"
