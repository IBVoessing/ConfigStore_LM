package com.voessing.vcde.tooladapter.handlers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TNotesUtil;

import lotus.domino.Document;
import lotus.domino.Item;
import lotus.domino.NotesException;

public class TrelloHandler extends BaseHandler {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels; 

    private Map<String, String> templateContext = new HashMap<>();

    public TrelloHandler(String crudEntity, String httpMethod, Document request, Document tool, JsonJavaObject body) throws NotesException {
        super(crudEntity, httpMethod, request, tool, body);
        trelloAPI = new TrelloAPI();
        labels = new HashMap<>();
    }

    @Override
    public JsonJavaObject excecute() throws Exception {
        // we only need it for the TI POST request as this is the only request where we use templating
        if(this.crudEntity.equals("TI") && this.httpMethod.equals("POST")){
            buildTemplateContext(request, tool);
        }
        
		createAdminTask(createTask(), getProjectPNr(), false);
        return new JsonJavaObject("success", "Weee Wooo Weee Wooo");
    }

    private String getProjectPNr() throws NotesException{
        String projectPNr = request.getItemValueString("ProjectPNr");
        if(projectPNr == null || projectPNr.isEmpty()){
            projectPNr = "N/A";
        }
        return projectPNr;
    }

    private void buildTemplateContext(Document request, Document tool) throws NotesException {
        templateContext.putAll(docToMap(request, "request"));
        templateContext.putAll(docToMap(tool, "tool"));
        generateComputedValues(request, tool);
        TNotesUtil.logEvent(templateContext.toString());
    }

    private void generateComputedValues(Document request, Document tool) throws NotesException {
        // Compute values based on the request and tool documents
        // and add them to the templateContext with the "$computed." prefix
    
        // Compute a member list from the request document
        String memberList = computeMemberList(request);
        templateContext.put("$computed.memberList", memberList);
    
        // More computed values can be added here...
    }

    @SuppressWarnings("unchecked")
    private String computeMemberList(Document request) throws NotesException {
        StringBuilder memberList = new StringBuilder();

        Vector<String> rawMembers = request.getItemValue("apiMembers");
        List<JsonJavaObject> members = convertToMVStrToJson(rawMembers).stream().peek(member -> member.remove("id")).collect(Collectors.toList());
        
        int counter = 1;
        for (JsonJavaObject member : members) {
            String memberLine = member.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining(" | "));
            memberList.append(counter + ". " + memberLine).append("\n");
            counter++;
        }
        
