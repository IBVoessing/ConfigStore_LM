package com.voessing.vcde.tooladapter.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.api.adapter.GraphAPI;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;

import lotus.domino.Document;

public final class TeamsTeamHandler extends BaseHandler{

    private GraphAPI api = new GraphAPI();

    public TeamsTeamHandler(String crudEntity, String httpMethod, Document request, Document tool, JsonJavaObject body) {
        super(crudEntity, httpMethod, request, tool, body);
    }
    
    @Override
	public JsonJavaObject excecute() throws Exception {
        String teamId = createToolInstance(body.getAsObject("apiAttributes"));

        // sleep for 2 seconds to give the API some time to create the team
        Thread.sleep(2000);
        
        List<JsonJavaObject> members = getConversationMembers((List<JsonJavaObject>) body.get("apiMembers"));
        for (JsonJavaObject member : members) {
            createUser(teamId, member);
        }

        //write back the team id into the TI
        Document ti = tool.getParentDatabase().getDocumentByUNID(request.getItemValueString("ToolInstanceUNID"));
        ti.replaceItemValue("objectId", teamId);
        ti.save(); 

		return new JsonJavaObject("success", "hallu:)");
	}

    private List<JsonJavaObject> getConversationMembers(List<JsonJavaObject> members) {
        return members.stream().map(this::getConversationMember).collect(Collectors.toList());
    }

    private JsonJavaObject getConversationMember(JsonJavaObject member) {
        JsonJavaObject conversationMember = new JsonJavaObject();
        conversationMember.put("@odata.type", "#microsoft.graph.aadUserConversationMember");
        conversationMember.put("roles", Arrays.asList(member.get("role")));
        conversationMember.put("user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + member.get("emailaddress") + "')");
        return conversationMember;
    }

    private void createUser(String teamId, JsonJavaObject conversationMember) throws Exception {
        try {
            api.post("/teams/" + teamId + "/members", conversationMember);
        } catch (Exception e) {
            throw new VrhException("Error creating user: " + e.getMessage());
        }
    }

    private String createToolInstance(Object input) throws Exception {

        Map<String, Object> params = (Map<String, Object>) input;

        String email = params.get("email").toString();
        String displayName = params.getOrDefault("displayName", "").toString();
        String description = params.getOrDefault("description", "").toString();
        String template = params.getOrDefault("template", "https://graph.microsoft.com/beta/teamsTemplates('standard')").toString();
    
        // handle team creation via api
        try {
            JsonJavaObject newTeam = new JsonJavaObject();
            newTeam.put("displayName", displayName);
            newTeam.put("description", description);
            newTeam.put("template@odata.bind", template);

            // the team needs an owner
            List<JsonJavaObject> members = new ArrayList<>();
            JsonJavaObject member = new JsonJavaObject();
            member.put("@odata.type", "#microsoft.graph.aadUserConversationMember");
            member.put("roles", Arrays.asList("owner"));
            member.put("user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + email + "')");
            members.add(member);
            newTeam.put("members", members);

            Object resp = api.post("/teams", newTeam);
            return extractTeamIdFromHeaders(resp);

        } catch (Exception e) {   
            e.printStackTrace();
            throw new VrhException("Error creating tool instance: " + e.getMessage());
        }
    }	

    private String extractTeamIdFromHeaders(Object resp) {
        try {
            JsonJavaObject response = (JsonJavaObject) resp;
            Map<String, Object> headers = response.getAsMap("headers");
            List<String> contentLocationArray = (List<String>) headers.get("Content-Location");
            String contentLocation = contentLocationArray.get(0);
    
            // The team ID is enclosed in single quotes ('), so we can extract it using a regular expression
            Pattern pattern = Pattern.compile("'(.*?)'");
            Matcher matcher = pattern.matcher(contentLocation);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new Exception("No team ID found in Content-Location header");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
