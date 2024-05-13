package com.voessing.api.adapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ContentType;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.google.gson.JsonObject;
import com.voessing.common.TVAppCredStore2;
import com.voessing.common.http.HttpClient;
import com.voessing.common.http.HttpUtil;
import com.voessing.common.http.HttpUtil.FileData;
import com.voessing.common.http.Options;
import com.voessing.common.http.Response;

import lotus.domino.NotesException;

public class TrelloAPI {

    private static final String CREDSTORE_KEY = "trello_lm";
    private static final int logLevel = 1;
    private static HttpClient api;
    
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

        TVAppCredStore2 credStore = new TVAppCredStore2(Factory.getSession(SessionType.NATIVE));

        String baseUrl = credStore.getValueByName(CREDSTORE_KEY, "baseUrl");

        String apiKey = credStore.getValueByName(CREDSTORE_KEY, "apiKey");
        String apiToken = credStore.getValueByName(CREDSTORE_KEY, "apiToken");
        
        Options options = new Options().defaultOptions()
        		.useOAuth(apiKey, apiToken)
        		.setBaseURI(baseUrl);
        
        api = new HttpClient(options);
        api.setLogLevel(logLevel);
    }

    public Response createCard(JsonObject card) throws Exception {
        verfiyPostObject(card, Type.CARD);
        return api.post("/cards", HttpUtil.createEntity(card));
    }

    public Response createChecklist(String cardId, JsonObject checklist) throws Exception {
        verfiyPostObject(checklist, Type.CHECKLIST);
        return api.post("/cards/" + cardId + "/checklists", HttpUtil.createEntity(checklist));
    }

    public Response createCheckItem(String checklistId, JsonObject checkitem) throws Exception {
        verfiyPostObject(checkitem, Type.CHECKITEM);
        return api.post("/checklists/" + checklistId + "/checkItems", HttpUtil.createEntity(checkitem));
    }

    public Response createLabel(String boardId, JsonObject label) throws Exception {
        verfiyPostObject(label, Type.LABEL);
        return api.post("/boards/" + boardId + "/labels", HttpUtil.createEntity(label));
    }

    public Response getLabels(String boardId) throws Exception {
        return api.get("/boards/" + boardId + "/labels");
    }

    public Response createWebhook(JsonObject webhook) throws Exception {
        verfiyPostObject(webhook, Type.WEBHOOK);
        return api.post("/webhooks", HttpUtil.createEntity(webhook));
    }   

    public Response createAttachmentOnCard(String cardId, String fileName, byte[] fileContent) throws Exception {
        Map<String, FileData> files = new HashMap<>();
        files.put("file", new FileData(fileContent, fileName, ContentType.TEXT_PLAIN));
        return api.post("/cards/" + cardId + "/attachments", HttpUtil.createMultipartEntity(null, files));
    }

    private void verfiyPostObject(JsonObject postObject, Type type) throws Exception {
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
