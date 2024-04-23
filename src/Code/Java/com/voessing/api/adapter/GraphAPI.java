package com.voessing.api.adapter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.voessing.common.TDateTimeUtil;
import com.voessing.common.TRestConsumerEx;
import com.voessing.common.TVAppCredStore;
import com.voessing.vcde.VCDEUtil;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Session;

// Generelle MS Graph API
public class GraphAPI {


	private static final String AUTH_DISABLED_MSG = "Auth is disabled in this session due to unrecoverable auth errors";
	private static final String NEXT_PAGE_URL_FIELD = "@odata.nextLink";
	private static final String API_VER = "1.3";
	private static final int API_LOGLEVEL = 1;

	private static final String CREDSTORE_KEY = "ms-graph-voessing-2";
	private static final String MS_API_URL = "https://graph.microsoft.com";
	private static final int RETRY_LIMIT = 100;
	private static final int EXCEPTION_RETRY_LIMIT = 5;

	private String MS_API_VERSION;

	private TRestConsumerEx api = null;

	private String apiToken = "";
	private LocalDateTime apiTokenExpires = null;
	private boolean authRetried = false;
	private boolean authDisabled = false;
	private int retryTime = 1; // in s
	private int retryCount = 0;

	public static enum API_VERSION {
		Stable("v1.0"), Beta("beta");

		public final String apiVersion;

		private API_VERSION(String apiVersion) {
			this.apiVersion = apiVersion;
		}
	}

	public GraphAPI() {
		setApiVersion(API_VERSION.Beta);
	}

	public GraphAPI(API_VERSION version) {
		setApiVersion(version);
	}

	public void setApiVersion(API_VERSION version) {
		MS_API_VERSION = version.apiVersion;
	}

	private JsonJavaObject getAuthObject() throws Exception {

		TRestConsumerEx api = new TRestConsumerEx("");
		api.setLogLevel(API_LOGLEVEL);
		api.setPostType(TRestConsumerEx.PostType.X_WWW_FORM_URLENCODED);

		// load variables from App-CredStore
		String authTokenUrl = TVAppCredStore.getValueByName(CREDSTORE_KEY, "authTokenUrl");

		JsonJavaObject postObj = new JsonJavaObject();
		postObj.put("grant_type", TVAppCredStore.getValueByName(CREDSTORE_KEY, "grant_type"));
		postObj.put("client_id", TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_id"));
		postObj.put("client_secret", TVAppCredStore.getValueByName(CREDSTORE_KEY, "client_secret"));
		postObj.put("scope", TVAppCredStore.getValueByName(CREDSTORE_KEY, "scope"));

		Object response = api.doPost(authTokenUrl, postObj);

		if (api.getLastResponseCode() == 200) {
			return (JsonJavaObject) response;
		} else {
			return null;
		}

	}

	private void authorize(boolean resetAuth) throws Exception {

		if (authDisabled)
			return;

		if (resetAuth) {
			// only accept 1 retry for a token
			authRetried = true;
			apiToken = "";
			apiTokenExpires = null;
		}

		if (apiTokenExpires != null && apiTokenExpires.isBefore(TDateTimeUtil.convertToLocalDateTime(new Date()))) {
			// if the cached token is expired, reset all token information
			authRetried = false;
			apiToken = "";
			apiTokenExpires = null;
		}

		// if no token is available, retrieve a token
		if (apiToken.isEmpty()) {

			// first try the cached token from the named document
			// if this fails, retrieve a new token

			Document authTokens = null;

			try {
				Session session = NotesContext.getCurrent().getCurrentSession();
				Database db = session.getCurrentDatabase();

				authTokens = db.getNamedDocument("AuthTokens");
				String graphAuthStr = authTokens.getItemValueString("graph");

				if (!graphAuthStr.isEmpty()) {

					JsonJavaObject graphAuth = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, graphAuthStr);

					// check if the stored token is still valid (datecheck)
					LocalDateTime expires = LocalDateTime.parse(graphAuth.getString("apiTokenExpires"));

					if (expires.isAfter(TDateTimeUtil.convertToLocalDateTime(new Date()))) {
						apiToken = graphAuth.getString("access_token");
						apiTokenExpires = expires;
						api.setBearerAuth(apiToken);
						return;
					}
				}
			} catch (Exception e) {
				System.out.println("Caching failed! Reason: Database Maximum Internet name and password must be: Editor ");
			}


			JsonJavaObject authObject = getAuthObject();

			if (authObject != null && authObject.containsKey("access_token")) {

				apiToken = authObject.getString("access_token");

				// set expires info: 60 seconds less
				apiTokenExpires = TDateTimeUtil.convertToLocalDateTime(new Date());
				apiTokenExpires = apiTokenExpires.plusSeconds(authObject.getLong("expires_in") - 60);

				System.out.println("APITOKEN LEN: " + apiToken.length());
				System.out.println("APITOKEN EXP: " + apiTokenExpires.toString());

				// cache token
				if (authTokens != null) {
					authObject.put("apiTokenExpires", apiTokenExpires.toString());
					authTokens.replaceItemValue("graph", authObject.toString());
					authTokens.save();
					authTokens.recycle();

					System.out.println("TOKEN CACHED");
				}

				api.setBearerAuth(apiToken);

			} else {
				authDisabled = true;
			}

		}

	}

	// helper method to wait
	private static void waitRetry(long s) {
		long ms = s * 1000; // convert input to ms
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}


	public JsonJavaObject post(String apiCommand, JsonJavaObject postObj) throws Exception {
		return post(apiCommand, postObj, null);
	}

