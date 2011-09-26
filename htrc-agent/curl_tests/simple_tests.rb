#!/usr/bin/env ruby
hostport = "coffeetree.cs.indiana.edu:41567"
#agent = `curl #{hostport}/manager/vend/foo/bar/baz|grep agentID`
#print "agent response for vend: #{agent}\n"
#<agentID>146aa720-4d70-4836-964d-daa0d0fabe16</agentID>
#/agentID>(.*)<\/agent/ =~ agent
#if Regexp.last_match == nil
#  print "failed to get an agent\n"
#  exit 1
#end
#agent_id= Regexp.last_match(1)

# now that we use PUT to get an agent, we'll have to figure out a way to re-do 
# this in ruby, or better, to incorporate unit tests in the scala build

# for now, assume masterkh is logged in

#agent_id = "urn:publicid:IDN+cilogon.org+user+A595"
#agent_id = "urn:publicid:IDN+bogusID.org+user+A1Winner"


# get a bogus agent
agent = `curl #{hostport}/manager/vend-bogus-agent|grep agentID`
print "agent response for vend: #{agent}\n"
#<agentID>146aa720-4d70-4836-964d-daa0d0fabe16</agentID>
/agentID>(.*)<\/agent/ =~ agent
if Regexp.last_match == nil
  print "failed to get an agent\n"
  exit 1
end
agent_id= Regexp.last_match(1)


print "get a list of agents"
get_agents = `curl #{hostport}/agent`

print "response - list of agents:\n#{get_agents}\n"

get_solr_epr = `curl #{hostport}/agent/#{agent_id}/index-epr`
print "agent response for index-epr: #{get_solr_epr}\n"



get_repository_epr = `curl #{hostport}/agent/#{agent_id}/repository-epr`
print "agent response for repository-epr: #{get_repository_epr}\n"

# use this collection for the next several tests
#collection_name = "IUCollection.txt"
#collection_name = "IUCollection.csv"
collection_name = "TestCollectionSingleVolume.txt"


# use this arg for the tests
user_args = 'w.*%5ct5'   # %5c is URL code for backslash

# try finding a bad algorithm and see what happens
bad_algo_name = "thisalgorithmdoesntexist"
#curl_string = "curl #{hostport}/agent/#{agent_id}/algorithm/run/#{bad_algo_name}/#{collection_name}/#{user_args}"
#print "executing: #{curl_string}"



# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"
# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"
# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"

# # run_nonexistent_algorithm = 
# # `curl #{hostport}/agent/#{agent_id}/algorithm/run/#{bad_algo_name}/#{collection_name}/#{user_args}`
# # #  `#{curl_string}`

# # print "agent response for running a NON EXISTING algo '#{bad_algo_name}' on collection '#{collection_name}' with args '#{user_args}':\n"
# # print "              #{run_nonexistent_algorithm}\n"

# # print "\n\n Do note that calling /agent/.../algorithm/run/ALGONAME   with a bad ALGONAME will not explicitly show an error.\n"
# # print " ^^ worth fixing...^^ \n"

# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"
# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"
# print "UNGAG BAD ALGO NAME TEST WHEN YOU'RE READY\n"


##
## list available algorithms
##

print "\n\n list available algorithms\n\n"
print "curl #{hostport}/agent/#{agent_id}/algorithm/list"
print `curl #{hostport}/agent/#{agent_id}/algorithm/list`

##
## list available collections
##

print "\n\n list available collections\n\n"
print "curl #{hostport}/agent/#{agent_id}/collection/list"
print `curl #{hostport}/agent/#{agent_id}/collection/list`
print "\n\n"



# try to find a trivial algorithm

trivial_algo_name = "simplescript"


print "running algorithm using this GET call:\n"
print "GET #{hostport}/agent/#{agent_id}/algorithm/run/#{trivial_algo_name}/#{collection_name}/#{user_args}\n"


#print "executing: #{curl_string}"
run_trivial_algorithm = 
`curl #{hostport}/agent/#{agent_id}/algorithm/run/#{trivial_algo_name}/#{collection_name}/#{user_args}`
#  `#{curl_string}`

print "agent response for running algo '#{trivial_algo_name}' on collection '#{collection_name}' with args '#{user_args}':\n"
print "              #{run_trivial_algorithm}\n"

#   <id>0ff82012-7508-47be-b238-4f10e840e4b8</id>
run_trivial_algo_lines = run_trivial_algorithm.split("\n")
triv_algo_id = nil
run_trivial_algo_lines.each do |l|
   /id>(.*)<\/id/ =~  l
   if Regexp.last_match != nil
     triv_algo_id = Regexp.last_match(1)
   end

end

if triv_algo_id != nil
  [0,5,12].each do |n|
    print "\nSleeping #{n} seconds..."
    sleep n
    print "\nPolling algorithm #{triv_algo_id} \n"
    poll_trivial_algorithm = 
      `curl #{hostport}/agent/#{agent_id}/algorithm/poll/#{triv_algo_id}`
    print "\nResult: \n#{poll_trivial_algorithm}\n"
    ## poll all running algorithms for this user

    print "\npolling all running algos for this user\n"
    poll_all = `curl #{hostport}/agent/#{agent_id}/algorithm/poll`
    print "\nResult: \n#{poll_all}\n"
  end
else 
  print """

-------------------------------------------------------------------------------
                       WARNING WARNING WARNING! 
         couldn't match an algorithm run ID for the trivial algorithm
-------------------------------------------------------------------------------

"""
end



#once more, for good measure
## poll all running algorithms for this user

print "\npolling all running algos for this user\n"
poll_all = `curl #{hostport}/agent/#{agent_id}/algorithm/poll`
print "\nResult: \n#{poll_all}\n"
