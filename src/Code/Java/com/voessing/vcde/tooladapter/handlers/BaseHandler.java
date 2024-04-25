package com.voessing.vcde.tooladapter.handlers;

import com.ibm.commons.util.io.json.JsonJavaObject;

import lotus.domino.Document;

public abstract class BaseHandler {
    protected final String crudEntity, httpMethod;
    protected final Document request, tool;
    protected final JsonJavaObject body;

    public BaseHandler(String crudEntity, String httpMethod, Document request, Document tool, JsonJavaObject body) {
        this.crudEntity = crudEntity;
        this.httpMethod = httpMethod;
        this.request = request;
        this.tool = tool;
        this.body = body;
    }
    
    public abstract JsonJavaObject excecute() throws Exception;
}