	public JsonJavaObject post(String apiCommand, JsonJavaObject postObj, LinkedHashMap<String, Object> headers) throws Exception {

		prepareApi();
		if (authDisabled) {
			throw new Exception(AUTH_DISABLED_MSG);
		}

		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		String url = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand);

		// wrap the response in a JsonJavaObject
		JsonJavaObject response = new JsonJavaObject();
		response.put("body", api.doPost(url, postObj));
		response.put("httpStatus", api.getLastResponseCode());
		response.put("headers", api.getLastResponseHeaders());

		// statuscode between 200 and 299 is considered as success
		if (api.getLastResponseCode() >= 200 && api.getLastResponseCode() < 300) {
			retryCount = 0;
			return response;
		} else if (api.getLastResponseCode() == 401 && !authRetried) {
			/// 1x Re-Auth erlauben
			authorize(true);
			return post(apiCommand, postObj);
		} else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT)
				return response;
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			waitRetry(retryTime);
			return post(apiCommand, postObj);
		} else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			return response;
		}
	}

	public JsonJavaObject patch(String apiCommand, JsonJavaObject postObj) throws Exception {
		return patch(apiCommand, postObj, null);
	}

	public JsonJavaObject patch(String apiCommand, JsonJavaObject postObj, LinkedHashMap<String, Object> headers) throws Exception {
		prepareApi();
		if (authDisabled)
			throw new Exception(AUTH_DISABLED_MSG);

		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		String url = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand);

		// wrap the response in a JsonJavaObject
		JsonJavaObject response = new JsonJavaObject();
		response.put("body", api.doPatch(url, postObj));
		response.put("httpStatus", api.getLastResponseCode());
		response.put("headers", api.getLastResponseHeaders());

		if (api.getLastResponseCode() >= 200 && api.getLastResponseCode() < 300) {
			retryCount = 0;
			return response;
		} else if (api.getLastResponseCode() == 401 && !authRetried) {
			/// 1x Re-Auth erlauben
			authorize(true);
			return patch(apiCommand, postObj);
		} else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT)
				return response;
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			waitRetry(retryTime);
			return patch(apiCommand, postObj);
		} else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			return response;
		}
	}

	public JsonJavaObject delete(String apiCommand) throws Exception {
		return delete(apiCommand, null);
	}

	public JsonJavaObject delete(String apiCommand, LinkedHashMap<String, Object> headers) throws Exception {
		prepareApi();
		if (authDisabled)
			throw new Exception(AUTH_DISABLED_MSG);

		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		String url = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand);

		// wrap the response in a JsonJavaObject
		JsonJavaObject response = new JsonJavaObject();
		response.put("body", api.doDelete(url));
		response.put("httpStatus", api.getLastResponseCode());
		response.put("headers", api.getLastResponseHeaders());

		if (api.getLastResponseCode() >= 200 && api.getLastResponseCode() < 300) {
			retryCount = 0;
			return response;
		} else if (api.getLastResponseCode() == 401 && !authRetried) {
			/// 1x Re-Auth erlauben
			authorize(true);
			return delete(apiCommand);
		} else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT)
				return response;
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			waitRetry(retryTime);
			return delete(apiCommand);
		} else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			return response;
		}
	}

	/**
	 * Retrieves a single JSON object from the specified API endpoint. Catches java.net.SocketTimeoutException and retries the request up to 5
	 * times.
	 *
	 * @param apiCommand The API command to retrieve the object.
	 * @param params The parameters to include in the API request.
	 * @return The retrieved JSON object.
	 * @throws Exception If an error occurs while retrieving the object.
	 */
	public JsonJavaObject getSingleObject2(String apiCommand, LinkedHashMap<String, Object> params) throws Exception {
		return getSingleObject2(apiCommand, params, 0);
	}

	public JsonJavaObject getSingleObject2(String apiCommand, LinkedHashMap<String, Object> params, int attempt) throws Exception {
		try {
			if (attempt > EXCEPTION_RETRY_LIMIT)
				throw new Exception("Too many retries for getSingleObject2 for URL: " + apiCommand);
			return getSingleObject(apiCommand, params);
		} catch (Exception e) {
			return getSingleObject2(apiCommand, params, attempt + 1);
		}
	}

	public JsonJavaObject getSingleObject(String apiCommand, LinkedHashMap<String, Object> params) throws Exception {
		return getSingleObject(apiCommand, params, null);
	}

	// Core-Routine zum Abgriff eines einzelnen Response-JSON-Objects
	public JsonJavaObject getSingleObject(String apiCommand, LinkedHashMap<String, Object> params, LinkedHashMap<String, Object> headers) throws Exception {

		prepareApi();

		if (authDisabled)
			throw new Exception(AUTH_DISABLED_MSG);

		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		String url = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);

		JsonJavaObject json = (JsonJavaObject) api.doGet(url);

		// 2023-10-06, lma/dko: handle Bandwidth Limit Exceeded
		// -> 5 Sekunden warten, Request 1x neu ausf?hren und weiter. Kann sp?ter mal ausgebaut werden f?r bessere Retries

		if (api.getLastResponseCode() == 200) {
			// reset retry values
			retryCount = 0;
			// retryTime = 1;
			return json;
		} else if (api.getLastResponseCode() == 401 && !authRetried) {

			/// 1x Re-Auth erlauben
			authorize(true);

			// rekursiv-Call
			return getSingleObject(apiCommand, params);

		} else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT)
				raiseError(null, json);
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			// TODO: read Retry-smth Field from Headers and use this time
			// instead >> not every API returns it and i could not trigger it so
			// far
			waitRetry(retryTime);
			// set new retry time
			// retryTime = retryTime * 2;
			return getSingleObject(apiCommand, params);
		} else if (api.getLastResponseCode() == 403 || api.getLastResponseCode() == 500 || api.getLastResponseCode() == 502 || api.getLastResponseCode() == 503) {
			// handle sporadic errors of graph
			if (retryCount >= RETRY_LIMIT)
				raiseError(null, json);
			retryCount++;
			return getSingleObject(apiCommand, params);
		} else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			raiseError(null, json);
		}

		return null;

	}

	/**
	 * Retrieves a paged collection of data from the API. Catches java.net.SocketTimeoutException and retries the request up to 5 times.
	 *
	 * @param apiCommand The API command to execute.
	 * @param params The parameters to include in the API request.
	 * @return A paged collection of data in the form of a {@link JsonJavaArray}.
	 * @throws Exception If an error occurs while retrieving the data.
	 */
	public JsonJavaArray getCollectionPaged2(String apiCommand, LinkedHashMap<String, Object> params) throws Exception {
		return getCollectionPaged2(apiCommand, params, 0);
	}

	public JsonJavaArray getCollectionPaged2(String apiCommand, LinkedHashMap<String, Object> params, int attempt) throws Exception {
		try {
			if (attempt > EXCEPTION_RETRY_LIMIT)
				throw new Exception("Too many retries for getCollectionPaged2 for URL: " + apiCommand);
			return getCollectionPaged(apiCommand, params);
		} catch (Exception e) {
			return getCollectionPaged2(apiCommand, params, attempt + 1);
		}
	}

	public JsonJavaArray getCollectionPaged(String apiCommand, LinkedHashMap<String, Object> params) throws Exception {
		return getCollectionPaged(apiCommand, params, null);
	}

	// Core-Routine zum Verarbeiten der Paged Response
	public JsonJavaArray getCollectionPaged(String apiCommand, LinkedHashMap<String, Object> params, LinkedHashMap<String, Object> headers) throws Exception {
		return getCollectionPaged(apiCommand, params, headers, -1);
	}

	// Core-Routine zum Verarbeiten der Paged Response
	public JsonJavaArray getCollectionPaged(String apiCommand, LinkedHashMap<String, Object> params, LinkedHashMap<String, Object> headers, int maxEntries) throws Exception {

		prepareApi();

		if (authDisabled)
			throw new Exception(AUTH_DISABLED_MSG);

		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		int page = 0;

		JsonJavaArray retArr = new JsonJavaArray();

		String nextPageUrl = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);

		while (!nextPageUrl.isEmpty() && (maxEntries == -1 || retArr.size() < maxEntries)) {

			// query next page, reset hasMore
			page++;
			JsonJavaObject json = (JsonJavaObject) api.doGet(nextPageUrl);

			// 2023-10-06, lma/dko: handle Bandwidth Limit Exceeded
			if (api.getLastResponseCode() == 429) {
				if (retryCount >= RETRY_LIMIT)
					raiseError(null, json);
				System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "s... Retries so far: " + retryCount);
				retryCount++;
				page--;
				// TODO: read Retry-smth Field from Headers and use this time
				// instead >> not every API returns it and i could not trigger
				// it so far
				waitRetry(retryTime);
				// set new retry time
				// retryTime = retryTime * 2;
				continue;
			} else if (api.getLastResponseCode() == 403 || api.getLastResponseCode() == 500 || api.getLastResponseCode() == 502 || api.getLastResponseCode() == 503) {
				// handle sporadic errors of graph
				if (retryCount >= RETRY_LIMIT)
					raiseError(null, json);// fix
				retryCount++;
				page--;
				continue;
			}

			// reset Url
			nextPageUrl = "";

			if (api.getLastResponseCode() == 200) {

				// reset Retry values
				retryCount = 0;
				// retryTime = 1;

				retArr.addAll(json.getAsArray("value"));

				// decide if we need to query another page
				if (json.containsKey(NEXT_PAGE_URL_FIELD)) {
					nextPageUrl = json.getString(NEXT_PAGE_URL_FIELD);
				}

			} else if (api.getLastResponseCode() == 401 && !authRetried) {

				/// 1x Re-Auth erlauben
				authorize(true);

				// restart the whole request from page 1
				page = 0;
				retArr = new JsonJavaArray();
				nextPageUrl = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);

			} else if (api.getLastResponseCode() == 404) {
				return retArr;
			} else {
				System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + nextPageUrl);
				raiseError(page, json);
			}

		} // while nextPageUrl

		// System.out.println("PROCESSED PAGES: " + page);

		// reduce the result to the maxEntries
		if (maxEntries != -1 && retArr.size() > maxEntries) {
			retArr = new JsonJavaArray(retArr.subList(0, maxEntries));
		}

		return retArr;

	}

	/**
	 * Adds request headers to the api object.
	 * 
	 * @param headers a LinkedHashMap of header names and values
	 * 
	 */
	private void addRequestHeaders(LinkedHashMap<String, Object> headers) {
		if (api != null && headers != null) {
			for (String key : headers.keySet()) {
				api.addRequestHeader(key, headers.get(key));
			}
		}
	}

	private void raiseError(Integer page, JsonJavaObject json) throws Exception {

		// compile error message string, use details if available
		StringBuilder errorMsg = new StringBuilder();
		errorMsg.append(api.getLastResponseCode());
		errorMsg.append(": ");
		errorMsg.append(api.getLastResponseMessage());
		errorMsg.append(" (");

		List<String> info = new ArrayList<>();

		if (page != null)
			info.add("at page " + page);

		if (json != null)
			System.out.println("JSON RESP: " + json.toString());

		if (json != null && json.containsKey("error")) {
			JsonJavaObject jsonErr = json.getAsObject("error");
			info.add(jsonErr.getString("code"));
			info.add(jsonErr.getString("message"));
		}

		errorMsg.append(String.join(", ", info));
		errorMsg.append(")");

		throw new Exception(errorMsg.toString());

	}

	private void prepareApi() throws Exception {

		if (api == null) {

			System.out.println(this.getClass().getName() + " v" + API_VER + ", logLevel=" + API_LOGLEVEL);

			api = new TRestConsumerEx("");
			api.setLogLevel(API_LOGLEVEL);

		}

		// authorize with no reset
		authorize(false);

	}

	/**
	 * 
	 * @param additionalUrlParameters can be null or has to start with & as there is already a parameter in use
	 * @return returns all M365 Groups
	 * @throws Exception
	 */
	public JsonJavaArray getM365Groups(String additionalUrlParameters) throws Exception {
		if (additionalUrlParameters == null) {
			additionalUrlParameters = "";
		}
		return this.getCollectionPaged("/groups?$filter=groupTypes/any(c:c+eq+'Unified')" + additionalUrlParameters, null);
	}

	public JsonJavaArray getMailDistributionGroups() throws Exception {
		JsonJavaArray groups = this.getCollectionPaged("/groups?$filter=mailEnabled%20eq%20true", null);
		List<Object> mailGroups = groups.stream().filter(group -> ((JsonJavaObject) group).getAsList("groupTypes").isEmpty()).collect(Collectors.toList());
		return new JsonJavaArray(mailGroups);
	}

	/**
	 * 
	 * @return returns all groups that are also a teams team
	 * @throws Exception
	 */
	public JsonJavaArray getAllGroupsThatAreTeams() throws Exception {

		LinkedHashMap<String, Object> params = new LinkedHashMap<>();
		params.put("$filter", "resourceProvisioningOptions/Any(x:x eq 'Team')");
		JsonJavaArray teamsGroups = getCollectionPaged("/groups", params);
		for (int i = 0; i < teamsGroups.length(); i++) {
			JsonJavaObject group = teamsGroups.getAsObject(i);
			String groupId = group.getAsString("id");
			JsonJavaObject relatedTeamInfo = getTeamOfGroup(groupId);
			group.put("relatedTeam", relatedTeamInfo);
		}

		return teamsGroups;

	}

	/**
	 * 
	 * @return returns all teams teams
	 * @throws Exception
	 */
	public JsonJavaArray getAllTeams() throws Exception {
		return getCollectionPaged("/teams", null);
	}

	/**
	 * 
	 * @param teamGroupId id to identify a team or group
	 * @return returns the team based on the groupId (Note teamId == groupId)
	 * @throws Exception
	 */
	public JsonJavaObject getTeamOfGroup(String teamGroupId) throws Exception {
		return getSingleObject("/groups/" + teamGroupId + "/team", null);
	}

	/**
	 * 
	 * @return returns all planner plans found
	 * @throws Exception
	 */
	public JsonJavaArray getAllPlannerPlans() throws Exception {
		JsonJavaArray groups = this.getM365Groups("&select=id,displayName,description,resourceProvisioningOptions");
		JsonJavaArray result = new JsonJavaArray();
		for (int i = 0; i < groups.length(); i++) {
			String groupId = groups.getAsObject(i).getString("id");
			String groupName = groups.getAsObject(i).getString("displayName");
			String groupDescription = groups.getAsObject(i).getString("description");
			// retrieve planner plans of given groupId
			// TODO: make this async for better performance
			JsonJavaArray plans = getPlannerPlansOfGroup(groupId);

			for (Object plan : plans) {
				// populate the group information into the plans
				((JsonJavaObject) plan).put("groupId", groupId);
				((JsonJavaObject) plan).put("groupDisplayName", groupName);
				((JsonJavaObject) plan).put("groupDescription", groupDescription);
				// create a compositeId that consists of plannerId + (channeld) < if a related channel is found later on
				((JsonJavaObject) plan).put("compositeId", ((JsonJavaObject) plan).getAsString("id"));
				result.add(plan);
			}
		}
		return result;
	}

	/**
	 * 
	 * @param groupId id of a group
	 * @return return the planner plans associated with a group
	 * @throws Exception
	 */
	private JsonJavaArray getPlannerPlansOfGroup(String groupId) throws Exception {
		return getCollectionPaged("/groups/" + groupId + "/planner/plans", null);
	}

	/**
	 * 
	 * @return returns all onenote notebooks found via /notebooks and in the sharepoint sites of private and shared channels
	 * @throws Exception
	 */
	public JsonJavaArray getAllNotebooks() throws Exception {
		JsonJavaArray groups = this.getM365Groups("&select=id,displayName,description");
		// get all sharepoint sites of the company that are not personal
		JsonJavaArray sites = getCollectionPaged("/sites/getAllSites?$filter=isPersonalSite%20eq%20false", null);
		// TODO: make this async for better performance
		JsonJavaArray result = new JsonJavaArray();
		for (int i = 0; i < groups.length(); i++) {
			String groupId = groups.getAsObject(i).getString("id");
			String groupName = groups.getAsObject(i).getString("displayName");
			String groupDescription = groups.getAsObject(i).getString("description");
			JsonJavaArray onenotes = getPublicMSOneNoteNotebooks(groupId);
			// retrieve the default site of the group
			JsonJavaObject rootSite = getSharePointRootSite(groupId);
			// grab the webUrl and add "-" in order to prevent accidental matches with similar group names
			String rootWebUrl = rootSite.getAsString("webUrl") + "-";
			// find the matching shared and private sites using the webUrl of the root/default site of the group
			// NOTE: unlucky naming of the another group could lead to wrong
			// filtering, however this is the only known way and can?t be restricted further
			List<Object> spSites = sites.stream().filter(x -> ((JsonJavaObject) x).getAsString("webUrl").startsWith(rootWebUrl)).collect(Collectors.toList());

			for (Object site : spSites) {
				JsonJavaArray books = getMSOneNoteNotebooksFromSite(((JsonJavaObject) site).getString("id"));
				onenotes.addAll(books);
			}

			for (Object note : onenotes) {
				((JsonJavaObject) note).put("groupId", groupId);
				((JsonJavaObject) note).put("groupDisplayName", groupName);
				((JsonJavaObject) note).put("groupDescription", groupDescription);
				// create a compositeId that consists of notebookId + (tabld) < if a related channel is found later on
				// we also add the groupId as the notebook can theoretically be found in different groups without having a tab context
				((JsonJavaObject) note).put("compositeId", ((JsonJavaObject) note).getAsString("id") + groupId);
				result.add(note);
			}
		}
		return result;
	}

	public JsonJavaArray getChannelTabs(String groupId, String channelId) throws Exception {
		return getCollectionPaged("/teams/" + groupId + "/channels/" + channelId + "/tabs?expand=teamsapp", null);
	}

	/**
	 * 
	 * @param groupId
	 * @return returns the onenote notebooks that are public e.g. created in a public channel
	 * @throws Exception
	 */
	private JsonJavaArray getPublicMSOneNoteNotebooks(String groupId) throws Exception {
		return getCollectionPaged("/groups/" + groupId + "/onenote/notebooks", null);
	}

	/**
	 * 
	 * @param siteId
	 * @return returns the onenote notebooks that are available in this site
	 * @throws Exception
	 */
	private JsonJavaArray getMSOneNoteNotebooksFromSite(String siteId) throws Exception {
		return getCollectionPaged("/sites/" + siteId + "/onenote/notebooks", null);
	}

	public JsonJavaArray getAllSharePointSites() throws Exception {
		// get all groups that could have a sharepoint site
		JsonJavaArray groups = this.getM365Groups("&select=id,displayName,description");
		// get all sharepoint sites of the company that are not personal
		JsonJavaArray sites = getCollectionPaged("/sites/getAllSites?$filter=isPersonalSite%20eq%20false", null);
		JsonJavaArray result = new JsonJavaArray();
		for (int i = 0; i < groups.length(); i++) {
			String groupId = groups.getAsObject(i).getString("id");
			String groupName = groups.getAsObject(i).getString("displayName");
			String groupDescription = groups.getAsObject(i).getString("description");
			// retrieve the default site of the group
			JsonJavaObject rootSite = getSharePointRootSite(groupId);
			// grab the webUrl and add "-" in order to prevent accidental
			// matches with similar group names
			String rootWebUrl = rootSite.getAsString("webUrl") + "-";
			// find the matching shared and private sites using the webUrl of
			// the root/default site of the group
			// NOTE: unlucky naming of the another group could lead to wrong
			// filtering, however this is the only known way and can?t be
			// restricted further
			List<Object> spSites = sites.stream().filter(x -> ((JsonJavaObject) x).getAsString("webUrl").startsWith(rootWebUrl)).collect(Collectors.toList());
			// add the root/default site to the list
			spSites.add(rootSite);
			// convert the list in an JsonJavaArray
			JsonJavaArray allSites = new JsonJavaArray();
			allSites.addAll(spSites);

			allSites.forEach(x -> {
				((JsonJavaObject) x).put("groupId", groupId);
				((JsonJavaObject) x).put("groupDisplayName", groupName);
				((JsonJavaObject) x).put("groupDescription", groupDescription);
				result.add(x);
			});
		}
		return result;
	}

	private JsonJavaObject getSharePointRootSite(String groupId) throws Exception {
		return getSingleObject("/groups/" + groupId + "/sites/root", null);
	}

	public JsonJavaArray getSharePointChannelFolders() throws Exception {
		JsonJavaArray result = new JsonJavaArray();
		JsonJavaArray teams = getAllTeams();
		for (Object team : teams) {
			String teamId = ((JsonJavaObject) team).getAsString("id");
			JsonJavaArray teamChannels = getTeamsChannels(teamId);
			for (Object channel : teamChannels) {
				if (((JsonJavaObject) channel).getAsString("displayName").equals("General"))
					((JsonJavaObject) channel).put("displayName", "Allgemein");
				String channelId = ((JsonJavaObject) channel).getAsString("id");
				JsonJavaObject channelFolder = getChannelFolder(teamId, channelId);
				channelFolder.put("relatedChannel", channel);
				channelFolder.put("relatedTeam", team);
				if (((JsonJavaObject) channelFolder).getAsString("name").equals("General"))
					((JsonJavaObject) channelFolder).put("name", "Allgemein");
				result.add(channelFolder);
			}
		}
		return result;
	}

	private JsonJavaObject getChannelFolder(String groupId, String channelId) throws Exception {
		return getSingleObject2("/teams/" + groupId + "/channels/" + channelId + "/filesFolder", null);
	}

	public JsonJavaArray getAllTeamsChannels() throws Exception {
		JsonJavaArray result = new JsonJavaArray();
		JsonJavaArray teams = getAllTeams();
		for (Object team : teams) {
			String teamId = ((JsonJavaObject) team).getAsString("id");
			JsonJavaArray teamChannels = getTeamsChannels(teamId);
			for (Object channel : teamChannels) {
				((JsonJavaObject) channel).put("relatedTeam", team);
				if (((JsonJavaObject) channel).getAsString("displayName").equals("General"))
					((JsonJavaObject) channel).put("displayName", "Allgemein");
				result.add(channel);
			}
		}
		return result;
	}

	private JsonJavaArray getTeamsChannels(String groupId) throws Exception {
		return getCollectionPaged("/teams/" + groupId + "/channels", null);
	}

	public JsonJavaArray getAllTeamsTabs() throws Exception {
		JsonJavaArray result = new JsonJavaArray();
		JsonJavaArray channels = getAllTeamsChannels();
		for (Object channel : channels) {
			String channelId = ((JsonJavaObject) channel).getAsString("id");
			JsonJavaObject relatedTeam = (JsonJavaObject) ((JsonJavaObject) channel).get("relatedTeam");
			// remove the relatedTeam from the channel as we add it later to the object itself
			((JsonJavaObject) channel).remove("relatedTeam");
			String teamId = relatedTeam.getAsString("id");

			JsonJavaArray tabs = getChannelTabs(teamId, channelId);
			for (Object tab : tabs) {
				((JsonJavaObject) tab).put("relatedTeam", relatedTeam);
				((JsonJavaObject) tab).put("relatedChannel", channel);
				result.add(tab);
			}
		}
		return result;
	}


	/**
	 * 
	 * @param teamId
	 * @return returns all channelTabs of a team
	 * @throws Exception
	 */
	public JsonJavaArray getChannelTabs(String teamId) throws Exception {
		JsonJavaArray result = new JsonJavaArray();
		JsonJavaObject relatedTeam = getTeamOfGroup(teamId);
		JsonJavaArray channels = getTeamsChannels(teamId);
		for (Object channel : channels) {
			String channelId = ((JsonJavaObject) channel).getAsString("id");
			JsonJavaArray tabs = getChannelTabs(teamId, channelId);
			for (Object tab : tabs) {
				((JsonJavaObject) tab).put("relatedTeam", relatedTeam);
				((JsonJavaObject) tab).put("relatedChannel", channel);
				result.add(tab);
			}
		}
		return result;
	}

	/**
	 * Returns a JsonJavaObject that represents a user with the given on-premises SAM account name. Queries the /users endpoint of the Microsoft
	 * Graph API with the specified filter and count parameters. If the query returns more than one result or no result, it returns null.
	 * 
	 * @param name the on-premises SAM account name of the user to be retrieved
	 * @return a JsonJavaObject that contains the user's properties, or null if no user is found or multiple users are found
	 * @throws Exception if the query fails or the result cannot be parsed
	 */
	public JsonJavaObject getUserFromSamAccountName(String name) throws Exception {

		// This is needed otherwise the request fails
		LinkedHashMap<String, Object> headers = new LinkedHashMap<>();
		headers.put("ConsistencyLevel", "eventual");

		String query = "/users?$filter=onPremisesSamAccountName+eq+'" + name + "'&$count=true";
		JsonJavaArray result = getCollectionPaged(query, null, headers);
		if (result.size() != 1) {
			return null;
		}
		return result.getAsObject(0);
	}

	/**
	 * Retrieves the members of a channel in a team.
	 *
	 * @param teamId the ID of the team
	 * @param channelId the ID of the channel
	 * @return a JsonJavaArray containing the members of the channel
	 * @throws Exception if an error occurs during the retrieval
	 */
	public JsonJavaArray getChannelMembers(String teamId, String channelId) throws Exception {
		return getCollectionPaged("/teams/" + teamId + "/channels/" + channelId + "/members", null);
	}

	/**
	 * Retrieves a collection of guest accounts from the Graph API.
	 *
	 * @return A JsonJavaArray containing the guest accounts.
	 * @throws Exception if an error occurs during the retrieval process.
	 */
	public JsonJavaArray getGuestAccounts() throws Exception {
		return getCollectionPaged("/users?$filter=userType+eq+'Guest'", null);
	}

	/**
	 * Retrieves the children of a drive item specified by the drive ID and item ID.
	 *
	 * @param driveId The ID of the drive.
	 * @param itemId The ID of the item.
	 * @return A JsonJavaArray containing the children of the drive item.
	 * @throws Exception if an error occurs during the retrieval.
	 */
	public JsonJavaArray getDriveItemChildren(String driveId, String itemId) throws Exception {
		return getCollectionPaged2("/drives/" + driveId + "/items/" + itemId + "/children", null);
	}

	/**
	 * Recursively crawls a drive folder and its subfolders.
	 *
	 * @param driveId The ID of the drive.
	 * @param itemId The ID of the folder.
	 * @throws Exception if an error occurs during the retrieval process.
	 */
	public JsonJavaArray crawlFolder(String driveId, String itemId, int maxDepth) throws Exception {
		return crawlFolder(driveId, itemId, new JsonJavaArray(), maxDepth);
	}

	private JsonJavaArray crawlFolder(String driveId, String itemId, JsonJavaArray result, int maxDepth) throws Exception {
		if (maxDepth <= 0) {
			return result;
		}
		JsonJavaArray children = getDriveItemChildren(driveId, itemId);
		for (int i = 0; i < children.length(); i++) {
			JsonJavaObject child = children.getAsObject(i);
			result.add(child);

			if (child.containsKey("folder")) {
				// This child is a folder, so we need to crawl its children too
				String childId = child.getAsString("id");
				crawlFolder(driveId, childId, result, maxDepth - 1);
			}
		}

		return result;
	}

	/**
	 * Retrieves the .eml item entries from a specific channel in a team.
	 * 
	 * @param teamId The ID of the team.
	 * @param channelId The ID of the channel.
	 * @param max The maximum number of email entries to retrieve.
	 * @return A JsonJavaArray containing the email entries.
	 * @throws Exception If an error occurs while retrieving the email entries.
	 */
	public JsonJavaArray getChannelMailEMLEntries(String teamId, String channelId, int max) throws Exception {
		JsonJavaObject filesFolder = getChannelFolder(teamId, channelId);
		String folderId = filesFolder.getAsString("id");
		String parentDriveId = filesFolder.getAsObject("parentReference").getAsString("driveId");

		JsonJavaArray folders = getDriveItemChildren(parentDriveId, folderId);

		// Filter out non-email folders (name starts with EmailMessages)
		List<JsonJavaObject> mailFolders =
				folders.stream().map(folder -> (JsonJavaObject) folder).filter(folder -> folder.getAsString("name").startsWith("EmailMessages")).collect(Collectors.toList());

		// Order mailFolders from newest to oldest (createdDateTime)
		mailFolders.sort((folder1, folder2) -> VCDEUtil.parseTeamsTimestamp(folder2.getAsString("createdDateTime")).compareTo(VCDEUtil.parseTeamsTimestamp(folder1.getAsString("createdDateTime"))));

		JsonJavaArray result = new JsonJavaArray();

		for (JsonJavaObject mailFolder : mailFolders) {
			String mailFolderId = mailFolder.getAsString("id");
			JsonJavaArray items = getDriveItemChildren(parentDriveId, mailFolderId);

			// Filter out everything that is not an eml file (name ends with .eml)
			List<JsonJavaObject> emlItems = items.stream().map(item -> (JsonJavaObject) item).filter(item -> item.getAsString("name").endsWith(".eml")).collect(Collectors.toList());

			// Order emlItems from newest to oldest (createdDateTime)
			emlItems.sort((item1, item2) -> VCDEUtil.parseTeamsTimestamp(item2.getAsString("createdDateTime")).compareTo(VCDEUtil.parseTeamsTimestamp(item1.getAsString("createdDateTime"))));

			// Only add as many items as specified by max
			if (emlItems.size() > max) {
				emlItems = emlItems.subList(0, max);
			}

			max -= emlItems.size();

			result.addAll(emlItems);

			if (max <= 0) {
				break;
			}
		}

		return result;
	}

	/**
	 * Retrieves the EML content for a specific drive item.
	 *
	 * @param driveId The ID of the drive.
	 * @param itemId The ID of the item.
	 * @return The EML content as an Object.
	 * @throws Exception if an error occurs during the retrieval process.
	 */
	public Object getEMLContent(String driveId, String itemId) throws Exception {

		String apiCommand = "/drives/" + driveId + "/items/" + itemId + "/content";

		prepareApi();

		if (authDisabled)
			throw new Exception(AUTH_DISABLED_MSG);

		// Remove request headers from the prev request
		api.clearRequestHeader();

		String url = TRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, new LinkedHashMap<>());

		return api.doGet(url);

	}

	public JsonJavaArray searchDriveItems(String driveId, String query) throws Exception {
		return searchDriveItems(driveId, query, null, null, -1);

	}

	public JsonJavaArray searchDriveItems(String driveId, String query, List<String> select, List<String> orderBy, int top) throws Exception {
		LinkedHashMap<String, Object> params = new LinkedHashMap<>();

		if (top > 0) {
			params.put("$top", top);
		}

		if (select != null && !select.isEmpty()) {
			params.put("$select", String.join(",", select));
		}

		if (orderBy != null && !orderBy.isEmpty()) {
			params.put("$orderby", String.join(",", orderBy));
		}

		return getCollectionPaged("/drives/" + driveId + "/root/search(q='" + query + "')", params, null, top);
	}

	public JsonJavaArray getChannelMailEMLItems(String teamId, String channelId, List<String> select, List<String> orderBy, int max) throws Exception {
		JsonJavaObject filesFolder = getChannelFolder(teamId, channelId);
		String parentDriveId = filesFolder.getAsObject("parentReference").getAsString("driveId");

		JsonJavaArray driveSearchResult = searchDriveItems(parentDriveId, ".eml", select, orderBy, max);
		System.out.println(driveSearchResult.size());

		// the result might contain files that are not eml files, so we filter them out
		JsonJavaArray emlItems =
				driveSearchResult.stream().map(item -> (JsonJavaObject) item).filter(item -> item.getAsString("name").endsWith(".eml")).collect(Collectors.toCollection(JsonJavaArray::new));

		System.out.println(emlItems.size());

		// add a new property to the emlItems (subject = name without _somenumber.eml)
		// e.g. "Test_1.eml" -> "Test"
		// subject can also be empty then we have somenumber.eml
		// e.g. "1.eml" -> ""
		emlItems.stream().map(item -> (JsonJavaObject) item).forEach(item -> {
			String name = item.getAsString("name");
			String subject = name.substring(0, name.lastIndexOf("."));

			if (subject.matches(".*_\\d+")) {
				subject = subject.substring(0, subject.lastIndexOf("_"));
			} else {
				subject = "Kein Betreff";
			}

			item.put("subject", subject);
		});

		return emlItems;
	}

	public JsonJavaArray getChannelMailEMLItems(String teamId, String channelId, int max) throws Exception {
		return getChannelMailEMLItems(teamId, channelId, null, null, max);
	}

	public JsonJavaObject getDriveItem(String driveId, String itemId) throws Exception {
		return getSingleObject("/drives/" + driveId + "/items/" + itemId, null);
	}

	public JsonJavaArray getDriveItemsWithPersonalPermissions(String teamId, String channelId) throws Exception {

		JsonJavaArray result = new JsonJavaArray();
		JsonJavaObject team = getTeamOfGroup(teamId);
		JsonJavaObject channel = getSingleObject("/teams/" + teamId + "/channels/" + channelId, null);

		JsonJavaObject filesFolder = getChannelFolder(teamId, channelId);

		String folderId = filesFolder.getAsString("id");
		String parentDriveId = filesFolder.getAsObject("parentReference").getAsString("driveId");

		JsonJavaArray driveItemsWithPermissions = getDriveItemsWithPersonalPermissions(teamId, parentDriveId, folderId);

		for (Object item : driveItemsWithPermissions) {
			JsonJavaObject itemObj = (JsonJavaObject) item;

			itemObj.put("relatedChannel", channel);
			itemObj.put("relatedTeam", team);

			String compositeId = itemObj.getAsString("id") + ":" + teamId + ":" + channelId;
			itemObj.put("compositeId", compositeId);

			result.add(itemObj);
		}

		return result;
	}


	private JsonJavaArray getDriveItemsWithPersonalPermissions(String teamId, String driveId, String itemId) throws Exception {
		return getDriveItemsWithPersonalPermissions(teamId, driveId, itemId, new HashSet<String>(), new JsonJavaArray());
	}

	private JsonJavaArray getDriveItemsWithPersonalPermissions(String teamId, String driveId, String itemId, Set<String> alreadyFound, JsonJavaArray result) throws Exception {

		// copy the set to prevent changes to the original set
		// only prevent listing inherited permissions for the subfolders
		Set<String> alreadyResolvedNew = new HashSet<>(alreadyFound);

		JsonJavaArray children = getDriveItemChildren(driveId, itemId);

		for (int i = 0; i < children.length(); i++) {
			JsonJavaObject child = children.getAsObject(i);

			JsonJavaArray permissions = getDriveItemPermissions(driveId, child.getAsString("id"));

			// remove grantTo, shareId and id from permissions
			permissions.forEach(permission -> {
				((JsonJavaObject) permission).remove("grantedTo");
				((JsonJavaObject) permission).remove("shareId");
				((JsonJavaObject) permission).remove("id");
			});

			/**
			 * remove permissions if: (1) grantedToV2 contains key siteGroup (contains owner, member, guest from the corresponding group) (2)
			 * grantedToV2 contains key group and group.id is equal to the teamId
			 */
			permissions = permissions.stream().filter(permission -> {

				String identityId = getDriveItemPermIdentityId((JsonJavaObject) permission);

				// if the identityId is already present in the parent folder, we don't need to list it again
				if (identityId != null) {
					if (alreadyResolvedNew.contains(identityId)) {
						return false;
					} else {
						alreadyResolvedNew.add(identityId);
					}
				}

				JsonJavaObject grantedToV2 = ((JsonJavaObject) permission).getAsObject("grantedToV2");

				if (grantedToV2 == null) {
					return false;
				}

				if (grantedToV2.containsKey("siteGroup")) {
					return false;
				}

				if (grantedToV2.containsKey("group")) {
					JsonJavaObject group = grantedToV2.getAsObject("group");
					return !group.getAsString("id").equals(teamId);
				}

				return true;
			}).collect(Collectors.toCollection(JsonJavaArray::new));

			// only add items that have special permissions
			if (!permissions.isEmpty()) {
				child.put("permissions", permissions);
				result.add(child);
			}

			if (child.containsKey("folder")) {
				// This child is a folder, so we need to crawl its children too
				String childId = child.getAsString("id");
				getDriveItemsWithPersonalPermissions(teamId, driveId, childId, alreadyResolvedNew, result);
			}
		}

		return result;
	}

	/**
	 * Retrieves the identity ID associated with the permission. Group Id or User Id is returned.
	 *
	 * @param permission The permission object.
	 * @return The identity ID as a String, or null if not found.
	 */
	private String getDriveItemPermIdentityId(JsonJavaObject permission) {
		JsonJavaObject grantedToV2 = permission.getAsObject("grantedToV2");

		if (grantedToV2 == null) {
			return null;
		}

		if (grantedToV2.containsKey("group")) {
			JsonJavaObject group = grantedToV2.getAsObject("group");
			return group.getAsString("id");
		}

		if (grantedToV2.containsKey("user")) {
			JsonJavaObject user = grantedToV2.getAsObject("user");
			return user.getAsString("id");
		}

		return null;
	}

	private JsonJavaArray getDriveItemPermissions(String parentDriveId, String itemId) throws Exception {
		return getCollectionPaged2("/drives/" + parentDriveId + "/items/" + itemId + "/permissions", null);
	}

}
