package com.voessing.vcde.endpoints.srs.handler;

import java.util.HashMap;

import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.utils.srs.handler.SrsProviderHandler;
import com.voessing.utils.srs.response.SrsResponseObject;

/**
 * Handler for resource endpoint "configurations".
 * 
 * @author markuspeters
 *
 */
public class SrsConfigurationsHandler extends SrsProviderHandler {
	private JsonJavaArray groupsOfUserFromAAD;

	public SrsConfigurationsHandler(JsonJavaObject attributesConfig) {
		super(attributesConfig);
	}

	@Override
	public void checkAllowanceForResponseObject(SrsResponseObject responseObject) throws Exception {
		if (groupsOfUserFromAAD != null) {
			/*-
			throw new SrsException403Forbidden("hat groups");
			*/
			// TODO: get ProjektNr from response object and try to find it in the groups
		}
	}

	@Override
	public void onBeforeProcessResource(HashMap<String, String> otherQueryParams, JsonJavaObject serviceConfig) throws Exception {
		String azurePrincipal = HandlerHelper.getAzurePrincipalFromQueryParams(otherQueryParams, serviceConfig);
		this.groupsOfUserFromAAD = HandlerHelper.getGroupsOfUserFromAAD(azurePrincipal);
	}

}
