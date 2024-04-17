package com.voessing.vcde.endpoints.vrh.crawler;

import javax.servlet.http.HttpServletRequest;

import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

public class RunVCDEAdapter extends VrhHttpHandler {

	private static final String CREDSTORE_KEY = "trello_lm";

	@Override
	protected void onRequest(HttpServletRequest request) throws Exception {
		// validate that the request is from Trello
		System.out.println("hallooo :)))");
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {

		return ":)";
	}

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {

		return "skfjaskjfklsajfljasdf";
	}

}
