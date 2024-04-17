package com.voessing.vcde.endpoints.srs.handler;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.utils.srs.handler.SrsNsfSearchProviderHandler;

public class SrsMyProjectsHandler extends SrsNsfSearchProviderHandler {

	public SrsMyProjectsHandler(JsonJavaObject attributesConfig) {
		super(attributesConfig);
	}

	private String getProjectConstraintsForUser(String azurePrincipal) throws Exception {
		StringBuilder result = new StringBuilder();

		/*-
		// run graph request to get all groups of user
		String graphQuery = "/users/" + azurePrincipal + "/transitiveMemberOf";
		LinkedHashMap<String, Object> graphParams = new LinkedHashMap<>();
		graphParams.put("$select", "displayName");
		// currently not supported to the reference property query: graphParams.put("$filter", "startswith(displayName, 'P-')");
		
		TMSGraphAPI graph = new TMSGraphAPI();
		JsonJavaArray groups = graph.getCollectionPaged(graphQuery, graphParams);
		*/

		JsonJavaArray groupsOfUser = HandlerHelper.getGroupsOfUserFromAAD(azurePrincipal);

		// loop groups to find membership in privileged groups (i.e. developers)
		boolean isDeveloper = HandlerHelper.groupsContain(groupsOfUser, "developers");
		
		/*-
		boolean skipConstraints = false;
		for (int i = 0; i < groups.length(); i++) {
			JsonJavaObject group = groups.getAsObject(i);
			String displayName = group.getString("displayName");

			if (displayName != null && displayName.equalsIgnoreCase("developers")) {
				skipConstraints = true;
				break;
			}
		}
		*/

		if (!isDeveloper) {
			final String regexCompany = "(VI|VV|VP|VQ|VC)"; // certain char sequences
			final String regexProjektNr = "([0-9]{4})"; // 4 digits
			final String regexRole = "(EDITOR|READER)";
			// final String regexRole = "(EDITOR)";

			// get groups, whose displayName matches the project group name pattern and build the query constraint
			Pattern patternCompany = Pattern.compile(regexCompany);
			Pattern patternProjektNr = Pattern.compile(regexProjektNr);

			int numberOfGroups = 0;
			for (int i = 0; i < groupsOfUser.length(); i++) {
				JsonJavaObject group = groupsOfUser.getAsObject(i);
				String displayName = group.getString("displayName");

				if (displayName != null && displayName.matches("^P-" + regexCompany + "-" + regexProjektNr + "-" + regexRole + "$")) {
					Matcher matcherCompany = patternCompany.matcher(displayName);
					Matcher matcherProjektNr = patternProjektNr.matcher(displayName);

					String company = matcherCompany.find() ? matcherCompany.group(1) : "";
					String projektNr = matcherProjektNr.find() ? matcherProjektNr.group(1) : "";

					if (result.length() > 0) {
						result.append(" | ");
					}

					result.append("(FirmaKreis=\"" + company + "\" & ProjektNr=\"" + projektNr + "\")");
					numberOfGroups++;
				}
			}

			// enclose constraint in brackets
			if (result.length() > 0) {
				result.insert(0, "(").append(")");
			}

			// add count check to avoid full list when no groups exist
			if (result.length() > 0) {
				result.append(" & ");
			}

			result.append("(" + String.valueOf(numberOfGroups) + " > 0)");
		}

		// enclose constraint in brackets
		if (result.length() > 0) {
			result.insert(0, "(").append(")");
		}

		return result.toString();
	}

	@Override
	public String onGetNsfQuery(String nsfQuery, HashMap<String, String> otherQueryParams, JsonJavaObject serviceConfig) throws Exception {
		/*-
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
		
		String azurePrincipal = "";
		
		// get azure principal from url parameters (preferably by id, otherwise by name (=upn))
		if (otherQueryParams != null && otherQueryParams.containsKey(paramnameAzurePricipalId)) {
			azurePrincipal = otherQueryParams.get(paramnameAzurePricipalId);
		}
		
		if (azurePrincipal.isEmpty() && otherQueryParams != null && otherQueryParams.containsKey(paramnameAzurePricipalName)) {
			azurePrincipal = otherQueryParams.get(paramnameAzurePricipalName);
		}
		*/

		String azurePrincipal = HandlerHelper.getAzurePrincipalFromQueryParams(otherQueryParams, serviceConfig);

		// get project constraints for current azure user based on group membership derived from graph api (current azure user is contained in param $azureprincipalid or $azureprincipalname)
		String projectConstraints = getProjectConstraintsForUser(azurePrincipal);

		if (!projectConstraints.isEmpty()) {
			if (nsfQuery.isEmpty()) {
				nsfQuery = projectConstraints;
			} else {
				nsfQuery = "(" + nsfQuery + ") & " + projectConstraints;
			}
		}

		return nsfQuery;
	}

}
