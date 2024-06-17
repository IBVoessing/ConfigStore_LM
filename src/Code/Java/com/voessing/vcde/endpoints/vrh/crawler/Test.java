package com.voessing.vcde.endpoints.vrh.crawler;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;

import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import nl.martijndwars.webpush.Subscription.Keys;



public class Test extends VrhHttpHandler {
	
    private static final String publicKey = "BOa7SIqFD8eLsxmSTpe1xebkai_TNYYXvXJxO_Q3jLpe38Q8gJVLgUHF0dP1gxzAK5IuQpDJs6gp2EYC-z5KX_0";
    private static final String privateKey = "AIm9uJh2mDwpsoaKxj3AmTwSTDRSQmRpr_OjM--VMbc";
    private static final String notificationSubject = "mailto:leonardo.malzacher@voessing.de";

    
    public static void sendPushMessage(Subscription sub, String payload)
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {

        Notification notification;
        PushService pushService = pushService();

        // Create a notification with the endpoint, userPublicKey from the subscription
        // and a custom payload
        notification = new Notification(sub, payload);

        // Send the notification
        
        HttpPost post = pushService.preparePost(notification, Encoding.AES128GCM);
        
        CloseableHttpClient client = HttpClientBuilder.create().build();
        client.execute(post);
        
        //pushService.send(notification);
    }
    
    public static PushService pushService() throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            System.out.println("Security provider:" + Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));
        	Security.addProvider(new BouncyCastleProvider());
        }
        System.out.println("Security provider:" + Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));
        PushService ps = new PushService();

        ps.setSubject(notificationSubject);
        ps.setPrivateKey(privateKey);
        ps.setPublicKey(publicKey);
        
        return ps;
        //return new PushService(publicKey, privateKey, notificationSubject);
    }

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig,
			Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("GET, POST, HEAD");
		return initialConfig;
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {
		return "bob";		
	}

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {
//		Session session = Factory.getSession(SessionType.NATIVE);
//		session.boogie();
//		getRequestPayload(request);
//		TNotesUtil.logEvent(capturedPayloadRaw);
		System.out.println("Hello world!");

		Subscription sub = new Subscription();
        Keys keys = new Keys("BGiAF0Y6fOhlhUfWC5Mk_iHdxZdQImwrMnSeAmJBeBf7k-OmLpWtuujNzOrIpPP2B-eebYQT1o6ijPvWGUY-cyU",
                "qT4CmPEisLw573fsz2LF3g");
        sub.endpoint = "https://updates.push.services.mozilla.com/wpush/v2/gAAAAABmUF9hcHlI_iMCR_7NKlXLkB_kKQ0mW3eaU7RV3kr4dvTH3EGMav918H5hf8V42BwZWgztHJxzeEuxi_48NnfHbbzdccBO7M7-v1cKZjn2OSFfkcG6OgQZPZVzpwaomOY3WGxzlmsNprvp92-aFqY1i4bZ6WrBuqTZl0vF9beOvWgkQvg";
        sub.keys = keys;

        String jsonString = "{"
                + "\"title\" : \"New Teams Team\","
                + "\"message\": \"A Microsoft Teams Team has been added to your Project \\\"Informationstechnik\\\" 0063\""
                + "}";

        sendPushMessage(sub, jsonString);
		return "skfjaskjfklsajfljasdf";
	}

}
