package com.voessing.api.adapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.common.TRestConsumerEx;
import com.voessing.common.TVAppCredStore2;
import com.voessing.common.http.HttpClient;
import com.voessing.common.http.HttpUtil;
import com.voessing.common.http.HttpUtil.FileData;
import com.voessing.common.http.Response;

import lotus.domino.NotesException;

public class TrelloAPI {

    private static final String CREDSTORE_KEY = "trello_lm";
    private static final int logLevel = 1;
    private static HttpClient api;
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
        api = new HttpClient();
        api.setLogLevel(logLevel);

        TVAppCredStore2 credStore = new TVAppCredStore2(Factory.getSession(SessionType.NATIVE));

        baseUrl = credStore.getValueByName(CREDSTORE_KEY, "baseUrl");

        String apiKey = credStore.getValueByName(CREDSTORE_KEY, "apiKey");
        String apiToken = credStore.getValueByName(CREDSTORE_KEY, "apiToken");

        api.useOAuth(apiKey, apiToken);
    }

    public Response get(String apiCommand) throws Exception {
        return get(apiCommand, 0);
    }

    public Response get(String apiCommand, int attempt) throws Exception {
        try {
            return api.fetch(baseUrl + apiCommand);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return get(apiCommand, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public Response post(String apiCommand, HttpEntity body) throws Exception {
        return post(apiCommand, body, 0);
    }

    public Response post(String apiCommand, HttpEntity body, int attempt) throws Exception {
        try {
            return api.fetch(baseUrl + apiCommand, "POST", body);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                return post(apiCommand, body, attempt + 1);
            } else {
                throw e;
            }
        }
    }

    public Response createCard(JsonJavaObject card) throws Exception {
        verfiyPostObject(card, Type.CARD);
        return post("/cards", HttpUtil.createEntity(card));
    }

    public Response createChecklist(String cardId, JsonJavaObject checklist) throws Exception {
        verfiyPostObject(checklist, Type.CHECKLIST);
        return post("/cards/" + cardId + "/checklists", HttpUtil.createEntity(checklist));
    }

    public Response createCheckItem(String checklistId, JsonJavaObject checkitem) throws Exception {
        verfiyPostObject(checkitem, Type.CHECKITEM);
        return post("/checklists/" + checklistId + "/checkItems", HttpUtil.createEntity(checkitem));
    }

    public Response createLabel(String boardId, JsonJavaObject label) throws Exception {
        verfiyPostObject(label, Type.LABEL);
        return post("/boards/" + boardId + "/labels", HttpUtil.createEntity(label));
    }

    public Response getLabels(String boardId) throws Exception {
        return get("/boards/" + boardId + "/labels");
    }

    public Response createWebhook(JsonJavaObject webhook) throws Exception {
        verfiyPostObject(webhook, Type.WEBHOOK);
        return post("/webhooks", HttpUtil.createEntity(webhook));
    }   

    public Response createAttachmentOnCard(String cardId, String fileName, byte[] fileContent) throws Exception {
        Map<String, FileData> files = new HashMap<>();
        files.put("file", new FileData(fileContent, fileName, ContentType.TEXT_PLAIN));
        return post("/cards/" + cardId + "/attachments", HttpUtil.createMultipartEntity(null, files));
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
