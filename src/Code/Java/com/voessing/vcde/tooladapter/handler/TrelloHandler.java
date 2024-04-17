package com.voessing.vcde.tooladapter.handler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.api.adapter.TrelloAPI;
import com.voessing.common.TNotesUtil;

public class TrelloHandler {
    private final String VCDE_ADMIN_TASKS_BOARD_ID = "66162a1deef60aea4cc681f7";
    private final String VCDE_ADMIN_TASKS_LIST_ID = "661668d9b485dff1f9488151";
    private TrelloAPI trelloAPI;
    private Map<String, String> labels; 

    public TrelloHandler() {
        trelloAPI = new TrelloAPI();
        labels = new HashMap<>();
    }

    public void createAdminTask(JsonJavaObject task, String projectPNr, boolean createWebhook) throws Exception{
        // get card object from task
        JsonJavaObject card = getCardFromTask(task);
        // add the id of the list where the card should be created
        card.put("idList", VCDE_ADMIN_TASKS_LIST_ID);
        // add the label to the card
        card.put("idLabels", Arrays.asList(getLabel(projectPNr)));
        // try to create the card
        Object createdCard = trelloAPI.createCard(card);

        // check if card was created successfully (if the response is a string, it's an error message)
        if(createdCard instanceof String){
            throw new Exception("Error creating card: " + createdCard);
        }

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
        Object webhookResp = trelloAPI.createWebhook(webhook);

        // check if webhook was created successfully
        if(webhookResp instanceof String){
            throw new Exception("Error creating webhook: " + webhookResp);
        }
    }

    private String getLabel(String projecPNr) throws Exception{

        // check if label is already in cache
        if(labels.containsKey(projecPNr)){
            return labels.get(projecPNr);
        }

        // update labels cache
        List<JsonJavaObject> labelsResp = (List<JsonJavaObject>) trelloAPI.getLabels(VCDE_ADMIN_TASKS_BOARD_ID);

        if(labelsResp != null){
            for(JsonJavaObject label : labelsResp){
                labels.put(label.getAsString("name"), label.getAsString("id"));
            }
        }

        // check if label is now in cache
        if(labels.containsKey(projecPNr)){
            return labels.get(projecPNr);
        }

        // create label if it doesn't exist
        JsonJavaObject label = new JsonJavaObject();
        label.put("name", projecPNr);
        label.put("color", "green");
        Object newLabel = trelloAPI.createLabel(VCDE_ADMIN_TASKS_BOARD_ID, label);

        if(newLabel instanceof String){
            throw new Exception("Error creating label: " + newLabel);
        }

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
            Object createdChecklist = trelloAPI.createChecklist(cardId, checklistObj);

            // check if checklist was created successfully
            if(createdChecklist instanceof String){
                throw new Exception("Error creating checklist: " + createdChecklist);
            }

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
            Object createdCheckItem = trelloAPI.createCheckItem(checklistId, checkItemObj);

            // check if checkitem was created successfully
            if(createdCheckItem instanceof String){
                throw new Exception("Error creating checkitem: " + createdCheckItem);
            }
        }
    }
}
