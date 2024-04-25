package com.voessing.vcde.tooladapter.interfaces;

import com.ibm.commons.util.io.json.JsonJavaObject;

import lotus.domino.Document;

public interface ExecutableAdapter {
	
	JsonJavaObject excecute(String crudEntity, String httpMethod, Document request, Document tool, JsonJavaObject body) throws Exception;

}
