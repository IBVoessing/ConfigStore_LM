package com.voessing.vcde.tooladapter.handlers;

import com.ibm.commons.util.io.json.JsonJavaObject;

public abstract class BaseHandler {
    protected final ReqBundle reqBundle;

    public BaseHandler(ReqBundle reqBundle) {
        this.reqBundle = reqBundle;
    }
    
    public abstract JsonJavaObject excecute() throws Exception;
}

