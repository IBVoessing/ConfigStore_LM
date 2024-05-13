package com.voessing.vcde.tooladapter.handlers;

import org.openntf.domino.Document;
import com.google.gson.JsonObject;

public class ReqBundle {

    public final String crudEntity, httpMethod;
    public final Document request, tool;
    public final JsonObject body;
    
    public ReqBundle(String crudEntity, String httpMethod, Document request, Document tool, JsonObject body) {
        this.crudEntity = crudEntity;
        this.httpMethod = httpMethod;
        this.request = request;
        this.tool = tool;
        this.body = body;
    }
}
