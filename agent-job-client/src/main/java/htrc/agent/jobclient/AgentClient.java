package htrc.agent.jobclient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class AgentClient {
	private String agentEndPoint;
	private String jobid;
	private String authToken;
	
	public AgentClient(String jobid, String agentEndPoint, String authToken) {
		this.jobid = jobid;
		this.agentEndPoint = agentEndPoint;
		this.authToken = authToken;
	}

	private String getJobStatusUpdateURL() {
		return "/job/" + jobid + "/updatestatus";
	}
	
	private static String getRunningStatusXML() {
		return "<status type='Running'/>";
	}
	
	private static String getJobClientErrorStatusXML(String errorMsg) {
		String template = "<status type='JobClientError'>" +
				          "<error>%s</error>" +
				          "</status>";
		return String.format(template, errorMsg);
	}

	private static String getFinishedStatusXML(int jobExitValue, String jobRuntime) {
		String template = "<status type='FinishedPendingCompletion'>" +
				          "<job_exit_value>%s</job_exit_value>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, jobExitValue, jobRuntime);
	}

	private static String getCrashedStatusXML(int jobExitValue, String jobRuntime) {
		String template = "<status type='CrashedPendingCompletion'>" +
				          "<job_exit_value>%s</job_exit_value>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, jobExitValue, jobRuntime);
	}

	private static String getTimedOutStatusXML(String jobRuntime) {
		String template = "<status type='TimedOutPendingCompletion'>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, jobRuntime);
	}

	private void sendJobStatusUpdate(String content) {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			HttpPost httpPost = new HttpPost(agentEndPoint + getJobStatusUpdateURL());
			httpPost.setHeader("Authorization", "Bearer " + authToken);
			httpPost.setHeader("Content-type", "text/xml");
			StringEntity requestEntity = new StringEntity(content);
		    httpPost.setEntity(requestEntity);
		    
		    try {
		      CloseableHttpResponse response = httpClient.execute(httpPost);
	            try {
	            	StatusLine statusLine = response.getStatusLine();
	            	int status = statusLine.getStatusCode();
	            	if (status > 300) {
		                System.err.println("Unexpected response from the agent to \"job/id/updatestatus\" sent by AgentJobClient: " + 
	            	                       statusLine);
	            	}
	                EntityUtils.consume(response.getEntity());
	            } finally {
	                response.close();
	            }
		    } catch (Exception e) {
		    	handleException(e);
		    }
		} catch (UnsupportedEncodingException e) {
	    	handleException(e);
		}
		finally {
			try {
			  httpClient.close();
			} catch (IOException e) {
				handleException(e);
			}
		}
	}
	
	private void handleException(Exception e) {
    	System.err.println("Exception during send of \"job/id/updatestatus\" from AgentJobClient to the agent.");
    	System.err.println(e);
        e.printStackTrace(System.err);
	}
	
	public void sendJobStatusRunning() {
		sendJobStatusUpdate(getRunningStatusXML());
	}
	
	public void sendJobStatusJobClientError(String errorMsg) {
		sendJobStatusUpdate(getJobClientErrorStatusXML(errorMsg));
	}

	public void sendJobStatusFinished(int jobExitValue, String jobRuntime) {
		sendJobStatusUpdate(getFinishedStatusXML(jobExitValue, jobRuntime));
	}

	public void sendJobStatusCrashed(int jobExitValue, String jobRuntime) {
		sendJobStatusUpdate(getCrashedStatusXML(jobExitValue, jobRuntime));
	}

	public void sendJobStatusTimedOut(String jobRuntime) {
		sendJobStatusUpdate(getTimedOutStatusXML(jobRuntime));
	}
}
