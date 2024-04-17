package com.voessing.api.adapter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.common.TDateTimeUtil;
import com.voessing.common.TJsonRestConsumerEx;
import com.voessing.common.TVAppCredStore;

//Generelle MS Graph API
public class GraphAPI {

	
	private static final String AUTH_DISABLED_MSG = "Auth is disabled in this session due to unrecoverable auth errors";
	private static final String NEXT_PAGE_URL_FIELD = "@odata.nextLink";
	private static final String API_VER = "1.3";
	private static final int API_LOGLEVEL = 1;

	private static final String CREDSTORE_KEY = "ms-graph-voessing-2";
	private static final String MS_API_URL = "https://graph.microsoft.com";
	private static final int RETRY_LIMIT = 100;
	
	private String MS_API_VERSION;
	
	private TJsonRestConsumerEx api = null; 
	
	private String apiToken = "";
	private LocalDateTime apiTokenExpires = null;
	private boolean authRetried = false;
	private boolean authDisabled = false;
	private int retryTime = 1; // in s
	private int retryCount = 0;
	
	public static enum API_VERSION{
		Stable("v1.0"), Beta("beta");
		public final String apiVersion;
		private API_VERSION(String apiVersion){
			this.apiVersion = apiVersion;
		}
	}

	public GraphAPI(){
		setApiVersion(API_VERSION.Beta);
	}
	
	public GraphAPI(API_VERSION version){
		setApiVersion(version);
	}
	
	public void setApiVersion(API_VERSION version){
		MS_API_VERSION = version.apiVersion;
	}
		
