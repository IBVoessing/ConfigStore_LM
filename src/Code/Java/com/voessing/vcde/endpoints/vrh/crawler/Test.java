package com.voessing.vcde.endpoints.vrh.crawler;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

public class Test extends VrhHttpHandler {

	
	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("GET, POST, HEAD");
		return initialConfig;
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {
		
		if(request.getMethod().equalsIgnoreCase("HEAD")){
			return "yeeeey";
		}
		
		System.out.println("Hallo Test");
		String blah = "{'msg':'hallu'}";
		if(!blah.isEmpty()){
			throw new VrhException(400, "hallo");
		}
		Gson gson = new Gson();
		JsonObject jo = new JsonObject();
		jo.addProperty("test", "test");

		return jo.toString();
	}
	
	@Override
	protected String doPost(HttpServletRequest request) throws Exception {
		
		return "skfjaskjfklsajfljasdf";
	}

	
}
