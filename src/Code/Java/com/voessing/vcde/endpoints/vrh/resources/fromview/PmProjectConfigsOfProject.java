package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Map;

import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

public class PmProjectConfigsOfProject extends VrhDominoNotesDocumentHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) throws VrhException {
		// ### endpoint config
		config.setAllowedMethods("GET, OPTIONS");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		config.withoutConcurrencyControl();

		// ### resource config
		config.setFormName("ProjectConfig");

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmProjectConfigsOfProject") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValue(parameterMap, "ProjectRID")) // add project rid (not nullable params, as empty param must lead to empty response instead of all items)
			.addKey(getRequestParameterValue(parameterMap, "ProjectUNID")) // add project unid (not nullable params, as empty param must lead to empty response instead of all items)
		);

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));
		config.getAttributeConfigs().put("httpUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_HTTPURL));

		// 02. payload
		config.getAttributeConfigs().put("roles", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());
		config.getAttributeConfigs().put("scopes", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());
		config.getAttributeConfigs().put("ProjectRID", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("ProjectUNID", VrhAttributeConfigFactory.createAttributeConfig());

		return config;

	}

}
