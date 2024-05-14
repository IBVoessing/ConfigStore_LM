package com.voessing.vcde.tooladapter.handlers;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.api.adapter.GraphAPINew;
import com.voessing.common.TNotesUtil;
import com.voessing.common.http.HttpUtil;
import com.voessing.common.http.Response;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;

import lotus.domino.Document;

public final class TeamsTeamHandler extends BaseHandler {

    private GraphAPINew api = new GraphAPINew();
    private Gson gson = new Gson();

    public TeamsTeamHandler(ReqBundle reqBundle) {
        super(reqBundle);
    }

    @SuppressWarnings("unchecked")
	@Override
    public JsonJavaObject excecute() throws Exception {

        JsonObject apiAttributesObj = reqBundle.body.get("apiAttributes").getAsJsonObject();

        Map<String, Object> map = gson.fromJson(apiAttributesObj, Map.class);

        String teamId = createToolInstance(map);

        // sleep for 2 seconds to give the API some time to create the team
        Thread.sleep(2000);

        JsonArray members = getConversationMembers(reqBundle.body.get("apiMembers").getAsJsonArray());
        for (JsonElement member : members) {
            createUser(teamId, member.getAsJsonObject());
        }

        // write back the team id into the TI
        Document ti = reqBundle.tool.getParentDatabase()
                .getDocumentByUNID(reqBundle.request.getItemValueString("ToolInstanceUNID"));
        ti.replaceItemValue("objectId", teamId);
        ti.save();

        return new JsonJavaObject("success", "hallu:)");
    }

    private JsonArray getConversationMembers(JsonArray members) {
        JsonArray result = new JsonArray();
        for (int i = 0; i < members.size(); i++) {
            JsonObject member = members.get(i).getAsJsonObject();
            result.add(getConversationMember(member));
        }
        return result;
    }

    private JsonObject getConversationMember(JsonObject member) {
        JsonObject conversationMember = new JsonObject();
        conversationMember.addProperty("@odata.type", "#microsoft.graph.aadUserConversationMember");
        JsonArray roles = new JsonArray();
        roles.add(member.get("role").getAsString());
        conversationMember.add("roles", roles);
        conversationMember.addProperty("user@odata.bind",
                "https://graph.microsoft.com/v1.0/users('" + member.get("emailaddress").getAsString() + "')");
        return conversationMember;
    }

    private void createUser(String teamId, JsonObject conversationMember) throws Exception {
        try {
            api.client.post("/teams/" + teamId + "/members", HttpUtil.createEntity(conversationMember));
        } catch (Exception e) {
            throw new VrhException("Error creating user: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
	private String createToolInstance(Object input) throws Exception {

        Map<String, Object> params = (Map<String, Object>) input;

        String email = params.get("email").toString();
        String displayName = params.getOrDefault("displayName", "").toString();
        String description = params.getOrDefault("description", "").toString();
        String template = params.getOrDefault("template", "https://graph.microsoft.com/beta/teamsTemplates('standard')")
                .toString();

        // handle team creation via api
        try {
            JsonObject newTeam = new JsonObject();
            newTeam.addProperty("displayName", displayName);
            newTeam.addProperty("description", description);
            newTeam.addProperty("template@odata.bind", template);

            // the team needs an owner
            JsonArray members = new JsonArray();
            JsonObject member = new JsonObject();
            member.addProperty("@odata.type", "#microsoft.graph.aadUserConversationMember");
            
            JsonArray roles = new JsonArray();
            roles.add("owner");
            member.add("roles", roles);
            
            member.addProperty("user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + email + "')");
            
            members.add(member);
            newTeam.add("members", members);

            Response resp = api.client.post("/teams", HttpUtil.createEntity(newTeam));
            return extractTeamIdFromHeaders(resp);
        } catch (Exception e) {
            e.printStackTrace();
            throw new VrhException("Error creating tool instance: " + e.getMessage());
        }
    }

    private String extractTeamIdFromHeaders(Response resp) throws Exception {
        String contentLocation = resp.getHeaders().get("Content-Location");

        // The team ID is enclosed in single quotes ('), so we can extract it using a
        // regular expression
        Pattern pattern = Pattern.compile("'(.*?)'");
        Matcher matcher = pattern.matcher(contentLocation);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("No team ID found in Content-Location header");
        }

    }
}
