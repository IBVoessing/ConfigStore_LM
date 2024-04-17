package com.voessing.vcde.endpoints.vrh.resources;

import java.util.Map;

import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

public class CurrentUser extends VrhDominoNotesDocumentHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) {
		// endpoint config
		config.setAllowedMethods("GET, OPTIONS");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		config.setResourceless(true); // no db search
		config.withoutConcurrencyControl(); // resourceless endpoints cannot have concurrency control

		// resource config
		config.getAttributeConfigs().put("userabbreviatedname", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_USERABBREVIATEDNAME));
		config.getAttributeConfigs().put("usercanonicalname", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_USERCANONICALNAME));
		config.getAttributeConfigs().put("useremail", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_USEREMAIL));
		config.getAttributeConfigs().put("username", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_USERCOMMONNAME));
		config.getAttributeConfigs().put("userphotourl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_USERPHOTOURL));

		return config;
	}

}
