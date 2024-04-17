package com.voessing.vcde.test;

import com.voessing.common.TRestConsumerEx;
import com.voessing.common.TVAppCredStore2;

import lotus.domino.NotesException;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

public class VCDEHttpCaller {

    public TRestConsumerEx api;

    private static final String CREDSTORE_KEY = "server-agent-web";

    public VCDEHttpCaller() throws NotesException {
        // init api
        api = new TRestConsumerEx("");
        api.setLogLevel(1);

        // get basic auth credentials from credstore (using a server session)
        TVAppCredStore2 credStore = new TVAppCredStore2(Factory.getSession(SessionType.NATIVE));
        String username = credStore.getValueByName(CREDSTORE_KEY, "username");
        String password = credStore.getValueByName(CREDSTORE_KEY, "password");

        api.setBasicAuth(username, password);
    }
}
