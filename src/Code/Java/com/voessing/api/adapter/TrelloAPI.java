package com.voessing.api.adapter;

import java.util.Arrays;
import java.util.List;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.common.TRestConsumerEx;
import com.voessing.common.TVAppCredStore;

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

    public TrelloAPI() {
        api = new TRestConsumerEx("");
        api.setLogLevel(logLevel);

        baseUrl = TVAppCredStore.getValueByName(CREDSTORE_KEY, "baseUrl");

        String apiKey = TVAppCredStore.getValueByName(CREDSTORE_KEY, "apiKey");
        String apiToken = TVAppCredStore.getValueByName(CREDSTORE_KEY, "apiToken");

        String authHeader = "OAuth oauth_consumer_key=\"" + apiKey + "\", oauth_token=\"" + apiToken + "\"";
        api.addRequestHeader("Authorization", authHeader);
    }

    public Object get(String apiCommand) throws Exception {
        return get(apiCommand, 0);
    }

    public Object get(String apiCommand, int attempt) throws Exception {
        try {
            return api.doGet(baseUrl + apiCommand);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return get(apiCommand, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public Object post(String apiCommand, JsonJavaObject body) throws Exception {
        return post(apiCommand, body, 0);
    }

    public Object post(String apiCommand, JsonJavaObject body, int attempt) throws Exception {
        try {
            return api.doPost(baseUrl + apiCommand, body);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return post(apiCommand, body, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public Object createCard(JsonJavaObject card) throws Exception {
        verfiyPostObject(card, Type.CARD);
        return api.doPost(baseUrl + "/cards", card);
    }

    public Object createChecklist(String cardId, JsonJavaObject checklist) throws Exception {
        verfiyPostObject(checklist, Type.CHECKLIST);
        return api.doPost(baseUrl + "/cards/" + cardId + "/checklists", checklist);
    }

    public Object createCheckItem(String checklistId, JsonJavaObject checkitem) throws Exception {
        verfiyPostObject(checkitem, Type.CHECKITEM);
        return api.doPost(baseUrl + "/checklists/" + checklistId + "/checkItems", checkitem);
    }

    public Object createLabel(String boardId, JsonJavaObject label) throws Exception {
        verfiyPostObject(label, Type.LABEL);
        return api.doPost(baseUrl + "/boards/" + boardId + "/labels", label);
    }

    public Object getLabels(String boardId) throws Exception {
        return api.doGet(baseUrl + "/boards/" + boardId + "/labels");
    }

    public Object createWebhook(JsonJavaObject webhook) throws Exception {
        verfiyPostObject(webhook, Type.WEBHOOK);
        return api.doPost(baseUrl + "/webhooks", webhook);
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
