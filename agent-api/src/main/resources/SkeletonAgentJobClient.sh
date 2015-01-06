#!/usr/bin/env bash

# print the name of the host (compute node) where the AgentJobClient and
# the algorithm child process run
echo "Hostname: `hostname --fqdn`"

$JAVA_CMD -Xmx512m -jar $HTRC_DEPENDENCY_DIR/AgentJobClient.jar -d $HTRC_WORKING_DIR -s $SHELL -t $HTRC_TIME_LIMIT -j $HTRC_JOBID -u $HTRC_USER -a $HTRC_AGENT_ENDPOINT -i $HTRC_ID_SERVER_TOKEN_URL $HTRC_WORKING_DIR/$HTRC_ALG_SCRIPT
