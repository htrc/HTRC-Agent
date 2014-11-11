package htrc.agent.jobclient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Utils {
	public static List<String> buildCommand(File shell, String[] arguments, File script) {
        StringBuilder jobCmd = new StringBuilder();
        jobCmd.append(script.toString());
        for (String argument : arguments)
            jobCmd.append(" ").append(argument);

        List<String> command = new ArrayList<>();
        command.add(shell.toString());
        // command.add("-c");
        command.add(jobCmd.toString());
        return command;
	}
	
    // timeLimitStr has format hhh:mm:ss or more likely hh:mm:ss; convert this
    // into milliseconds; if timeLimitStr is set to "no time limit" or "-1", then -1 is 
    // returned
    public static long timeLimitStringToMilliseconds(String timeLimitStr, AgentClient agentClient) {
    	if (timeLimitStr.equals("-1"))
    		return -1L;
    	
    	// timelimitStr should be of the format hhh:mm:ss or hh:mm:ss
        String pattern = "\\d{2,3}:\\d{2}:\\d{2}";
        if (!timeLimitStr.matches(pattern)) {
        	String errorMsg = "Invalid timelimit argument to AgentJobClient: " + timeLimitStr + 
        			          "; expected hhh:mm:ss or hh:mm:ss.";
        	handleErrorInTimeLimitString(errorMsg, agentClient);
        }
  	
        String[] times = timeLimitStr.split(":");
        int hours = Integer.parseInt(times[0]);
        int mins = Integer.parseInt(times[1]); 
        int secs = Integer.parseInt(times[2]);
        long result = ((hours*60*60 + mins*60 + secs)*1000L);
        
        if (result <= Constants.PROCESSING_TIME_FOR_JOB_COMPLETION) {
        	String errorMsg = "Value of argument timelimit to AgentJobClient, " + timeLimitStr + 
			                  ", not greater than processing time " + 
        			          Constants.READABLE_PROCESSING_TIME_FOR_JOB_COMPLETION + ".";
        	handleErrorInTimeLimitString(errorMsg, agentClient);
        }
        
        return result; 
    }

    public static long timeLimitStringToMilliseconds(String timeLimitStr) {
    	if (timeLimitStr.equals("-1"))
    		return -1L;
    	
    	// timelimitStr should be of the format hhh:mm:ss or hh:mm:ss
        String pattern = "\\d{2,3}:\\d{2}:\\d{2}";
        if (!timeLimitStr.matches(pattern)) {
        	System.err.println("Invalid timeLimitStr arg to timeLimitStringToMilliseconds(String): " +
        					   timeLimitStr + "; expected hhh:mm:ss or hh:mm:ss.");
        	return Constants.DEFAULT_PROCESSING_TIME_FOR_JOB_COMPLETION;
        }
  	
        String[] times = timeLimitStr.split(":");
        int hours = Integer.parseInt(times[0]);
        int mins = Integer.parseInt(times[1]); 
        int secs = Integer.parseInt(times[2]);
        long result = ((hours*60*60 + mins*60 + secs)*1000L);
        return result; 
    }

    private static void handleErrorInTimeLimitString(String errorMsg, AgentClient agentClient) {
    	agentClient.sendJobStatusJobClientError(errorMsg);
    	System.exit(1);
    }
}
