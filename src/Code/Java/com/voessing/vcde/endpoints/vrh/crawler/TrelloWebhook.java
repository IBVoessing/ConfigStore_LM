package com.voessing.vcde.endpoints.vrh.crawler;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.voessing.common.TNotesUtil;
import com.voessing.common.TVAppCredStore2;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

public class TrelloWebhook extends VrhDominoNotesDocumentHandler {
	
    private static final String CREDSTORE_KEY = "trello_lm";

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("POST, HEAD");
		
		config.setFormName("TrelloWebhookEntry");
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));
		config.getAttributeConfigs().put("httpUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_HTTPURL));

		config.withPayloadDumping("requestBody");
		
		// has to be set as getCurrentDatabase returns null if used via ODA Session????
		config.setDatabaseName("VCDE-Config_LM.nsf");
		config.withAllowAnonymous();

		return initialConfig;
	}
			
	@Override
	protected lotus.domino.Session getSession() {
		return Factory.getSession(SessionType.NATIVE);
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws Exception {
		// validate that the request is from Trello 
		if (request.getMethod().equals("POST") && !verifyTrelloWebhookRequest(request)) {
			throw new VrhException(401, "Nono don't touch me there this is my nono square");
		}
	}

	private boolean verifyTrelloWebhookRequest(HttpServletRequest request) throws Exception {
		// contains (((body+callbackURL) HmacSHA1 with appSecret) base64) 
		String headerHash = request.getHeader("X-Trello-Webhook");

		if(headerHash == null){
			return false;
		}

		TVAppCredStore2 creds = new TVAppCredStore2(getSession());
		String appSecret = creds.getValueByName(CREDSTORE_KEY, "appSecret");
		String callbackURL = creds.getValueByName(CREDSTORE_KEY, "callbackURL");
		
		//trigger capturedPayloadRaw to be available
		getRequestPayload(request);
		
		// build hash inc encoding
		String payload = capturedPayloadRaw + callbackURL;
		String computedHash = computeHmacSha1(payload, appSecret);
		
		//verify 
		return computedHash.equals(headerHash);
	}

	private static String computeHmacSha1(String payload, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
		
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(secretKeySpec);
		
		byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		
		return Base64.getEncoder().encodeToString(hash);
	}

}