	private JsonJavaObject getAuthObject() throws Exception {
		
		TJsonRestConsumerEx api = new TJsonRestConsumerEx("");
		api.setLogLevel(API_LOGLEVEL);
		api.setPostType(TJsonRestConsumerEx.PostType.X_WWW_FORM_URLENCODED);
		
		//load variables from App-CredStore
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
		
		if (authDisabled) return;
		
		if (resetAuth) {
			//only accept 1 retry for a token
			authRetried = true;
			apiToken = "";
			apiTokenExpires = null;
		}
		
		if (apiTokenExpires!=null && apiTokenExpires.isBefore(TDateTimeUtil.convertToLocalDateTime(new Date()))) {
			//if the cached token is expired, reset all token information
			authRetried = false;
			apiToken = "";
			apiTokenExpires = null;
		}
		
		//if no token is available, retrieve a token
		if (apiToken.isEmpty()) {
			
			JsonJavaObject json = getAuthObject();
			
			if (json!=null && json.containsKey("access_token")) {
				
				apiToken = json.getString("access_token");
				
				//set expires info: 60 seconds less
				apiTokenExpires = TDateTimeUtil.convertToLocalDateTime(new Date());
				apiTokenExpires = apiTokenExpires.plusSeconds(json.getLong("expires_in") - 60);
				
				System.out.println("APITOKEN LEN: " + apiToken.length());
				System.out.println("APITOKEN EXP: " + apiTokenExpires.toString());
				
				api.setBearerAuth(apiToken);
				
			} else authDisabled = true;
			
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
	
	
	public JsonJavaObject post(String apiCommand, JsonJavaObject postObj) throws Exception{
		return post(apiCommand, postObj, null);
	}
	
	public JsonJavaObject post(String apiCommand, JsonJavaObject postObj, LinkedHashMap<String,Object> headers) throws Exception{
		prepareApi();
		if (authDisabled) throw new Exception(AUTH_DISABLED_MSG);
		
		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);
		
		String url = TJsonRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand);
		JsonJavaObject resp = (JsonJavaObject) api.doPost(url, postObj);
		
		if(api.getLastResponseCode() == 201){
			retryCount = 0;
			return resp;
		}else if (api.getLastResponseCode() == 401 && !authRetried) {
			/// 1x Re-Auth erlauben
			authorize(true);
			return post(apiCommand, postObj);
		}else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT) raiseError(null, resp);
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			waitRetry(retryTime);
			return post(apiCommand, postObj);
		}else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			raiseError(null, resp);
		}
		return null;
	}
	
	public JsonJavaObject delete(String apiCommand) throws Exception{
		return delete(apiCommand, null);
	}
	
	public JsonJavaObject delete(String apiCommand, LinkedHashMap<String, Object> headers) throws Exception{
		prepareApi();
		if (authDisabled) throw new Exception(AUTH_DISABLED_MSG);
		
		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);
		
		String url = TJsonRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand);
		//base case is 204 => no content => empty Object() is returned
		Object resp = api.doDelete(url);
		if(api.getLastResponseCode() == 204){
			retryCount = 0;
			return new JsonJavaObject();
		}else if (api.getLastResponseCode() == 401 && !authRetried) {
			/// 1x Re-Auth erlauben
			authorize(true);
			return delete(apiCommand);
		}else if (api.getLastResponseCode() == 429) {
			// handle Bandwidth Limit Exceeded
			if (retryCount >= RETRY_LIMIT) raiseError(null, (JsonJavaObject) resp);
			System.out.println("MS Graph Bandwidth Exceeded! Retrying in: " + retryTime + "ms... Retries so far: " + retryCount);
			retryCount++;
			waitRetry(retryTime);
			return delete(apiCommand);
		}else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			raiseError(null,(JsonJavaObject) resp);
		}
		return null;
	}
	
	public JsonJavaObject getSingleObject(String apiCommand, LinkedHashMap<String, Object> params) throws Exception {
		return getSingleObject(apiCommand, params, null);
	}

	// Core-Routine zum Abgriff eines einzelnen Response-JSON-Objects
	public JsonJavaObject getSingleObject(String apiCommand, LinkedHashMap<String, Object> params, LinkedHashMap<String, Object> headers) throws Exception {

		prepareApi();

		if (authDisabled) throw new Exception(AUTH_DISABLED_MSG);
		
		// Remove request headers from the prev request
		api.clearRequestHeader();
		// Add new request headers
		addRequestHeaders(headers);

		String url = TJsonRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);
		
		JsonJavaObject json = (JsonJavaObject) api.doGet(url);
	
		//2023-10-06, lma/dko: handle Bandwidth Limit Exceeded
		//-> 5 Sekunden warten, Request 1x neu ausf?hren und weiter. Kann sp?ter mal ausgebaut werden f?r bessere Retries

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
			if (retryCount >= RETRY_LIMIT) raiseError(null, json);
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
			//handle sporadic errors of graph 
			if(retryCount >= RETRY_LIMIT) raiseError(null, json);
			retryCount++;		
			return getSingleObject(apiCommand, params);
		} else {
			System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + url);
			raiseError(null, json);
		}

		return null;
		
	}
	
	
	public JsonJavaArray getCollectionPaged(String apiCommand, LinkedHashMap<String,Object> params) throws Exception {
		return getCollectionPaged(apiCommand, params, null);
	}
	
	//Core-Routine zum Verarbeiten der Paged Response
	public JsonJavaArray getCollectionPaged(String apiCommand, LinkedHashMap<String,Object> params, LinkedHashMap<String, Object> headers) throws Exception {

		prepareApi();

		if (authDisabled) throw new Exception(AUTH_DISABLED_MSG);
		
		//Remove request headers from the prev request
		api.clearRequestHeader();
		//Add new request headers
		addRequestHeaders(headers);
		
		int page = 0;
		
		JsonJavaArray retArr = new JsonJavaArray();

		String nextPageUrl = TJsonRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);
		
		while (!nextPageUrl.isEmpty()) {
			
			//query next page, reset hasMore
			page++;
			JsonJavaObject json = (JsonJavaObject) api.doGet(nextPageUrl);

			//2023-10-06, lma/dko: handle Bandwidth Limit Exceeded
			if (api.getLastResponseCode() == 429) {
				if (retryCount >= RETRY_LIMIT) raiseError(null, json);
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
				//handle sporadic errors of graph 
				if(retryCount >= RETRY_LIMIT) raiseError(null, json);// fix
				retryCount++;												
				page--;
				continue;
			}
			
			// reset Url
			nextPageUrl = "";
			
			if (api.getLastResponseCode()==200) {
			
				// reset Retry values
				retryCount = 0;
				// retryTime = 1;
				
				retArr.addAll(json.getAsArray("value"));
				
				//decide if we need to query another page
				if (json.containsKey(NEXT_PAGE_URL_FIELD)) {
					nextPageUrl = json.getString(NEXT_PAGE_URL_FIELD);
				} 
				
			} else if (api.getLastResponseCode()==401 && !authRetried) {
				
				///1x Re-Auth erlauben
				authorize(true);
				
				//restart the whole request from page 1
				page = 0;
				retArr = new JsonJavaArray();
				nextPageUrl = TJsonRestConsumerEx.buildXAgentURL(MS_API_URL, MS_API_VERSION, apiCommand, params);
				
			} else if (api.getLastResponseCode() == 404) {
				return retArr;
			} else {
				System.out.println("Statuscode: " + api.getLastResponseCode() + " from URL: " + nextPageUrl);
				raiseError(page, json);
			}
			
		} //while nextPageUrl
		
		//System.out.println("PROCESSED PAGES: " + page);
				
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
		
		//compile error message string, use details if available
		StringBuilder errorMsg = new StringBuilder();
		errorMsg.append(api.getLastResponseCode());
		errorMsg.append(": "); 
		errorMsg.append(api.getLastResponseMessage());
		errorMsg.append(" (");
		
		List<String> info = new ArrayList<>();
		
		if (page!=null) info.add("at page " + page);
		
		if (json!=null) System.out.println("JSON RESP: " + json.toString());
		
		if (json!=null && json.containsKey("error")) {
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

			api = new TJsonRestConsumerEx("");
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
	public JsonJavaArray getM365Groups(String additionalUrlParameters) throws Exception{
		if(additionalUrlParameters == null){
			additionalUrlParameters  = "";
		}
		return this.getCollectionPaged("/groups?$filter=groupTypes/any(c:c+eq+'Unified')"+ additionalUrlParameters, null);
	}
	
	public JsonJavaArray getMailDistributionGroups() throws Exception{
		JsonJavaArray groups = this.getCollectionPaged("/groups?$filter=mailEnabled%20eq%20true", null);
		List<Object> mailGroups = groups.stream().filter(group -> ((JsonJavaObject)group).getAsList("groupTypes").isEmpty()).collect(Collectors.toList());
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
				//create a compositeId that consists of plannerId + (channeld) < if a related channel is found later on
				((JsonJavaObject) plan).put("compositeId", ((JsonJavaObject)plan).getAsString("id"));
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
			List<Object> spSites = sites.stream()
					.filter(x -> ((JsonJavaObject) x).getAsString("webUrl").startsWith(rootWebUrl))
					.collect(Collectors.toList());
			
			for(Object site: spSites){
				JsonJavaArray books = getMSOneNoteNotebooksFromSite(((JsonJavaObject) site).getString("id"));
				onenotes.addAll(books);
			}
			
			for (Object note : onenotes) {
				((JsonJavaObject) note).put("groupId", groupId);
				((JsonJavaObject) note).put("groupDisplayName", groupName);
				((JsonJavaObject) note).put("groupDescription", groupDescription);
				//create a compositeId that consists of notebookId + (tabld) < if a related channel is found later on
				//we also add the groupId as the notebook can theoretically be found in different groups without having a tab context
				((JsonJavaObject) note).put("compositeId", ((JsonJavaObject)note).getAsString("id") + groupId);
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
			List<Object> spSites = sites.stream()
					.filter(x -> ((JsonJavaObject) x).getAsString("webUrl").startsWith(rootWebUrl))
					.collect(Collectors.toList());
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
		for(Object team : teams){
			String teamId = ((JsonJavaObject)team).getAsString("id");
			JsonJavaArray teamChannels = getTeamsChannels(teamId);
			for(Object channel: teamChannels){
				if (((JsonJavaObject)channel).getAsString("displayName").equals("General")) 
					((JsonJavaObject)channel).put("displayName", "Allgemein");
				String channelId = ((JsonJavaObject) channel).getAsString("id");
				JsonJavaObject channelFolder = getChannelFolder(teamId, channelId);
				channelFolder.put("relatedChannel", channel);
				channelFolder.put("relatedTeam", team);
				if (((JsonJavaObject)channelFolder).getAsString("name").equals("General")) 
					((JsonJavaObject)channelFolder).put("name", "Allgemein");
				result.add(channelFolder);
			}
		}
		return result;
	}
	
	private JsonJavaObject getChannelFolder(String groupId, String channelId) throws Exception {
		return getSingleObject("/teams/" + groupId + "/channels/" + channelId + "/filesFolder", null);
	}
	
	public JsonJavaArray getAllTeamsChannels() throws Exception {
		JsonJavaArray result = new JsonJavaArray();
		JsonJavaArray teams = getAllTeams();
		for(Object team : teams){
			String teamId = ((JsonJavaObject)team).getAsString("id");
			JsonJavaArray teamChannels = getTeamsChannels(teamId);
			for(Object channel: teamChannels){
				((JsonJavaObject)channel).put("relatedTeam", team);
				if (((JsonJavaObject)channel).getAsString("displayName").equals("General")) 
					((JsonJavaObject)channel).put("displayName", "Allgemein");
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
		for(Object channel: channels){
			String channelId = ((JsonJavaObject)channel).getAsString("id");
			JsonJavaObject relatedTeam = (JsonJavaObject) ((JsonJavaObject)channel).get("relatedTeam");
			//remove the relatedTeam from the channel as we add it later to the object itself
			((JsonJavaObject)channel).remove("relatedTeam");
			String teamId = relatedTeam.getAsString("id");
			
			JsonJavaArray tabs = getChannelTabs(teamId, channelId);
			for(Object tab: tabs){
				((JsonJavaObject)tab).put("relatedTeam", relatedTeam);	
				((JsonJavaObject)tab).put("relatedChannel", channel);	
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
		for(Object channel: channels) {
			String channelId = ((JsonJavaObject)channel).getAsString("id");
			JsonJavaArray tabs = getChannelTabs(teamId, channelId);
			for(Object tab: tabs){
				((JsonJavaObject)tab).put("relatedTeam", relatedTeam);	
				((JsonJavaObject)tab).put("relatedChannel", channel);	
				result.add(tab);
			}
		}
		return result;
	}
	
	/**
	 * Returns a JsonJavaObject that represents a user with the given on-premises SAM account name.
	 * Queries the /users endpoint of the Microsoft Graph API with the specified filter and count parameters.
	 * If the query returns more than one result or no result, it returns null.
	 * @param name the on-premises SAM account name of the user to be retrieved
	 * @return a JsonJavaObject that contains the user's properties, or null if no user is found or multiple users are found
	 * @throws Exception if the query fails or the result cannot be parsed
	 */
	public JsonJavaObject getUserFromSamAccountName(String name) throws Exception{
		
		//This is needed otherwise the request fails
		LinkedHashMap<String, Object> headers = new LinkedHashMap<>();
		headers.put("ConsistencyLevel", "eventual");

		String query = "/users?$filter=onPremisesSamAccountName+eq+'"+name+"'&$count=true";
		JsonJavaArray result = getCollectionPaged(query, null, headers);
		if(result.size() != 1){
			return null;
		}
		return result.getAsObject(0);
	}
	
	/**
	 * Retrieves the members of a channel in a team.
	 *
	 * @param teamId    the ID of the team
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
		return getCollectionPaged("/drives/" + driveId + "/items/" + itemId + "/children", null);
	}

	/**
	 * Recursively crawls a drive folder and its subfolders.
	 *
	 * @param driveId The ID of the drive.
	 * @param itemId The ID of the folder.
	 * @throws Exception if an error occurs during the retrieval process.
	 */
	public JsonJavaArray crawlFolder(String driveId, String itemId, int maxDepth) throws Exception {
		return crawlFolder(driveId, itemId, "", new JsonJavaArray(), maxDepth);
	}

	private JsonJavaArray crawlFolder(String driveId, String itemId, String resolvePath, JsonJavaArray result, int maxDepth) throws Exception {
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
				crawlFolder(driveId, childId, resolvePath, result, maxDepth - 1);
			}
		}

		return result;
	}
   
}
