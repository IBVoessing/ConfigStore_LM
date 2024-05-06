package com.voessing.vcde.endpoints.vrh.crawler;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TVAppCredStore2;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

public class Test extends VrhHttpHandler {

	
	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("GET, POST, HEAD");
		return initialConfig;
	}

    private static final String CREDSTORE_KEY = "trello_lm";
    
	@Override
	protected String doGet(HttpServletRequest request) throws Exception {

        TVAppCredStore2 credStore = new TVAppCredStore2(Factory.getSession(SessionType.NATIVE));
        String apiKey = credStore.getValueByName(CREDSTORE_KEY, "apiKey");
        String apiToken = credStore.getValueByName(CREDSTORE_KEY, "apiToken");
        String authHeader = "OAuth oauth_consumer_key=\"" + apiKey + "\", oauth_token=\"" + apiToken + "\"";
        
        String boardGumbaId = "662ba0a35610c87d77d9a89c";

		StringBuilder csvBuilder = new StringBuilder();
		csvBuilder.append("id,name\n");
		csvBuilder.append("1,John\n");
		String csv = csvBuilder.toString();

		TrelloAPI api = new TrelloAPI();
		api.createAttachmentOnCard(boardGumbaId, "test.csv", csv.getBytes());
		return "ksdfjlsf";
	}

	// @Override
	// protected String doGet(HttpServletRequest request) throws Exception {
	// 	BasicCredentialsProvider provider = new BasicCredentialsProvider();
	// 	provider.setCredentials(AuthScope.ANY, (Credentials) new UsernamePasswordCredentials("allo", "gumba".toCharArray() ));
	// 	String gumba = Request.get("http://httpbin.org/get").execute().returnContent().asString(StandardCharsets.UTF_8);
	// 	System.out.println(gumba);

	// 	return "";
	// }

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {
		
		return "skfjaskjfklsajfljasdf";
	}

	
}
