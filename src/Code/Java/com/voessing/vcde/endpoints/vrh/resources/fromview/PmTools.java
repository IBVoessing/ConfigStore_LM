package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Map;
import java.util.Optional;

import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

public class PmTools extends VrhDominoNotesDocumentHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) throws VrhException {
		// ### endpoint config
		config.setAllowedMethods("GET, OPTIONS");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		config.withoutConcurrencyControl();

		// ### resource config
		// config.setDatabaseName(VCDEShared.PMTOOLS_DATABASENAME);
		// config.setDatabaseServerName(VCDEShared.PMTOOLS_SERVERNAME);
		config.setFormName("Tool");

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmTools") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValue(parameterMap, "ProjectRID")) // add project rid (not nullable params, as empty param must lead to empty response instead of all items)
			.addKey(getRequestParameterValue(parameterMap, "ProjectUNID")) // add project unid (not nullable params, as empty param must lead to empty response instead of all items)
		);

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all

		// 02. payload
		config.getAttributeConfigs().put("deploymentType", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("name", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("omitToolInstanceUrl", VrhAttributeConfigFactory.createAttributeConfig(Optional.of(0)));
		config.getAttributeConfigs().put("title", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("type", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("usageTypes", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());

		// API configuration, can only be set in Notes Client, so make them all asIgnoreOnWrite()
		config.getAttributeConfigs().put("apiMemberHandling", VrhAttributeConfigFactory.createAttributeConfig(Optional.of(1)).asMultiValue().withStructuredContent().asIgnoreOnWrite());
		config.getAttributeConfigs().put("apiMemberScheme", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue().withStructuredContent().asIgnoreOnWrite()); // expecting a JSON according to the columns definition of a Webix ui.datatable (see https://docs.webix.com/datatable__columns_configuration.html)

		return config;
	}

}
