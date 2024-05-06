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

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TNotesUtil;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class TrelloHandler extends BaseHandler {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels;

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

    private List<String> getTrelloAdminIds() throws NotesException {
        Vector<?> adminDocUNIDs = reqBundle.tool.getItemValue("adminUnids");
        List<String> adminIds = new ArrayList<>();

        for (Object unid : adminDocUNIDs) {
            Document adminDoc = reqBundle.tool.getParentDatabase().getDocumentByUNID(unid.toString());
            adminIds.add(adminDoc.getItemValueString("trelloId"));
        }

        return adminIds;
    }

    @SuppressWarnings("unchecked")
    private JsonJavaObject createTask() throws JsonException, NotesException {
        JsonJavaObject card = new JsonJavaObject();

        // standard card properties
        card.put("start", dateToIsoString(new Date()));
        card.put("due", dateToIsoString(addWeekToDate(new Date())));
        card.put("idMembers", getTrelloAdminIds());

        // build description
        JsonJavaObject cardConfig = parseToJson(reqBundle.tool.getItemValueString("adminConfig"));
        // get the exact entity config
        JsonJavaObject entityConfig = cardConfig.getAsObject(translateHttpMethodToCRUD(reqBundle.httpMethod)).getAsObject(reqBundle.crudEntity);

        card.put("name", fillTemplate(entityConfig.getAsString("cardName")));
        card.put("desc", fillTemplate(entityConfig.getAsString("cardDesc")));

        JsonJavaObject checklist = new JsonJavaObject();
        checklist.put("name", fillTemplate(entityConfig.getAsString("checklistName")));

        List<String> adminChecklist = (List<String>) entityConfig.get("checklistItems");
        List<JsonJavaObject> checkItems = new ArrayList<>();

        for (String instruction : adminChecklist) {
            JsonJavaObject checkItem = new JsonJavaObject();
            checkItem.put("name", fillTemplate(instruction));
            checkItems.add(checkItem);
        }

        checklist.put("checkItems", checkItems);
        card.put("checklists", Arrays.asList(checklist));

        JsonJavaObject task = new JsonJavaObject();
        task.put("card", card);

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

    private JsonJavaObject parseToJson(String input) throws JsonException {
        return (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, input);
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

    private boolean isResponeValid(JsonJavaObject response) {
        int httpStatus = response.getInt("httpStatus");
        return response != null && httpStatus >= 200 && httpStatus < 300;
    }

    public void createAdminTask(JsonJavaObject task, String projectPNr, boolean createWebhook) throws Exception {
        // get card object from task
        JsonJavaObject card = getCardFromTask(task);
        // add the id of the list where the card should be created
        card.put("idList", VCDE_ADMIN_TASKS_LIST_ID);
        // add the label to the card
        card.put("idLabels", Arrays.asList(getLabel(projectPNr)));
        // try to create the card
        JsonJavaObject createdCard = trelloAPI.createCard(card);
        // check if card was created successfully
        if (!isResponeValid(createdCard)) {
            throw new Exception("Error creating card: " + createdCard);
        }

        createdCard = (JsonJavaObject) createdCard.get("body");

        // do checklist stuff
        final String cardId = ((JsonJavaObject) createdCard).getAsString("id");

        List<Object> checklists = getChecklistsFromTask(task);

        // add checklists to card
        if (checklists != null && !checklists.isEmpty()) {
            addChecklistsToCard(checklists, cardId);
        }

        // create webhook
        if (createWebhook) {
            createWebhook(cardId);
        }

        doSpecialCases(cardId);

        // special case if the tool.name = BIM-COLLAB
        if(reqBundle.tool.getItemValueString("name").equals("BIM-COLLAB") && reqBundle.httpMethod.equals("POST") && reqBundle.crudEntity.equals("TI")){
            // csv for entraId
            StringBuilder entraIdCSV = new StringBuilder();
            entraIdCSV.append("id,firstname,lastname,email\n");
            entraIdCSV.append("1,Dieter,Menzel,dieter.menzel@galluga.gom\n");
            createCSV(cardId, "entraId.csv", entraIdCSV.toString());
            createCSV(cardId, "bimgollab.csv", entraIdCSV.toString());
        }
    }

    private void doSpecialCases(String cardId) {
        
    }

    private void createCSV(String cardId, String fileName, String content) throws Exception {
        JsonJavaObject createdCsv = trelloAPI.createAttachmentOnCard(cardId, fileName, content.getBytes()); 

        if (!isResponeValid(createdCsv)) {
            throw new Exception("Error creating card: " + createdCsv);
        }
    }

    private void createWebhook(String cardId) throws Exception {
        JsonJavaObject webhook = new JsonJavaObject();
        webhook.put("callbackURL", "https://xapps.voessing.de/VCDE-Config_LM.nsf/crawler.xsp/trello/webhook");
        webhook.put("idModel", cardId);
        JsonJavaObject webhookResp = trelloAPI.createWebhook(webhook);

        // check if webhook was created successfully
        if (!isResponeValid(webhookResp)) {
            throw new Exception("Error creating webhook: " + webhookResp);
        }
    }

    @SuppressWarnings("unchecked")
    private String getLabel(String projecPNr) throws Exception {

        // check if label is already in cache
        if (labels.containsKey(projecPNr)) {
            return labels.get(projecPNr);
        }

        // update labels cache
        JsonJavaObject labelsResp = trelloAPI.getLabels(VCDE_ADMIN_TASKS_BOARD_ID);

        if (!isResponeValid(labelsResp)) {
            throw new Exception("Error getting labels: " + labelsResp);
        }

        List<JsonJavaObject> labelsList = (List<JsonJavaObject>) labelsResp.get("body");

        for (JsonJavaObject label : labelsList) {
            labels.put(label.getAsString("name"), label.getAsString("id"));
        }

        // check if label is now in cache
        if (labels.containsKey(projecPNr)) {
            return labels.get(projecPNr);
        }

        // create label if it doesn't exist
        JsonJavaObject label = new JsonJavaObject();
        label.put("name", projecPNr);
        label.put("color", "green");
        JsonJavaObject newLabel = trelloAPI.createLabel(VCDE_ADMIN_TASKS_BOARD_ID, label);

        if (!isResponeValid(newLabel)) {
            throw new Exception("Error creating label: " + newLabel);
        }

        newLabel = (JsonJavaObject) newLabel.get("body");

        String newLabelId = ((JsonJavaObject) newLabel).getAsString("id");

        // add label to cache
        labels.put(projecPNr, newLabelId);

        return newLabelId;
    }

    private JsonJavaObject getCardFromTask(JsonJavaObject task) throws Exception {
        JsonJavaObject card = TNotesUtil.deepCopyJsonObject(task);
        card = card.getAsObject("card");

        if (card == null) {
            throw new Exception("No card object found in task");
        }

        card.remove("checklists");

        return card;
    }

    private List<Object> getChecklistsFromTask(JsonJavaObject task) throws Exception {
        return task.getAsObject("card").getAsList("checklists");
    }

    private void addChecklistsToCard(List<Object> checklists, String cardId) throws Exception {
        for (Object checklist : checklists) {
            JsonJavaObject checklistObj = (JsonJavaObject) checklist;
            JsonJavaObject createdChecklist = trelloAPI.createChecklist(cardId, checklistObj);

            // check if checklist was created successfully
            if (!isResponeValid(createdChecklist)) {
                throw new Exception("Error creating checklist: " + createdChecklist);
            }

            createdChecklist = (JsonJavaObject) createdChecklist.get("body");

            final String checklistId = ((JsonJavaObject) createdChecklist).getAsString("id");

            // add checkitems to checklist
            List<Object> checkItems = checklistObj.getAsList("checkItems");

            if (checkItems != null && !checkItems.isEmpty()) {
                addCheckItemsToChecklist(checkItems, checklistId);
            }
        }
    }

    private void addCheckItemsToChecklist(List<Object> checkItems, String checklistId) throws Exception {
        for (Object checkItem : checkItems) {
            JsonJavaObject checkItemObj = (JsonJavaObject) checkItem;
            JsonJavaObject createdCheckItem = trelloAPI.createCheckItem(checklistId, checkItemObj);

            // check if checkitem was created successfully
            if (!isResponeValid(createdCheckItem)) {
                throw new Exception("Error creating checkitem: " + createdCheckItem);
            }
        }
    }
}
