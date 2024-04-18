package com.voessing.api.adapter;

import java.util.Arrays;
import java.util.List;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.common.TRestConsumerEx;
import com.voessing.common.TVAppCredStore2;

import lotus.domino.NotesException;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

public class TrelloAPI {

    private static final String CREDSTORE_KEY = "trello_lm";
    private static final int logLevel = 1;
    private static TRestConsumerEx api;
    private static String baseUrl;

    private static final int MAX_RETRIES = 3;

    private static enum Type {
        CARD(Arrays.asList("idList")),
        CHECKLIST(Arrays.asList("name")), 
        CHECKITEM(Arrays.asList("name")),
        LABEL(Arrays.asList("name", "color")),
        WEBHOOK(Arrays.asList("callbackURL", "idModel"));

        public final List<String> requiredFields;

        private Type(List<String> requiredFields) {    
            this.requiredFields = requiredFields;
        }
    }

    public TrelloAPI() throws NotesException {
        api = new TRestConsumerEx("");
        api.setLogLevel(logLevel);

        TVAppCredStore2 credStore = new TVAppCredStore2(Factory.getSession(SessionType.NATIVE));

        baseUrl = credStore.getValueByName(CREDSTORE_KEY, "baseUrl");

        String apiKey = credStore.getValueByName(CREDSTORE_KEY, "apiKey");
        String apiToken = credStore.getValueByName(CREDSTORE_KEY, "apiToken");

        String authHeader = "OAuth oauth_consumer_key=\"" + apiKey + "\", oauth_token=\"" + apiToken + "\"";
        api.addRequestHeader("Authorization", authHeader);
    }

    private JsonJavaObject createResponse(Object body) {
        JsonJavaObject response = new JsonJavaObject();
        response.put("body", body);
        response.put("httpStatus", api.getLastResponseCode());
        return response;
    }

    public JsonJavaObject get(String apiCommand) throws Exception {
        return get(apiCommand, 0);
    }

    public JsonJavaObject get(String apiCommand, int attempt) throws Exception {
        try {
            return createResponse(api.doGet(baseUrl + apiCommand));
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return get(apiCommand, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public JsonJavaObject post(String apiCommand, JsonJavaObject body) throws Exception {
        return post(apiCommand, body, 0);
    }

    public JsonJavaObject post(String apiCommand, JsonJavaObject body, int attempt) throws Exception {
        try {
            Object response = api.doPost(baseUrl + apiCommand, body);
            return createResponse(response);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return post(apiCommand, body, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public JsonJavaObject createCard(JsonJavaObject card) throws Exception {
        verfiyPostObject(card, Type.CARD);
        JsonJavaObject response = post("/cards", card);
        return response;
    }

    public JsonJavaObject createChecklist(String cardId, JsonJavaObject checklist) throws Exception {
        verfiyPostObject(checklist, Type.CHECKLIST);
        return post("/cards/" + cardId + "/checklists", checklist);
    }

    public JsonJavaObject createCheckItem(String checklistId, JsonJavaObject checkitem) throws Exception {
        verfiyPostObject(checkitem, Type.CHECKITEM);
        return post("/checklists/" + checklistId + "/checkItems", checkitem);
    }

    public JsonJavaObject createLabel(String boardId, JsonJavaObject label) throws Exception {
        verfiyPostObject(label, Type.LABEL);
        return post("/boards/" + boardId + "/labels", label);
    }

    public JsonJavaObject getLabels(String boardId) throws Exception {
        return get("/boards/" + boardId + "/labels");
    }

    public JsonJavaObject createWebhook(JsonJavaObject webhook) throws Exception {
        verfiyPostObject(webhook, Type.WEBHOOK);
        return post("/webhooks", webhook);
    }   

    private void verfiyPostObject(JsonJavaObject postObject, Type type) throws Exception {
        if (postObject == null) {
            throw new Exception("Post object is null");
        }

        for (String requiredField : type.requiredFields) {
            if (postObject.get(requiredField) == null) {
                throw new Exception("Post object does not contain " + requiredField);
            }
        }
    }

}
