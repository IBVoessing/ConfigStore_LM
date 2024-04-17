package com.voessing.vcde.endpoints.vrh.crawler;

import java.io.IOException;

import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.router.VrhSimpleRouter;

public class CrawlerReqHandler {
	public static void handleRequest() throws VrhException, IOException {
		new VrhSimpleRouter<>()
			.addRoute("/test", Test.class)
			.addRoute("/trello/webhook", TrelloWebhook.class)
			.addRoute("/runVCDEAdapter", RunVCDEAdapter.class)
			.handleRequest();
	}
}
