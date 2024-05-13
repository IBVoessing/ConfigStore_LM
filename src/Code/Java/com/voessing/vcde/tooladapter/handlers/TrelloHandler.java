package com.voessing.vcde.tooladapter.handlers;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.http.Response;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class TrelloHandler extends BaseHandler {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels;
    private Gson gson = new Gson();

    private VelocityContext templateContext = new VelocityContext();
    // define multi value fields in order to build the templateContext correctly
    private final List<String> mvFields = Arrays.asList("apiMembers");

    public TrelloHandler(ReqBundle reqBundle) throws NotesException {
        super(reqBundle);
        trelloAPI = new TrelloAPI();
        labels = new HashMap<>();
        Velocity.init();
    }

    @Override
    public JsonJavaObject excecute() throws Exception {
        buildTemplateContext();

        createAdminTask(createTask(), getProjectPNr(), false);
        return new JsonJavaObject("success", "Weee Wooo Weee Wooo");
    }

    private String getProjectPNr() throws NotesException {
        String projectPNr = reqBundle.request.getItemValueString("ProjectPNr");
        if (projectPNr == null || projectPNr.isEmpty()) {
            projectPNr = "N/A";
        }
        return projectPNr;
    }

    private void buildTemplateContext() throws NotesException {
        templateContext.put("request", docToMap(reqBundle.request));
        templateContext.put("tool", docToMap(reqBundle.tool));
    }

    private Map<String, Object> docToMap(Document doc) throws NotesException {
        Map<String, Object> map = new HashMap<>();

        for (Object itemKey : doc.getItems()) {
            String key = itemKey.toString();

            // map.put(keyPrefix + key, value);
            Vector<?> itemValue = doc.getItemValue(key);

            // for multi value items, we need to use processValue for each value
            for (int i = 0; i < itemValue.size(); i++) {
                // .toString() might not work for all domino item types
                processValue(key, itemValue.elementAt(i).toString(), map);
            }
        }

        return map;
    }

    private void processValue(String key, String value, Map<String, Object> map) {
        value = value.trim();
        // check if the value is a JsonJavaArray or JsonJavaObject
        if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
            try {
                Object parsedValue = JsonParser.fromJson(JsonJavaFactory.instance, value);
                addToMap(key, parsedValue, map);
            } catch (Exception e) {
                // guess it's a string ¯\_(ツ)_/¯
                addToMap(key, value, map);
            }
        } else {
            addToMap(key, value, map);
        }
    }

    @SuppressWarnings("unchecked")
	private void addToMap(String key, Object value, Map<String, Object> map) {
        if (map.containsKey(key)) {
            Object existingValue = map.get(key);
            if (existingValue instanceof List) {
                ((List<Object>) existingValue).add(value);
            } else {
                map.put(key, new ArrayList<>(Arrays.asList(existingValue, value)));
            }
        } else {
            if (mvFields.contains(key)) {
                map.put(key, new ArrayList<>(Arrays.asList(value)));
            } else {
                map.put(key, value);
            }
        }
    }

    private JsonArray getTrelloAdminIds() throws NotesException {
        Vector<?> adminDocUNIDs = reqBundle.tool.getItemValue("adminUnids");
        JsonArray adminIds = new JsonArray();

        for (Object unid : adminDocUNIDs) {
            Document adminDoc = reqBundle.tool.getParentDatabase().getDocumentByUNID(unid.toString());
            adminIds.add(adminDoc.getItemValueString("trelloId"));
        }

        return adminIds;
    }

    private JsonObject createTask() throws JsonException, NotesException {
    	JsonObject card = new JsonObject();

        // standard card properties
        card.addProperty("start", dateToIsoString(new Date()));
        card.addProperty("due", dateToIsoString(addWeekToDate(new Date())));
        card.add("idMembers", getTrelloAdminIds());

        // build description
        JsonObject cardConfig = parseToJson(reqBundle.tool.getItemValueString("adminConfig"));
        // get the exact entity config
        JsonObject entityConfig = cardConfig.get(translateHttpMethodToCRUD(reqBundle.httpMethod)).getAsJsonObject().get(reqBundle.crudEntity).getAsJsonObject();

        card.addProperty("name", fillTemplate(entityConfig.get("cardName").getAsString()));
        card.addProperty("desc", fillTemplate(entityConfig.get("cardDesc").getAsString()));

        JsonObject checklist = new JsonObject();
        checklist.addProperty("name", fillTemplate(entityConfig.get("checklistName").getAsString()));

        JsonArray adminChecklist = entityConfig.get("checklistItems").getAsJsonArray();
        JsonArray checkItems = new JsonArray();

        for (JsonElement instruction : adminChecklist) {
        	
        	String instStr = instruction.getAsString();
        	
            JsonObject checkItem = new JsonObject();
            checkItem.addProperty("name", fillTemplate(instStr));
            checkItems.add(checkItem);
        }

        checklist.add("checkItems", checkItems);
        
        JsonArray checklists = new JsonArray();
        checklists.add(checklist);
        card.add("checklists", checklists);

        JsonObject task = new JsonObject();
        task.add("card", card);

        return task;
    }

    private String translateHttpMethodToCRUD(String httpMethod){
        switch(httpMethod){
            case "GET":
                return "READ";
            case "POST":
                return "CREATE";
            case "PATCH":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            default:
                return "READ";
        }
    }

    private JsonObject parseToJson(String input) throws JsonException {
        return (JsonObject) com.google.gson.JsonParser.parseString(input);
    }

    private String fillTemplate(String input) {
         /* Create a writer to hold the processed template */
        StringWriter writer = new StringWriter();

        /* Process the template */
        Velocity.evaluate(templateContext, writer, "", input);

        return writer.toString();
    }

    private String dateToIsoString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return sdf.format(date);
    }

    private Date addWeekToDate(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.WEEK_OF_YEAR, 1);
        return c.getTime();
    }

    public void createAdminTask(JsonObject task, String projectPNr, boolean createWebhook) throws Exception {
        // get card object from task
        JsonObject card = getCardFromTask(task);
        // add the id of the list where the card should be created
        card.addProperty("idList", VCDE_ADMIN_TASKS_LIST_ID);
        // add the label to the card
        JsonArray labels = new JsonArray();
        labels.add(getLabel(projectPNr));
        card.add("idLabels", labels);
        // try to create the card
        Response createdCard = trelloAPI.createCard(card);
        // check if card was created successfully
        if (!createdCard.isOk()) {
            throw new Exception("Error creating card: " + createdCard);
        }

        // do checklist stuff
        final String cardId = ((JsonObject) createdCard.parseWithGSON()).get("id").getAsString();

        JsonArray checklists = getChecklistsFromTask(task);

        // add checklists to card
        if (checklists != null && !checklists.isEmpty()) {
            addChecklistsToCard(checklists, cardId);
        }

        // create webhook
        if (createWebhook) {
            createWebhook(cardId);
        }

        doSpecialCases(cardId);
    }

    private void doSpecialCases(String cardId) throws Exception {   
        // special case for BIM-COLLAB
        if(isToolName("BIM-COLLAB") && isHttpMethod("POST") && isCrudEntity("TI")){
            attachFileToCard(cardId, "import-users.csv", generateBimCollabCSV());
            attachFileToCard(cardId, "user-invite.csv", generateUserInvite());
        }
    }

    private String generateUserInvite() throws Exception{
        StringBuilder csv = new StringBuilder();
        csv.append("version:v1.0\n");
        csv.append("Email address to invite [inviteeEmail] Required,Redirection url [inviteRedirectURL] Required,Send invitation message (true or false) [sendEmail],Customized invitation message [customizedMessageBody]\n");
        List<String> members = reqBundle.request.getItemValues("apiMembers", String.class);

        for (String member : members) {
            JsonObject memberObj = parseToJson(member);
            csv.append(memberObj.get("mail").getAsString());
            csv.append(",");
            csv.append("https://myapplications.microsoft.com");
            csv.append(",");
            csv.append("true");
            csv.append(",");
            csv.append("Welcome to the team!");
            csv.append("\n");
        }

        return csv.toString();
    }

    private String generateBimCollabCSV() throws JsonException {
        StringBuilder csv = new StringBuilder();
        csv.append("email address,last name,first name,initials,company name\n");

        List<String> members = reqBundle.request.getItemValues("apiMembers", String.class);

        for (String member : members) {
            JsonObject memberObj = parseToJson(member);
            csv.append(memberObj.get("mail").getAsString());
            csv.append(",");
            csv.append(memberObj.get("lastname").getAsString());
            csv.append(",");
            csv.append(memberObj.get("firstname").getAsString());
            csv.append(",");
            csv.append(memberObj.get("initials").getAsString());
            csv.append(",");
            csv.append(memberObj.get("company").getAsString());
            csv.append("\n");
        }

        return csv.toString();
    }

    private boolean isToolName(String toolName) throws Exception {
        return reqBundle.tool.getItemValueString("name").equals(toolName);
    }

    private boolean isHttpMethod(String httpMethod) throws Exception {
        return reqBundle.httpMethod.equals(httpMethod);
    }

    private boolean isCrudEntity(String crudEntity) throws Exception {
        return reqBundle.crudEntity.equals(crudEntity);
    }

    private void attachFileToCard(String cardId, String fileName, String content) throws Exception {
        Response createdCsv = trelloAPI.createAttachmentOnCard(cardId, fileName, content.getBytes()); 

        if (!createdCsv.isOk()) {
            throw new Exception("Error creating card: " + createdCsv);
        }
    }

    private void createWebhook(String cardId) throws Exception {
        JsonObject webhook = new JsonObject();
        webhook.addProperty("callbackURL", "https://xapps.voessing.de/VCDE-Config_LM.nsf/crawler.xsp/trello/webhook");
        webhook.addProperty("idModel", cardId);
        Response webhookResp = trelloAPI.createWebhook(webhook);

        // check if webhook was created successfully
        if (!webhookResp.isOk()) {
            throw new Exception("Error creating webhook: " + webhookResp);
        }
    }

    private String getLabel(String projecPNr) throws Exception {

        // check if label is already in cache
        if (labels.containsKey(projecPNr)) {
            return labels.get(projecPNr);
        }

        // update labels cache
        Response labelsResp = trelloAPI.getLabels(VCDE_ADMIN_TASKS_BOARD_ID);

        if (!labelsResp.isOk()) {
            throw new Exception("Error getting labels: " + labelsResp);
        }

        JsonArray labelsList = (JsonArray) labelsResp.parseWithGSON();

        for (JsonElement label : labelsList) {
        	JsonObject labelObj = (JsonObject) label;
            labels.put(labelObj.get("name").getAsString(), labelObj.get("id").getAsString());
        }

        // check if label is now in cache
        if (labels.containsKey(projecPNr)) {
            return labels.get(projecPNr);
        }

        // create label if it doesn't exist
        JsonObject label = new JsonObject();
        label.addProperty("name", projecPNr);
        label.addProperty("color", "green");
        Response newLabel = trelloAPI.createLabel(VCDE_ADMIN_TASKS_BOARD_ID, label);

        if (!newLabel.isOk()) {
            throw new Exception("Error creating label: " + newLabel);
        }

        String newLabelId = ((JsonObject) newLabel.parseWithGSON()).get("id").getAsString();

        // add label to cache
        labels.put(projecPNr, newLabelId);

        return newLabelId;
    }

    private JsonObject getCardFromTask(JsonObject task) throws Exception {
        JsonObject card = task.deepCopy();
        card = card.get("card").getAsJsonObject();

        if (card == null) {
            throw new Exception("No card object found in task");
        }

        card.remove("checklists");

        return card;
    }

    private JsonArray getChecklistsFromTask(JsonObject task) throws Exception {
        return task.getAsJsonObject("card").get("checklists").getAsJsonArray();
    }

    private void addChecklistsToCard(JsonArray checklists, String cardId) throws Exception {
        for (Object checklist : checklists) {
            JsonObject checklistObj = (JsonObject) checklist;
            Response createdChecklist = trelloAPI.createChecklist(cardId, checklistObj);

            // check if checklist was created successfully
            if (!createdChecklist.isOk()) {
                throw new Exception("Error creating checklist: " + createdChecklist);
            }

            final String checklistId = ((JsonObject) createdChecklist.parseWithGSON()).get("id").getAsString();

            // add checkitems to checklist
            JsonArray checkItems = checklistObj.get("checkItems").getAsJsonArray();

            if (checkItems != null && !checkItems.isEmpty()) {
                addCheckItemsToChecklist(checkItems, checklistId);
            }
        }
    }

    private void addCheckItemsToChecklist(JsonArray checkItems, String checklistId) throws Exception {
        for (Object checkItem : checkItems) {
        	JsonObject checkItemObj = (JsonObject) checkItem;
            Response createdCheckItem = trelloAPI.createCheckItem(checklistId, checkItemObj);

            // check if checkitem was created successfully
            if (!createdCheckItem.isOk()) {
                throw new Exception("Error creating checkitem: " + createdCheckItem);
            }
        }
    }
}