        return memberList.toString();
    }

    private List<JsonJavaObject> convertToMVStrToJson(Vector<String> input){
        List<JsonJavaObject> result = new ArrayList<>();
        for (String item : input) {
            try {
                result.add( (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, item));
            } catch (JsonException e) {
                e.printStackTrace();
                TNotesUtil.logEvent("Error converting item to Json: " + e.getMessage());
            }
        }
        return result;
    }

    private Map<String, String> docToMap(Document doc, String keyPrefix) throws NotesException {
		keyPrefix = "$" + keyPrefix + ".";
		Map<String, String> map = new HashMap<>();

		for(Object itemKey: doc.getItems()){
			String key = itemKey.toString();

            //map.put(keyPrefix + key, value);
            Vector<?> itemValue = doc.getItemValue(key);
            if(itemValue.isEmpty()){
                // if the item has no value, we skip it
                continue;
            }
            else if (itemValue.size() == 1) {
                String value = itemToString(doc, key);
                processValue(keyPrefix, key, value, map);
            } else {
                // for multi value items, we need to use processValue for each value
                for (int i = 0; i < itemValue.size(); i++) {
                    // .toString() might not work for all domino item types
                    processValue(keyPrefix, key + "[" + i + "].", itemValue.elementAt(i).toString(), map);
                }
            }
		}

		return map;
	}

    private void processValue(String keyPrefix, String key, String value, Map<String, String> map) {
        value = value.trim();
        // check if the value is a JsonJavaArray or JsonJavaObject
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                Object parsedValue = JsonParser.fromJson(JsonJavaFactory.instanceEx, value);
                resolveJsonToMap(keyPrefix + key, (List<?>) parsedValue, map);
            } catch (Exception e) {
                TNotesUtil.logEvent("Error parsing JSON in docToMap: " + e.getMessage() + " - " + value);
            }
        } else if (value.startsWith("{") && value.endsWith("}")) {
            try {
                JsonJavaObject json = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, value);
                resolveJsonToMap(keyPrefix + key + ".", json, map);
            } catch (Exception e) {
                TNotesUtil.logEvent("Error parsing JSON in docToMap: " + e.getMessage() + " - " + value);
            }
        } else {
            map.put(keyPrefix + key, value);
        }
    }

    private void resolveJsonToMap(String keyPrefix, JsonJavaObject json, Map<String, String> map) {
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = keyPrefix + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof JsonJavaObject) {
                // If the value is a JsonJavaObject, recursively resolve it
                resolveJsonToMap(key + ".", (JsonJavaObject) value, map);
            } else if (value instanceof List) {
                // If the value is a JsonJavaArray, handle it
                List<?> array = (List<?>) value;
                for (int i = 0; i < array.size(); i++) {
                    Object arrayValue = array.get(i);
                    if (arrayValue instanceof JsonJavaObject) {
                        // If the array element is a JsonJavaObject, recursively resolve it
                        resolveJsonToMap(key + "[" + i + "].", (JsonJavaObject) arrayValue, map);
                    } else if (arrayValue instanceof List) {
                        resolveJsonToMap(key + "[" + i + "].", (List<?>) arrayValue, map);
                    } else {
                        // Otherwise, just add it to the map
                        map.put(key + "[" + i + "]", arrayValue.toString());
                    }
                }
            } else {
                // Otherwise, just add it to the map
                map.put(key, value.toString());
            }
        }
    }

    private void resolveJsonToMap(String keyPrefix, List<?> array, Map<String, String> map) {
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            String key = keyPrefix + "[" + i + "]";
    
            if (value instanceof JsonJavaObject) {
                resolveJsonToMap(key + ".", (JsonJavaObject) value, map);
            } else if (value instanceof List) {
                resolveJsonToMap(key + ".", (List<?>) value, map);
            } else {
                map.put(key, value.toString());
            }
        }
    }

    private String itemToString(Document doc, String key) {
        try {
            Item item = doc.getFirstItem(key);
            String res = item.getText();
            item.recycle();
            return res;
        } catch (NotesException e) {
            TNotesUtil.logEvent("Error in itemToString: " + e.getMessage());
        }
        return null;
    }

    private List<String> getTrelloAdminIds(Document tool) throws NotesException{
        Vector<?> adminDocUNIDs = tool.getItemValue("adminUnids");
        List<String> adminIds = new ArrayList<>();

        for (Object unid : adminDocUNIDs) {
            Document adminDoc = tool.getParentDatabase().getDocumentByUNID(unid.toString());
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
        card.put("idMembers", getTrelloAdminIds(tool));

        JsonJavaObject checklist = new JsonJavaObject();
        checklist.put("name", "Do this or you will be replaced by a robot");

        switch (httpMethod) {
            case "POST":
                switch (crudEntity) {
                    case "TI":
                        card.put("name", "Create new ToolInstance for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", buildDescription(tool.getItemValueString("adminInstruction")));

                        Vector<String> adminChecklist = tool.getItemValue("adminChecklist");
                        List<JsonJavaObject> checkItems = new ArrayList<>();
                        for (String instruction : adminChecklist) {
                            JsonJavaObject checkItem = new JsonJavaObject();
                            checkItem.put("name", instruction);
                            checkItems.add(checkItem);
                        }
                        checklist.put("checkItems", checkItems);

                        break;
                    case "TIM":
                        card.put("name", "Create new ToolInstanceMembership for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", "TODO add description: Case TIM POST");
                        addToChecklist(Arrays.asList("Add the user to the tool!", "Create the TIM Document for the user"), checklist);
                        break;
                }
                break;
            case "PATCH":
                switch (crudEntity) {
                    case "TI":
                        card.put("name", "Update ToolInstance for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", "TODO add description: Case TI PATCH");
                        addToChecklist(Arrays.asList("Update the tool!", "Update the TI Document!"), checklist);
                        break;
                    case "TIM":
                        card.put("name", "Update ToolInstanceMembership for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", "TODO add description: Case TIM PATCH");
                        addToChecklist(Arrays.asList("Update the user in the tool!", "Update the TIM Document!"), checklist);
                        break;
                }
                break;
            case "DELETE":
                switch (crudEntity) {
                    case "TI":
                        card.put("name", "Delete ToolInstance for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", "TODO add description: Case TI DELETE");
                        addToChecklist(Arrays.asList("Delete the tool!", "Delete the TI Document!"), checklist);
                        break;
                        case "TIM":
                        card.put("name", "Delete ToolInstanceMembership for the tool " + tool.getItemValueString("Title"));
                        card.put("desc", "TODO add description: Case TIM DELETE");
                        addToChecklist(Arrays.asList("Delete the user in the tool!", "Delete the TIM Document"), checklist);
                        break;
                }
                break;
        }

        card.put("checklists", Arrays.asList(checklist));

        JsonJavaObject task = new JsonJavaObject();
        task.put("card", card);

        return task;
    }

    private void addToChecklist(List<String> checkItems, JsonJavaObject checklist) throws NotesException {
        List<JsonJavaObject> checkItemsList = new ArrayList<>();
        for (String checkItem : checkItems) {
            JsonJavaObject checkItemObj = new JsonJavaObject();
            checkItemObj.put("name", checkItem);
            checkItemsList.add(checkItemObj);
        }
        checklist.put("checkItems", checkItemsList);
    }

    private String buildDescription(String input) {

        for (Map.Entry<String, String> entry : templateContext.entrySet()) {
            input = input.replaceAll(entry.getKey().replaceAll("\\$", "\\\\\\$").replaceAll("\\.", "\\\\.")
                    .replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]"), Matcher.quoteReplacement(entry.getValue()));
        }

        return input;
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

    private boolean isResponeValid(JsonJavaObject response){
        int httpStatus = response.getInt("httpStatus");
        return response != null && httpStatus >= 200 && httpStatus < 300;
    }

    public void createAdminTask(JsonJavaObject task, String projectPNr, boolean createWebhook) throws Exception{
        // get card object from task
        JsonJavaObject card = getCardFromTask(task);
        // add the id of the list where the card should be created
        card.put("idList", VCDE_ADMIN_TASKS_LIST_ID);
        // add the label to the card
        card.put("idLabels", Arrays.asList(getLabel(projectPNr)));
        // try to create the card
        JsonJavaObject createdCard = trelloAPI.createCard(card);
        // check if card was created successfully
        if(!isResponeValid(createdCard)){
            throw new Exception("Error creating card: " + createdCard);
        }

        createdCard = (JsonJavaObject) createdCard.get("body");

        // do checklist stuff
        final String cardId = ((JsonJavaObject) createdCard).getAsString("id");

        List<Object> checklists = getChecklistsFromTask(task);

        // add checklists to card
        if(checklists != null && !checklists.isEmpty()){
            addChecklistsToCard(checklists, cardId);
        }

        // create webhook
        if(createWebhook){
            createWebhook(cardId);
        }
    }

    private void createWebhook(String cardId) throws Exception {
        JsonJavaObject webhook = new JsonJavaObject();
        webhook.put("callbackURL", "https://xapps.voessing.de/VCDE-Config_LM.nsf/crawler.xsp/trello/webhook");
        webhook.put("idModel", cardId);
        JsonJavaObject webhookResp = trelloAPI.createWebhook(webhook);

        // check if webhook was created successfully
        if(!isResponeValid(webhookResp)){
            throw new Exception("Error creating webhook: " + webhookResp);
        }
    }

    @SuppressWarnings("unchecked")
    private String getLabel(String projecPNr) throws Exception{

        // check if label is already in cache
        if(labels.containsKey(projecPNr)){
            return labels.get(projecPNr);
        }

        // update labels cache
        JsonJavaObject labelsResp = trelloAPI.getLabels(VCDE_ADMIN_TASKS_BOARD_ID);

        if(!isResponeValid(labelsResp)){
            throw new Exception("Error getting labels: " + labelsResp);
        }

        List<JsonJavaObject> labelsList = (List<JsonJavaObject>) labelsResp.get("body");

        for (JsonJavaObject label : labelsList) {
            labels.put(label.getAsString("name"), label.getAsString("id"));
        }

        // check if label is now in cache
        if(labels.containsKey(projecPNr)){
            return labels.get(projecPNr);
        }

        // create label if it doesn't exist
        JsonJavaObject label = new JsonJavaObject();
        label.put("name", projecPNr);
        label.put("color", "green");
        JsonJavaObject newLabel = trelloAPI.createLabel(VCDE_ADMIN_TASKS_BOARD_ID, label);

        if(!isResponeValid(newLabel)){
            throw new Exception("Error creating label: " + newLabel);
        }

        newLabel = (JsonJavaObject) newLabel.get("body");

        String newLabelId = ((JsonJavaObject) newLabel).getAsString("id");

        // add label to cache
        labels.put(projecPNr, newLabelId);

        return newLabelId;
    }

    private JsonJavaObject getCardFromTask(JsonJavaObject task) throws Exception{
        JsonJavaObject card = TNotesUtil.deepCopyJsonObject(task);
        card = card.getAsObject("card");
        
        if(card == null){
            throw new Exception("No card object found in task");
        }
        
        card.remove("checklists");

        return card;
    }

    private List<Object> getChecklistsFromTask(JsonJavaObject task) throws Exception{
        return task.getAsObject("card").getAsList("checklists");
    }

    private void addChecklistsToCard(List<Object> checklists, String cardId) throws Exception {
        for(Object checklist : checklists){
            JsonJavaObject checklistObj = (JsonJavaObject) checklist;
            JsonJavaObject createdChecklist = trelloAPI.createChecklist(cardId, checklistObj);

            // check if checklist was created successfully
            if(!isResponeValid(createdChecklist)){
                throw new Exception("Error creating checklist: " + createdChecklist);
            }

            createdChecklist = (JsonJavaObject) createdChecklist.get("body");

            final String checklistId = ((JsonJavaObject) createdChecklist).getAsString("id");

            // add checkitems to checklist
            List<Object> checkItems = checklistObj.getAsList("checkItems");

            if(checkItems != null && !checkItems.isEmpty()){
                addCheckItemsToChecklist(checkItems, checklistId);
            }
        }
    }

    private void addCheckItemsToChecklist(List<Object> checkItems, String checklistId) throws Exception {
        for(Object checkItem : checkItems){
            JsonJavaObject checkItemObj = (JsonJavaObject) checkItem;
            JsonJavaObject createdCheckItem = trelloAPI.createCheckItem(checklistId, checkItemObj);

            // check if checkitem was created successfully
            if(!isResponeValid(createdCheckItem)){
                throw new Exception("Error creating checkitem: " + createdCheckItem);
            }
        }
    }
}
