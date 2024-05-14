package com.voessing.api.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voessing.api.adapter.auth.GraphOAuthAuthenticationHandler;
import com.voessing.common.TVAppCredStore;
import com.voessing.common.http.HttpClient;
import com.voessing.common.http.Options;
import com.voessing.common.http.Response;
import com.voessing.common.http.auth.AuthenticationException;
import com.voessing.common.http.auth.AuthenticationHandler;

public class GraphAPINew {

	private static final String NEXT_PAGE_URL_FIELD = "@odata.nextLink";
	private static final String CREDSTORE_KEY = "ms-graph-voessing-2";
	private static final int LOGLEVEL = 1;

	private String MS_API_VERSION;
	public HttpClient client;

	public static enum API_VERSION {
		Stable("v1.0"), Beta("beta");

		public final String apiVersion;

		private API_VERSION(String apiVersion) {
			this.apiVersion = apiVersion;
		}
	}

	public static class GraphUtil {
		public static JsonArray getJSONFromListResponse(List<Response> responses) {
			JsonArray result = new JsonArray();

			for (Response response : responses) {
				JsonObject content = response.parseWithGSON().getAsJsonObject();

				if (content.has("value")) {
					JsonArray value = content.getAsJsonArray("value");
					result.addAll(value);
				}
			}

			return result;
		}
	}

	public GraphAPINew() {
		this(API_VERSION.Beta);
	}

	public GraphAPINew(API_VERSION version) {

		String tokenUrl = TVAppCredStore.getValueByName(CREDSTORE_KEY, "authTokenUrl");
		String clientId = TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_id");
		String clientSecret = TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_secret");
		String scope = TVAppCredStore.getValueByName(CREDSTORE_KEY, "scope");

		AuthenticationHandler authHandler = new GraphOAuthAuthenticationHandler(clientId, clientSecret, tokenUrl, scope);
		authHandler.setMaxRetries(1);

		Options options = new Options().defaultOptions()
				.useAuthHandler(authHandler);

		client = new HttpClient(options);
		client.setLogLevel(LOGLEVEL);

		setApiVersion(version);
	}

	public void setApiVersion(API_VERSION version) {
		MS_API_VERSION = version.apiVersion;
		client.setBaseURI("https://graph.microsoft.com/" + MS_API_VERSION);
	}

	public List<Response> getEntirePagedResource(String url) throws IOException, AuthenticationException {
		int maxPages = 100;
		List<Response> responses = new ArrayList<>();

		while (true) {
			Response response = client.fetch(url);
			responses.add(response);

			JsonObject content = response.parseWithGSON().getAsJsonObject();
			if (!content.has(NEXT_PAGE_URL_FIELD) || maxPages-- <= 0) {
				break;
			}

			url = content.get(NEXT_PAGE_URL_FIELD).getAsString();
		}

		return responses;
	}

	public JsonObject getTeam(String teamId) throws IOException, AuthenticationException {
		return client.fetch("/teams/" + teamId).parseWithGSON().getAsJsonObject();
	}

}
