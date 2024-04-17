package com.voessing.vcde.endpoints.srs.handler;

import java.util.HashMap;
import java.util.LinkedHashMap;

import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.api.adapter.GraphAPI;
import com.voessing.utils.srs.config.SrsConfig;

public class HandlerHelper {

	public static String getAzurePrincipalFromQueryParams(HashMap<String, String> otherQueryParams, JsonJavaObject serviceConfig) {
		// define default parameter names
		String paramnameAzurePricipalId = SrsConfig.DEFAULT_QUERYPARAMNAME_$AZUEPRINCIPALID;
		String paramnameAzurePricipalName = SrsConfig.DEFAULT_QUERYPARAMNAME_$AZUEPRINCIPALNAME;

		// eventually map url parameter names from configuration
		JsonJavaObject queryParamMap;
		queryParamMap = serviceConfig == null
			? null
			: serviceConfig.getAsObject(SrsConfig.ATT_SERVICE_QUERYPARAMMAP);

		if (queryParamMap != null) {
			paramnameAzurePricipalId = (String) queryParamMap.getOrDefault(SrsConfig.ATT_SERVICE_QUERYPARAMMAP_KEY_$AZUEPRINCIPALID, paramnameAzurePricipalId);
			paramnameAzurePricipalName = (String) queryParamMap.getOrDefault(SrsConfig.ATT_SERVICE_QUERYPARAMMAP_KEY_$AZUEPRINCIPALNAME, paramnameAzurePricipalName);
		}

		String resultAzurePrincipal = "";

		// get azure principal from url parameters (preferably by id, otherwise by name (=upn))
		if (otherQueryParams != null && otherQueryParams.containsKey(paramnameAzurePricipalId)) {
			resultAzurePrincipal = otherQueryParams.get(paramnameAzurePricipalId);
		}

		if (resultAzurePrincipal.isEmpty() && otherQueryParams != null && otherQueryParams.containsKey(paramnameAzurePricipalName)) {
			resultAzurePrincipal = otherQueryParams.get(paramnameAzurePricipalName);
		}

		return resultAzurePrincipal;
	}

	public static JsonJavaArray getGroupsOfUserFromAAD(String azurePrincipal) throws Exception {
		// fake id to get test data TODO: remove
		// azurePrincipal = "Michael.Buchmann@voessing.de";

		// run graph request to get all groups of user
		String graphQuery = "/users/" + azurePrincipal + "/transitiveMemberOf";
		LinkedHashMap<String, Object> graphParams = new LinkedHashMap<>();
		graphParams.put("$select", "displayName");
		// currently not supported to the reference property query: graphParams.put("$filter", "startswith(displayName, 'P-')");

		GraphAPI graph = new GraphAPI();
		JsonJavaArray groups = graph.getCollectionPaged(graphQuery, graphParams);
		return groups;
	}
	
	public static boolean groupsContain(JsonJavaArray groups, String groupDisplayName) {
		boolean result = false;
		
		for (int i = 0; i < groups.length(); i++) {
			JsonJavaObject group = groups.getAsObject(i);
			String displayName = group.getString("displayName");

			if (displayName != null && displayName.equalsIgnoreCase(groupDisplayName)) {
				result = true;
				break;
			}
		}
	
		return result;
	}
}
