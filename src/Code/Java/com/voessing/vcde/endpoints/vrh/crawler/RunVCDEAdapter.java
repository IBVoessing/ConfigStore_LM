package com.voessing.vcde.endpoints.vrh.crawler;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

public class RunVCDEAdapter extends VrhHttpHandler {

	private static final String CREDSTORE_KEY = "trello_lm";

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("GET, POST");
		return initialConfig;
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws Exception {
		System.out.println("hallooo :)))");
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {

		return ":)";
	}

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {

		return ":(";
	}

}
