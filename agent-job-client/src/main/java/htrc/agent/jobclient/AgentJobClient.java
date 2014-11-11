package htrc.agent.jobclient;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.wso2.carbon.identity.oauth.stub.OAuthAdminServiceStub;

import com.martiansoftware.jsap.JSAPResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentJobClient {
	private File workDir, shell, script;
	private String workDirPath;
	private String timeLimitStr;
	private String jobid;
	private String agentEndpoint;
	private String authToken;
	private String[] arguments;
    private Timer jobTimer = null;
    private int jobTimerStatus = Constants.NA;
    private Process jobProcess;
    private AgentClient agentClient;
	private Logger log;

	private long startTime;
	
	public AgentJobClient(JSAPResult cmdLine, Logger log) throws Exception {
		workDir = cmdLine.getFile("workDir");
		workDirPath = workDir.getPath();
	    shell = cmdLine.getFile("shell");
	    timeLimitStr = cmdLine.getString("timelimit");
	    jobid = cmdLine.getString("jobid");
	    agentEndpoint = cmdLine.getURL("agentEndpoint").toString();
	    authToken = System.getenv(Constants.AUTH_TOKEN_ENV_VAR);
	    if (authToken == null) {
	    	throw new Exception("Environment variable " + Constants.AUTH_TOKEN_ENV_VAR + 
	    			            " not set."); 	
	    }
	    script = cmdLine.getFile("jobScript");
	    arguments = cmdLine.getStringArray("arg");
	    agentClient = new AgentClient(jobid, agentEndpoint, authToken);
	    this.log = log;
	    // printParamsToLog();
	}
	
	private void printParamsToLog() {
	    // log.debug("Reporting URL: {}", agentUrl);
	    log.debug("Shell: {}", shell);
	    log.debug("Working directory: {}", workDir);
	    log.debug("Walltime in milliseconds: {}", timeLimitStr);
	    log.debug("Command: {}", script);
	    log.debug("Arguments:");
	    if (log.isDebugEnabled())
	        for (String argument : arguments)
	            log.debug("\t{}", argument);
	}
	
	private synchronized void setJobTimerStatus(int status) {
		jobTimerStatus = status;
	}
	
	private void startJobTimer(final StreamReader outReader, final StreamReader errReader) {
		long timelimit = Utils.timeLimitStringToMilliseconds(timeLimitStr, agentClient);
		long waitTime = timelimit - Constants.PROCESSING_TIME_FOR_JOB_COMPLETION;
        if (waitTime >= 0) {
            jobTimer = new Timer();
            setJobTimerStatus(Constants.ALIVE);
            jobTimer.schedule(new TimerTask() { 
            	public void run() {
            		synchronized (this) {
            			// if this timer has not been cancelled yet then stop the job sub-process
            			// and perform other end-of-job actions
            			if (jobTimerStatus == Constants.ALIVE) {
            				jobTimerStatus = Constants.TIMEDOUT;
            				// jobProcess.destroy();
            			}
            		}
            		// if the jobTimer times out before the job is completed then perform 
            		// job completion tasks
        			if (jobTimerStatus == Constants.TIMEDOUT) {
        				jobProcess.destroy();
        				// wait for stream readers to be done to ensure that the agent has the
        				// correct stderr.txt, stdout.txt to copy
        				try {
        					outReader.join();
        					errReader.join();
        				} catch (InterruptedException e) {
        					System.err.println(e);
        					e.printStackTrace(System.err);
        				}
        				
        				// jobStatus = Constants.TIMEDOUT_PENDING_COMPLETION;
        	            long endTime = System.currentTimeMillis();
        	            String jobRuntime = String.valueOf((endTime - startTime)/1000);
            			agentClient.sendJobStatusTimedOut(jobRuntime);
        				String msg = "Walltime expired. Job killed.";
        	    		log.debug("Job completion status: {}", msg);
        	    		log.debug("Job runtime: {} seconds", jobRuntime);
        			}
            	}
            }, waitTime);
        }		
	}
	
	public void startProcess() {
		// set up port forwarding
//		String setSshTunnel = "ssh -f drhtrc@htrc4.pti.indiana.edu -L 19003:htrc4.pti.indiana.edu:9000 -N";
//		try {
//		  Process p = Runtime.getRuntime().exec(setSshTunnel);
//		  p.waitFor();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		
		// notify the agent that the job has started running
		agentClient.sendJobStatusRunning();
		
        // create a subprocess to execute the job
	    List<String> command = Utils.buildCommand(shell, arguments, script);
        log.debug("Executing command: {}", command);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        startTime = System.currentTimeMillis();
        
        try {
        	jobProcess = pb.start();	// start the subprocess
        } catch (IOException e) {
        	String errorMsg = e.toString();
        	agentClient.sendJobStatusJobClientError(errorMsg);
        	System.err.println(errorMsg);
        	e.printStackTrace(System.err);
        	System.exit(1);
        }
        
        // start threads to read error and output streams of job subprocess
        StreamReader errReader = new StreamReader(jobProcess.getErrorStream(), 
        		                                  workDirPath + "/stderr.txt",
        		                                  log, "Error", agentClient);
        StreamReader outReader = new StreamReader(jobProcess.getInputStream(), 
                                                  workDirPath + "/stdout.txt",
                                                  log, "Output", agentClient);
        errReader.start();
        outReader.start();
        
        // start a timer to stop the job once the specified timelimit is reached
        startJobTimer(outReader, errReader);

        // wait for the job sub-process to complete
        int jobExitValue = 0;
        try {
        	jobExitValue = jobProcess.waitFor();

        	// wait for the threads reading the output and error streams of jobProcess to complete;
        	// this should occur as soon as the job completes naturally, or is explicitly killed by 
        	// jobTimer's task
        	outReader.join();
        	errReader.join();
        } catch (InterruptedException e) {
			System.err.println(e);
			e.printStackTrace(System.err);
        }
    	   	
        synchronized (this) {
        	// if the timer is alive and has not yet timed out, then set jobTimerStatus as 
        	// CANCELLED so that even if jobTimer goes off at this point, it does not perform 
        	// any job completion tasks; jobTimerStatus being set to CANCELLED implies that the
        	// the job has completed and the main thread will perform job completion tasks
        	if (jobTimerStatus == Constants.ALIVE) {
        		jobTimerStatus = Constants.CANCELLED;
        		// jobTimer.cancel();
        	}
        }
        
        // jobTimer needs to be cancelled so that the application terminates; otherwise, the
        // non-daemon timer task execution thread may stay alive for an arbitrary time period,
        // preventing termination
        if (jobTimer != null)
        	jobTimer.cancel();

        long endTime = System.currentTimeMillis();
        // String jobRuntime = String.format("%,.2f", (endTime - startTime)/(1000d * 60));
        String jobRuntime = String.valueOf((endTime - startTime)/1000);

        if (jobTimerStatus == Constants.CANCELLED || jobTimerStatus == Constants.NA) {
    		String msg = "";
    		if (jobExitValue == 0) {
    			msg = "Job finished successfully ";
    			agentClient.sendJobStatusFinished(jobExitValue, jobRuntime);
    		}
    		else {
    			msg = "Job crashed.";
    			agentClient.sendJobStatusCrashed(jobExitValue, jobRuntime);
    		}
    		log.debug("Job exit value: {}", jobExitValue);
    		log.debug("Job completion status: {}", msg);
    		log.debug("Job runtime: {} seconds", jobRuntime);
    	}
	}
    
	public static void main(String[] args) {
		AgentJobClient ajc = null;
		try {
			Logger log = LoggerFactory.getLogger(AgentJobClient.class);

			// parse command line arguments
			JSAPResult cmdLine = ArgumentParser.parseArguments(args);
			if (cmdLine == null) {
				throw new Exception("Unexpected arguments to AgentJobClient: " + Arrays.toString(args));
			}
        
			// create an AgentJobClient to run process
			ajc = new AgentJobClient(cmdLine, log);
		}
		catch (Exception e) {
			System.err.println(e.toString());
			e.printStackTrace(System.err);
			System.exit(1);
		}
		if (ajc != null) {
			try {
				ajc.startProcess();
			} catch (Exception e) {
				String errorMsg = e.toString();
				System.err.println(errorMsg);
				e.printStackTrace(System.err);
				ajc.agentClient.sendJobStatusJobClientError(errorMsg);
			}
		}
	}
}
