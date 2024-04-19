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

import lotus.domino.Item;

import com.google.gson.Gson;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TNotesUtil;
import com.voessing.vcde.tooladapter.interfaces.ExecutableAdapter;
import com.voessing.vcde.tooladapter.models.TrelloTask;
import com.voessing.vcde.tooladapter.models.TrelloTask.Card;
import com.voessing.vcde.tooladapter.models.TrelloTask.Card.CheckItem;
import com.voessing.vcde.tooladapter.models.TrelloTask.Card.Checklist;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class TrelloHandler implements ExecutableAdapter {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels; 

    private Map<String, String> templateContext = new HashMap<>();

    private Gson gson = new Gson();

    public TrelloHandler() throws NotesException {
        trelloAPI = new TrelloAPI();
        labels = new HashMap<>();
    }

    @Override
    public JsonJavaObject excecute(Document request, Document tool, JsonJavaObject body) throws Exception {

        buildTemplateContext(request, tool);

        String projectPNr = request.getItemValueString("ProjectPNr");
        if(projectPNr == null || projectPNr.isEmpty()){
            projectPNr = "N/A";
        }

        JsonJavaObject task = createTask(request, tool, body);

		createAdminTask(task, projectPNr, false);
        return task;
    }

    private void buildTemplateContext(Document request, Document tool) throws NotesException {
        templateContext.putAll(docToMap(request, "request"));
        templateContext.putAll(docToMap(tool, "tool"));
        TNotesUtil.logEvent(templateContext.toString());
    }

    private Map<String, String> docToMap(Document doc, String keyPrefix) throws NotesException {
		keyPrefix = "$" + keyPrefix + ".";
		Map<String, String> map = new HashMap<>();

		for(Object itemKey: doc.getItems()){
			String key = itemKey.toString();

            //map.put(keyPrefix + key, value);

            if(doc.getItemValue(key).isEmpty()){
                // if the item has no value, we skip it
                continue;
            }
            else if (doc.getItemValue(key).size() == 1) {
                String value = itemToString(doc, key);
                processValue(keyPrefix, key, value, map);
            } else {
                // for multi value items, we need to use processValue for each value
                for (int i = 0; i < doc.getItemValue(key).size(); i++) {
                    processValue(keyPrefix, key + "[" + i + "]", doc.getItemValue(key).elementAt(i).toString(), map);
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
                if (parsedValue instanceof JsonJavaArray) {
                    resolveJsonToMap(keyPrefix + key, new ArrayList((JsonJavaArray) parsedValue), map);
                } else {
                    resolveJsonToMap(keyPrefix + key + ".", (ArrayList) parsedValue, map);
                }
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
                List array = (List) value;
                for (int i = 0; i < array.size(); i++) {
                    Object arrayValue = array.get(i);
                    if (arrayValue instanceof JsonJavaObject) {
                        // If the array element is a JsonJavaObject, recursively resolve it
                        resolveJsonToMap(key + "[" + i + "].", (JsonJavaObject) arrayValue, map);
                    } else if (arrayValue instanceof List) {
                        resolveJsonToMap(key + "[" + i + "]", (List) arrayValue, map);
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

    private void resolveJsonToMap(String keyPrefix, List array, Map<String, String> map) {
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            String key = keyPrefix + "[" + i + "]";
    
            if (value instanceof JsonJavaObject) {
                resolveJsonToMap(key + ".", (JsonJavaObject) value, map);
            } else if (value instanceof List) {
                resolveJsonToMap(key, (List) value, map);
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
        Vector adminDocUNIDs = tool.getItemValue("adminUnids");
        List<String> adminIds = new ArrayList<>();

        for (Object unid : adminDocUNIDs) {
            Document adminDoc = tool.getParentDatabase().getDocumentByUNID(unid.toString());
            adminIds.add(adminDoc.getItemValueString("trelloId"));
        }

        return adminIds;   
    }

    private JsonJavaObject createTask(Document request, Document tool, JsonJavaObject body) throws JsonException, NotesException{
		Card card = new Card();
		card.name = "Neue ToolInstanz für das Tool " + tool.getItemValueString("Title") + " erstellen";
		card.desc = generateDescription(request, tool, body);   
		card.start = dateToIsoString(new Date());
		card.due = dateToIsoString(addWeekToDate(new Date()));
        card.idMembers =  getTrelloAdminIds(tool);

        Vector<String> checklists = tool.getItemValue("adminChecklist");
        List<CheckItem> checklistsList = new ArrayList<>();
        for(String checklist : checklists){
            CheckItem checkItem = new CheckItem();
            checkItem.name = checklist;
            checklistsList.add(checkItem);
        }

        Checklist checklist = new Checklist();
        checklist.name = "Do this or you will be replaced by a robot";
        checklist.checkItems = checklistsList;
		
		TrelloTask task = new TrelloTask();
		task.card = card;
        task.card.checklists = Arrays.asList(checklist);

		JsonJavaObject result = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, gson.toJson(task));

		return result;
	}

    private String generateDescription(Document request, Document tool, JsonJavaObject body) throws NotesException{
        StringBuilder description = new StringBuilder();
        description.append("Es wurde eine neue ToolInstanz für das Tool ");
        description.append(tool.getItemValueString("Title"));
        description.append(" beantragt.\n\n");

        description.append("Projekt: ");
        description.append(request.getItemValueString("ProjectTitle"));
        description.append(" (");
        description.append(request.getItemValueString("ProjectPNr"));
        description.append(")\n\n");
        
        description.append("Gewünschter Name der ToolInstanz: ");
        description.append(body.get("tiTitle"));
        description.append("\n\n");

        description.append("Beschreibung:\n");
        description.append(body.get("tiDescription"));
        description.append("\n\n");

        description.append("Verantwortlicher/ Besitzer: ");
        description.append(body.get("tiOwner"));
        description.append("\n\n");

        description.append("Handlungsempfehlung:\n");
        description.append(populateTemplate(tool.getItemValueString("adminInstruction")));
        description.append("\n\n");
        
        description.append(membersToInstructions(body));
        description.append("\n");   

        description.append("Bitte beachten Sie, dass die ToolInstanz innerhalb einer Woche erstellt werden muss.\n\n");

        description.append("Payload:\n");
        description.append(body.toString());

        return description.toString();
        }

        private String populateTemplate(String input) {
            
            for (Map.Entry<String, String> entry : templateContext.entrySet()) {
                input = input.replaceAll(entry.getKey().replaceAll("\\$", "\\\\\\$").replaceAll("\\.", "\\\\.").replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]"), Matcher.quoteReplacement(entry.getValue()));
            }

            return input;
        }

        private String membersToInstructions(JsonJavaObject body){
        List<JsonJavaObject> members = (List<JsonJavaObject>) body.get("apiMembers");
        
        StringBuilder instructions = new StringBuilder();
        instructions.append("Folgende Personen sollen hinzugefügt werden:\n");
        
        for(JsonJavaObject member : members){
            instructions.append(member.get("firstname")).append(" ").append(member.get("lastname")).append(" - ");
            instructions.append(member.get("emailaddress"));
            instructions.append("\n");
        }

        return instructions.toString();
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

            if(checkItems != null || !checkItems.isEmpty()){
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
