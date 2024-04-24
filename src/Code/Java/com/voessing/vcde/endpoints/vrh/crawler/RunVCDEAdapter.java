package com.voessing.vcde.endpoints.vrh.crawler;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.google.gson.Gson;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.common.TGlobalConfig;
import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.vcde.tooladapter.handlers.TeamsTeamHandler;
import com.voessing.vcde.tooladapter.handlers.TrelloHandler;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class RunVCDEAdapter extends VrhHttpHandler {

	Gson gson = new Gson();

	Database db; 

	Document requestDoc, toolDoc;

	String toolName;

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig, Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("POST, GET");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		return initialConfig;
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws Exception {
		Session session = Factory.getSession(SessionType.NATIVE);
		//db = session.getDatabase(session.getServerName(), TGlobalConfig.getSystemVar(TGlobalConfig.VCDE_CFG_DB));
		db = session.getDatabase("CN=IBVDNO03/O=IBV/C=DE", TGlobalConfig.getSystemVar(TGlobalConfig.VCDE_CFG_DB));
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {
		return "";
	}

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {
		//trigger capturedPayloadRaw to be available
		getRequestPayload(request);
		JsonJavaObject body = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, capturedPayloadRaw);
		loadDocuments(body);

		String adapter = toolDoc.getItemValueString("provisioningType");
		
		if(adapter.isEmpty()) {
			adapter = "admin";
		}

		// if adapter is set to api, use the toolName as adapter
		if(adapter.equals("api")){
			adapter = toolName;
		}

		System.out.println("DEBUG => Adapter: " + adapter);
		
		switch (adapter) {
			case "admin":
				return new TrelloHandler().excecute(requestDoc, toolDoc, body).toString();
			case "MST-TEAM":
				return new TeamsTeamHandler().excecute(requestDoc, toolDoc, body).toString();		
			default:
				throw new VrhException(404,"Adapter not set in Tool Document!");
		}
	}

	private void loadDocuments(JsonJavaObject body) throws NotesException{
		String requestUNID = body.getString("id");
		String toolUNID = body.getString("ToolUNID");

		requestDoc = db.getDocumentByUNID(requestUNID);
		toolDoc = db.getDocumentByUNID(toolUNID);

		// set toolName to identify the right provisioning
		toolName = toolDoc.getItemValueString("name");
	}
}
