package htrc.agent.jobclient;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.URLStringParser;

public class ArgumentParser {
    private static Parameter[] getApplicationParameters() {
        Parameter workDir = new FlaggedOption("workDir")
                .setStringParser(
                        FileStringParser.getParser()
                                .setMustBeDirectory(true)
                                .setMustExist(true))
                .setRequired(true)
                .setShortFlag('d')
                .setHelp("Specifies the working directory to use for the job");

        Parameter shell = new FlaggedOption("shell")
                .setStringParser(
                        FileStringParser.getParser()
                                .setMustBeFile(true)
                                .setMustExist(true))
                .setShortFlag('s')
                .setDefault("/bin/bash")
                .setHelp("Specifies the shell to use for executing the job script");

        Parameter timelimit = new FlaggedOption("timelimit")
        		.setStringParser(JSAP.STRING_PARSER)
                .setShortFlag('t')
                .setRequired(true)
                .setHelp("Specifies the maximum time in hh:mm:ss or hhh:mm:ss to be given to the job.");

        Parameter jobid = new FlaggedOption("jobid")
				.setStringParser(JSAP.STRING_PARSER)
				.setShortFlag('j')
				.setRequired(true)
				.setHelp("Specifies the jobid.");

        Parameter user = new FlaggedOption("user")
				.setStringParser(JSAP.STRING_PARSER)
				.setShortFlag('u')
				.setRequired(true)
				.setHelp("Specifies the user that launched the job.");
        
        Parameter agentEndpoint = new FlaggedOption("agentEndpoint")
        		.setStringParser(URLStringParser.getParser())
				.setShortFlag('a')
				.setRequired(true)
				.setHelp("Specifies the agent endpoint that will be contacted by the AgentJobClient.");

        Parameter idServerTokenUrl = new FlaggedOption("idServerTokenUrl")
				.setStringParser(URLStringParser.getParser())
				.setShortFlag('i')
				.setRequired(true)
				.setHelp("Specifies the identity server URL that will be contacted by the AgentJobClient to obtain oauth tokens.");

        //        Parameter agent = new FlaggedOption("agent")
//                .setStringParser(URLStringParser.getParser())
//                .setShortFlag('a')
//                .setHelp("Specifies the URL to use for reporting job status information to the Agent");

        Parameter script = new UnflaggedOption("jobScript")
				.setStringParser(
						FileStringParser.getParser()
						.setMustBeFile(true)
						.setMustExist(true))
                .setRequired(true)
                .setGreedy(false)
                .setHelp("Job script file");

        Parameter arguments = new UnflaggedOption("arg")
                .setGreedy(true)
                .setHelp("Job arguments");

        return new Parameter[] { workDir, shell, timelimit, jobid, user, agentEndpoint, 
        		                 idServerTokenUrl, script, arguments };
    }

    private static String getApplicationHelp() {
        return "Wrapper for running an HTRC job and communicating job status and related information back to the Agent.";
    }

    public static JSAPResult parseArguments(String[] args) {
    	try {
    		SimpleJSAP jsap = new SimpleJSAP("AgentClient", getApplicationHelp(), getApplicationParameters());
        	JSAPResult result = jsap.parse(args);
        	if (jsap.messagePrinted())
        		return null;
        	else return result;
        } catch (JSAPException e) {
        	return null;
        }
    }

}
