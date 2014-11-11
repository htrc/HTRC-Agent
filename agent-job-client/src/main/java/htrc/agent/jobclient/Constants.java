package htrc.agent.jobclient;

public class Constants {
	// values for status of job timer used in AgentJobClient
	public static final int NA = 0, ALIVE = 1, TIMEDOUT = 2, CANCELLED = 3;
	
	// processing time required to perform job completion actions; used to set a timer that goes off
	// before PBS/SLURM walltime expiry, so that there is enough time for job completion actions; the
	// following string should be in "hhh:mm:ss" or "hh:mm:ss" format
	public static final String READABLE_PROCESSING_TIME_FOR_JOB_COMPLETION = "00:01:00";
	public static final long PROCESSING_TIME_FOR_JOB_COMPLETION = 
			Utils.timeLimitStringToMilliseconds(READABLE_PROCESSING_TIME_FOR_JOB_COMPLETION);

	// the following is used if there is an error in specifying READABLE_PROCESSING_TIME_FOR_JOB_COMPLETION
	public static final long DEFAULT_PROCESSING_TIME_FOR_JOB_COMPLETION = 1 * 60 * 1000L;
	
	public static final String AUTH_TOKEN_ENV_VAR = "HTRC_OAUTH_TOKEN";
}
