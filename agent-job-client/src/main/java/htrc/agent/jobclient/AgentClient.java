package htrc.agent.jobclient;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.net.ssl.SSLContext;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

public class AgentClient {
	private String agentEndPoint;
	private String jobid;
	private String user; // HTRC user that launched the job
	private String authToken;
	private IdentityServerClient idServerClient;

	private static final int MAX_RETRIES = 2;
	
	public AgentClient(String jobid, String user, String agentEndPoint, String authToken, IdentityServerClient idServerClient) {
		this.jobid = jobid;
		this.user = user;
		this.agentEndPoint = agentEndPoint;
		this.authToken = authToken;
		this.idServerClient = idServerClient;
	}

	private String getJobStatusUpdateURL() {
		return "/job/" + jobid + "/updatestatus";
	}
	
	private static String getRunningStatusXML(String user) {
		String template = "<status type='Running'>" +
		                  "<user>%s</user>" +
		                  "</status>";
		return String.format(template, user);
	}
	
	private static String getJobClientErrorStatusXML(String user, String errorMsg) {
		String template = "<status type='JobClientError'>" +
                          "<user>%s</user>" +
				          "<error>%s</error>" +
				          "</status>";
		return String.format(template, user, errorMsg);
	}

	private static String getFinishedStatusXML(String user, int jobExitValue, String jobRuntime) {
		String template = "<status type='FinishedPendingCompletion'>" +
                          "<user>%s</user>" +
				          "<job_exit_value>%s</job_exit_value>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, user, jobExitValue, jobRuntime);
	}

	private static String getCrashedStatusXML(String user, int jobExitValue, String jobRuntime) {
		String template = "<status type='CrashedPendingCompletion'>" +
                          "<user>%s</user>" +
				          "<job_exit_value>%s</job_exit_value>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, user, jobExitValue, jobRuntime);
	}

	private static String getTimedOutStatusXML(String user, String jobRuntime) {
		String template = "<status type='TimedOutPendingCompletion'>" +
                          "<user>%s</user>" +
				          "<job_runtime>%s</job_runtime>" +
				          "</status>";
		return String.format(template, user, jobRuntime);
	}

	private void sendJobStatusUpdate(String content) {
		sendJobStatusUpdateHelper(content, 0);
	}

	private void sendJobStatusUpdateHelper(String content, int numRetries) {
		try {
			String keystore = "/N/dc2/scratch/drhtrc/htrc-agent/karst/agent_dependencies/AJC.keystore";
			String keystorePsswd = "DUMMY_PASSWORD";
			SSLContext sslcontext = SSLContexts.custom().loadKeyMaterial(new File(keystore), keystorePsswd.toCharArray(), keystorePsswd.toCharArray()).build();

			SSLConnectionSocketFactory sslsf =
					new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1.1", "TLSv1.2" }, null, SSLConnectionSocketFactory.getDefaultHostnameVerifier());
			CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

			// CloseableHttpClient httpClient = HttpClients.createDefault();
			try {
				HttpPost httpPost = new HttpPost(agentEndPoint + getJobStatusUpdateURL());
				httpPost.setHeader("Authorization", "Bearer " + this.authToken);
				httpPost.setHeader("Content-type", "text/xml");
				StringEntity requestEntity = new StringEntity(content);
				httpPost.setEntity(requestEntity);

				CloseableHttpResponse response = httpClient.execute(httpPost);
				try {
					StatusLine statusLine = response.getStatusLine();
					int status = statusLine.getStatusCode();
					if (status > 300) {
						System.err.println("Unexpected response from the agent to \"job/id/updatestatus\" sent by AgentJobClient: "
								+ statusLine);
						EntityUtils.consume(response.getEntity());
						if (numRetries < MAX_RETRIES) {
							// the initial value of authToken, t0, is a password type token which might have expired;
							// retry the call to the agent with a freshly obtained client credentials type token; the
							// reason for multiple retries is explained below:
							// 
							// the agent and all instances of the AgentJobClient use the same oauth client to 
							// obtain client credentials type tokens; if such a token, t, has already been acquired
							// by some entity (such as the agent or an instance of the AJC) and is still valid, 
							// then the same token, t, is returned by the Identity Server in response to subsequent
							// requests for client credentials tokens; a new client credentials token, t', is
							// returned only after the old one, t, has expired; so, it is possible that in the 1st
							// retry of sendJobStatusUpdate, the client credentials token obtained is one with a 
							// very short amount of remaining validity period, such that it expires by the time
							// the "updatestatus" request containing the token is received by the agent; so, if the
							// 1st retry of sendJobStatusUpdate results in an error response from the agent, then 
							// perform a 2nd retry (starting with again obtaining a client credentials token)
							this.authToken = idServerClient.getClientCredentialsTypeToken();
							System.err.println("numRetries = " + numRetries + 
									"; Retrying \"job/id/updatestatus\" with client credentials token " + 
									this.authToken + ".");
							sendJobStatusUpdateHelper(content, numRetries + 1);
						}
					}
					else
						EntityUtils.consume(response.getEntity());
				} finally {
					response.close();
				}
			} catch (Exception e) {
				handleException(e);
			} finally {
				try {
					httpClient.close();
				} catch (IOException e) {
					handleException(e);
				}
			}
		} catch (Exception e) {
			handleException(e);
		}
	}
	
	private void handleException(Exception e) {
    	System.err.println("Exception during send of \"job/id/updatestatus\" from AgentJobClient to the agent.");
    	System.err.println(e);
        e.printStackTrace(System.err);
	}
	
	public void sendJobStatusRunning() {
		sendJobStatusUpdate(getRunningStatusXML(user));
	}
	
	public void sendJobStatusJobClientError(String errorMsg) {
		sendJobStatusUpdate(getJobClientErrorStatusXML(user, errorMsg));
	}

	public void sendJobStatusFinished(int jobExitValue, String jobRuntime) {
		sendJobStatusUpdate(getFinishedStatusXML(user, jobExitValue, jobRuntime));
	}

	public void sendJobStatusCrashed(int jobExitValue, String jobRuntime) {
		sendJobStatusUpdate(getCrashedStatusXML(user, jobExitValue, jobRuntime));
	}

	public void sendJobStatusTimedOut(String jobRuntime) {
		sendJobStatusUpdate(getTimedOutStatusXML(user, jobRuntime));
	}
}
