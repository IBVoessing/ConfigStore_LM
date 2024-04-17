package com.voessing.vcde.endpoints.vrh.resources;

import java.util.Map;

import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoProfileDocumentHandler;

public class Configuration extends VrhDominoProfileDocumentHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) throws VrhException {
		// ### endpoint config
		config.setAllowedMethods("GET, OPTIONS");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);

		// ### resource config
		// config.setDatabaseName("VCDE-Config.nsf");
		// config.setDatabaseServerName("ibvdno03");
		config.setFormName("Konfiguration");

		// ### attribute config

		// payload
		config.getAttributeConfigs().put("roles", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue().asItemExpected());
		config.getAttributeConfigs().put("useCases", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());

		return config;
	}

}
