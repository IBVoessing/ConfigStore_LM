package com.voessing.vcde.tooladapter.handlers;

import org.openntf.domino.Document;

import com.ibm.commons.util.io.json.JsonJavaObject;


public class ReqBundle {

    public final String crudEntity, httpMethod;
    public final Document request, tool;
    public final JsonJavaObject body;
    
    public ReqBundle(String crudEntity, String httpMethod, Document request, Document tool, JsonJavaObject body) {
        this.crudEntity = crudEntity;
        this.httpMethod = httpMethod;
        this.request = request;
        this.tool = tool;
        this.body = body;
    }
}
