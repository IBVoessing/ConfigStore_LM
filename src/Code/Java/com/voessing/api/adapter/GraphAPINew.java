package com.voessing.api.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.voessing.api.adapter.auth.GraphOAuthAuthenticationHandler;
import com.voessing.common.TNotesUtil;
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

	/**
	 * Enum representing the API versions for Microsoft Graph API.
	 *
	 * This enum contains two versions: Stable and Beta. Each version has a
	 * corresponding
	 * string value that is used in the base URI for Microsoft Graph API requests.
	 */
	public static enum API_VERSION {
		/**
		 * Represents the stable version of the Microsoft Graph API.
		 */
		Stable("v1.0"),

		/**
		 * Represents the beta version of the Microsoft Graph API.
		 */
		Beta("beta");

		/**
		 * The string value of the API version.
		 */
		public final String apiVersion;

		/**
		 * Constructs a new API_VERSION instance with the specified string value.
		 *
		 * @param apiVersion the string value of the API version
		 */
		private API_VERSION(String apiVersion) {
			this.apiVersion = apiVersion;
		}
	}

	/**
	 * Utility class for handling operations related to Microsoft Graph API.
	 */
	public static class GraphUtil {

		/**
		 * Converts a list of responses into a JsonArray.
		 *
		 * This method iterates over a list of responses, parses each response into a
		 * JsonObject,
		 * and if the JsonObject has a "value" field, adds its value (which is a
		 * JsonArray) to the result.
		 *
		 * @param responses a list of responses to convert
		 * @return a JsonArray containing the values from the responses
		 */
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
		// Load credentials from TVAppCredStore
		String tokenUrl = TVAppCredStore.getValueByName(CREDSTORE_KEY, "authTokenUrl");
		String clientId = TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_id");
		String clientSecret = TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_secret");
		String scope = TVAppCredStore.getValueByName(CREDSTORE_KEY, "scope");
		
		// Create authentication handler
		AuthenticationHandler authHandler = new GraphOAuthAuthenticationHandler(clientId, clientSecret, tokenUrl, scope);
		authHandler.setMaxRetries(1);

		// Create the Options object setting default options and enabling the authentication handler 
		Options options = new Options().defaultOptions()
				.useAuthHandler(authHandler);

		// Create the HttpClient with the options
		client = new HttpClient(options);
		client.setLogLevel(LOGLEVEL);

		// BaseURI is set to the Microsoft Graph API base URI with the specified API version
		setApiVersion(version);
	}

	/**
	 * Sets the API version for Microsoft Graph API requests.
	 *
	 * This method updates the API version used in the base URI of the HTTP client.
	 * The base URI is used for all subsequent requests to the Microsoft Graph API.
	 *
	 * @param version the API version to set, represented as an instance of the
	 *                API_VERSION enum
	 */
	public void setApiVersion(API_VERSION version) {
		MS_API_VERSION = version.apiVersion;
		client.setBaseURI("https://graph.microsoft.com/" + MS_API_VERSION);
	}

	/**
	 * Fetches all pages of a paged resource from a given URL.
	 *
	 * This method sends HTTP requests to the provided URL and collects the
	 * responses.
	 * It continues to fetch subsequent pages as long as the response contains a
	 * field
	 * indicating the URL of the next page and the number of fetched pages does not
	 * exceed
	 * a maximum limit.
	 *
	 * @param url the URL of the resource to fetch
	 * @return a list of responses from all fetched pages
	 * @throws IOException             if an I/O error occurs during the fetch
	 * @throws AuthenticationException if an authentication error occurs during the
	 *                                 fetch
	 */
	public List<Response> getEntirePagedResource(String url) throws IOException, AuthenticationException {
		List<Response> responses = new ArrayList<>();

		while (true) {
			Response response = client.fetch(url.replace("https://graph.microsoft.com/beta", ""));
			responses.add(response);

			JsonObject content = response.parseWithGSON().getAsJsonObject();
			if (!content.has(NEXT_PAGE_URL_FIELD)) {
				break;
			}

			url = content.get(NEXT_PAGE_URL_FIELD).getAsString();
		}

		return responses;
	}

	/**
	 * Fetches the details of a team from Microsoft Graph API.
	 *
	 * This method sends a GET request to the "/teams/{teamId}" endpoint of the
	 * Microsoft Graph API.
	 *
	 * @param teamId the ID of the team to fetch
	 * @return a JsonObject representing the team details
	 * @throws IOException             if an I/O error occurs during the fetch
	 * @throws AuthenticationException if an authentication error occurs during the
	 *                                 fetch
	 */
	public JsonObject getTeam(String teamId) throws IOException, AuthenticationException {
		return client.fetch("/teams/" + teamId).parseWithGSON().getAsJsonObject();
	}

	/**
	 * Fetches the details of a team from Microsoft Graph API.
	 *
	 * This method is similar to getTeam, but uses a generic GET method to fetch the
	 * team details.
	 *
	 * @param teamId the ID of the team to fetch
	 * @return a JsonObject representing the team details
	 * @throws IOException             if an I/O error occurs during the fetch
	 * @throws AuthenticationException if an authentication error occurs during the
	 *                                 fetch
	 */
	public JsonObject getTeam2(String teamId) throws IOException, AuthenticationException {
		return get("/teams/" + teamId);
	}

	/**
	 * Sends a GET request to the specified URL and returns the response.
	 *
	 * This method sends a GET request to the specified URL and parses the response
	 * using GSON.
	 * The parsed response is returned as an instance of a subclass of JsonElement.
	 *
	 * @param url the URL to send the GET request to
	 * @return the parsed response, as an instance of a subclass of JsonElement
	 * @throws IOException             if an I/O error occurs during the fetch
	 * @throws AuthenticationException if an authentication error occurs during the
	 *                                 fetch
	 */
	@SuppressWarnings("unchecked")
	public <T extends JsonElement> T get(String url) throws IOException, AuthenticationException {
		return (T) client.fetch(url).parseWithGSON();
	}

}
