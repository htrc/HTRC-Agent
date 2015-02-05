package htrc.agent.jobclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class IdentityServerClient {
	private String idServerTokenUrl;
	private String oauthClientId;
	private String oauthClientSecret;
	
	public IdentityServerClient(String idServerTokenUrl, String oauthClientId, String oauthClientSecret) {
		this.idServerTokenUrl = idServerTokenUrl;
		this.oauthClientId = oauthClientId;
		this.oauthClientSecret = oauthClientSecret;
	}
	
	public String getClientCredentialsTypeToken() {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		String result = "";
		try {
			HttpPost httpPost = new HttpPost(idServerTokenUrl);
			httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");

			List<NameValuePair> formparams = new ArrayList<NameValuePair>();
			formparams.add(new BasicNameValuePair("client_id", oauthClientId));
			formparams.add(new BasicNameValuePair("client_secret", oauthClientSecret));
			formparams.add(new BasicNameValuePair("grant_type", "client_credentials"));
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, Consts.UTF_8);
			httpPost.setEntity(entity);
					    
			StringResponseHandler rh = new StringResponseHandler();
			String tokenResponse = httpClient.execute(httpPost, rh);
            System.out.println("getClientCredentialsTypeToken: tokenResponse = {}" + tokenResponse);

        	String accessTokenFieldName = Constants.ACCESS_TOKEN_FIELD_NAME;
        	int accessTokenFieldNameLength = accessTokenFieldName.length();
        	
            int i = tokenResponse.indexOf(accessTokenFieldName);
            // the response is of the form "access_token":"<token>", including quotes
            int startIndex = i + accessTokenFieldNameLength + 3; 
            if (i >= 0) {
            	// obtain token from enclosed within "", as shown above
            	result = tokenResponse.substring(startIndex, tokenResponse.indexOf("\"", startIndex));
            }
		} catch (Exception e) {
			handleException("request for client credentials type token", e);
		} finally {
			try {
				httpClient.close();
			} catch (IOException e) {
				handleException("close of HttpClient", e);
			}
		}
		return result;
	}
	
	private void handleException(String opDescription, Exception e) {
    	System.err.println("Exception during " + opDescription);
    	System.err.println(e);
        e.printStackTrace(System.err);
	}

}
