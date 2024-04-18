package com.voessing.vcde.tooladapter.handlers;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.google.gson.Gson;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TNotesUtil;
import com.voessing.vcde.tooladapter.interfaces.ExecutableAdapter;
import com.voessing.vcde.tooladapter.models.TrelloTask;
import com.voessing.vcde.tooladapter.models.TrelloTask.Card;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class TrelloHandler implements ExecutableAdapter {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels; 

    private Gson gson = new Gson();

    public TrelloHandler() throws NotesException {
        trelloAPI = new TrelloAPI();
        labels = new HashMap<>();
    }

    @Override
    public JsonJavaObject excecute(Document request, Document tool, JsonJavaObject body) throws Exception {
        String projectPNr = request.getItemValueString("ProjectPNr");
        if(projectPNr == null || projectPNr.isEmpty()){
            projectPNr = "N/A";
        }

        JsonJavaObject task = createTask(body, tool);

		createAdminTask(task, projectPNr, false);
        return task;
    }

    private JsonJavaObject createTask(JsonJavaObject body, Document tool) throws JsonException, NotesException{
		Card card = new Card();
		card.name = "Test Card";
		card.desc = body.toString();
		card.start = dateToIsoString(new Date());
		card.due = dateToIsoString(addWeekToDate(new Date()));
        //card.idMembers =  tool.getItemValue("adminUnids");
		
		TrelloTask task = new TrelloTask();
		task.card = card;

		JsonJavaObject result = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, gson.toJson(task));

		return result;
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
            if(!isResponeValid(checkItemObj)){
                throw new Exception("Error creating checkitem: " + createdCheckItem);
            }
        }
    }
}
