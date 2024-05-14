package com.voessing.api.adapter.auth;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import com.google.gson.JsonObject;
import com.voessing.common.http.HttpClient;
import com.voessing.common.http.Response;
import com.voessing.common.http.auth.AuthenticationHandler;

public class GraphOAuthAuthenticationHandler extends AuthenticationHandler {
    private HttpClient client;

    private String token;
    private LocalDateTime tokenExpires = null;

    private String clientId;
    private String clientSecret;
    private String tokenUrl;
    private String scope;

    public GraphOAuthAuthenticationHandler(String clientId, String clientSecret, String tokenUrl, String scope) {
        super();

        this.client = new HttpClient();
        this.client.setLogLevel(1);

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.scope = scope;
    }

    @Override
    public HttpUriRequestBase authenticate(HttpUriRequestBase request) {
        try {
            doAuth();
            request.setHeader("Authorization", "Bearer " + token);
            return request;
        } catch (Exception e) {
            e.printStackTrace();
            return request;
        }
    }

    @Override
    public void authenticateRequestFromCurrentState(HttpUriRequestBase request) {
        // Trigger initial authentication lazily and
        // check for expiration to prevent unnecessary requests
        if (token == null || isExpired()) {
            doAuth();
        }

        request.setHeader("Authorization", "Bearer " + token);
    }

    @Override
    public boolean isAuthenticated(Response response) {
        return response.getStatus() != 401 && response.getStatus() != 403;
    }

    private void doAuth() {
        try {
            HttpEntity body = new UrlEncodedFormEntity(
                    Arrays.asList(
                            new BasicNameValuePair("grant_type", "client_credentials"),
                            new BasicNameValuePair("client_id", clientId),
                            new BasicNameValuePair("client_secret", clientSecret),
                            new BasicNameValuePair("scope", scope)));

            Response res = client.post(tokenUrl, body);

            JsonObject data = res.parseWithGSON();
            token = data.get("access_token").getAsString();

            if (data.has("expires_in")) {
                tokenExpires = LocalDateTime.now().plusSeconds(data.get("expires_in").getAsLong());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isExpired() {
        // 60 seconds of leeway
        return tokenExpires == null || tokenExpires.isBefore(LocalDateTime.now().minusSeconds(60));
    }
}
