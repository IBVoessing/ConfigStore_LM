package com.voessing.vcde.endpoints.vrh.crawler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.vcde.tooladapter.handlers.TrelloHandler;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

public class RunVCDEAdapter extends VrhHttpHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("POST");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		return initialConfig;
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws Exception {
		System.out.println("hallooo :)))");
	}

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {
		//trigger capturedPayloadRaw to be available
		getRequestPayload(request);
		
		String body = capturedPayloadRaw;
		
		TrelloHandler trelloHandler = new TrelloHandler();

		JsonJavaObject card = new JsonJavaObject();
		card.put("name", "Test Card");
		card.put("desc", body);
		card.put("start", dateToIsoString(new Date()));
		card.put("due", dateToIsoString(addWeekToDate(new Date())));
		
		JsonJavaObject task = new JsonJavaObject();
		task.put("card", card);

		trelloHandler.createAdminTask(task, "0063", false);
		
		return new JsonJavaObject().toString();
	}

	public String dateToIsoString(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		return sdf.format(date);
	}

	public Date addWeekToDate(Date date) {
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		c.add(Calendar.WEEK_OF_YEAR, 1);
		return c.getTime();
	}

}
