// StreamReader
//   reads input stream until end-of-stream is reached, and then writes contents to a 
//   specified file
package htrc.agent.jobclient;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;

public class StreamReader extends Thread {
	private InputStream input;
	private String outFile;
	private Logger log;
	private String logPrefix;
	private AgentClient agentClient;
	
	public StreamReader(InputStream input, String outFile, Logger log, String logPrefix,
			            AgentClient agentClient) {
		this.input = input;
		this.outFile = outFile;
		this.log = log;
		this.logPrefix = logPrefix;
		this.agentClient = agentClient;
	}
	
    private void readStream() {
		StringBuffer sb = new StringBuffer();
		PrintWriter writer = null;
		
		try {
	    	BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
			writer = new PrintWriter(outFile, "UTF-8");
			String line;
			while ((line = reader.readLine()) != null) {
				// log.debug(logPrefix + ": {}", line);
				sb.append(line + "\n");
			}
			writer.write(sb.toString());
		} catch (FileNotFoundException e) {
			String errorMsg = e.toString();
			handleExceptionAndExit(errorMsg, e);
		} catch (UnsupportedEncodingException e) {
			String errorMsg = e.toString();
			handleExceptionAndExit(errorMsg, e);
		} catch (IOException e) {
			// IOException occurs, amongst other scenarios, when Process.destroy() is called on
			// the process whose error or output stream is being read here
			System.err.println(logPrefix + ": " + e);
			if (writer != null) writer.write(sb.toString());
		} finally {
			if (writer != null)	writer.close();
		}
    }
    
    private void handleExceptionAndExit(String errorMsg, Exception e) {
		System.err.println(e);
		e.printStackTrace(System.err);
		agentClient.sendJobStatusJobClientError(errorMsg);
		System.exit(1);
    }
    
    public void run() {
		readStream();
	}
}
