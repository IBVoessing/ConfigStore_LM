package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class PmToolInstanceMembershipsOfProject extends VrhDominoNotesDocumentHandler {

	@Override
	protected String configSearchQueryServerConstraint(HttpServletRequest request) throws Exception {
		// ### always reduce response to the given project and deny all requests w/o project specification

		// eventually filter by IDs (needed for (My-Tools-dashlet))
		// TODO: move to onSkipDocument()
		String projectParticipantUNIDs = getRequestParameterValue(request, "projectparticipantunids");
		if (projectParticipantUNIDs != null && !projectParticipantUNIDs.isEmpty()) {
			return "@Contains(ProjectParticipantUNID; " + String.join(":", projectParticipantUNIDs.split(",")) + ")";
		}

		// mandatory filter by project
		String rid = getRequestParameterValue(request, "ProjectRID");
		if (rid == null || rid.isEmpty()) {
			throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectRID' was not given in request");
		}

		String uid = getRequestParameterValue(request, "ProjectUNID");
		if (uid == null || uid.isEmpty()) {
			throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectUNID' was not given in request");
		}

		return "(ProjectRID=\"" + rid + "\") & (ProjectUNID=\"" + uid + "\")";
	}

	@Override
	protected void onBeforeSave(Document document, boolean isNewDocument, Map<String, String[]> parameterMap, Map<String, ?> payload) throws Exception {
		super.onBeforeSave(document, isNewDocument, parameterMap, payload);

		// ### maintenance "Originator" (when creating document), "Reviser" (when editing existent document) and "Janitor" (when creating or editing existent document)
		if (isNewDocument) {
			// document.replaceItemValue("Janitor", "frontend");
			document.replaceItemValue("Originator", "frontend");
		} else {
			document.replaceItemValue("Reviser", "frontend");
		}
	}

	/*
	 * Skip orphan documents.
	 * 
	 * @see com.voessing.xapps.utils.vrh.handler.VrhDominoResourceHandler#onFilterDocument(lotus.domino.Document)
	 */
	@Override
	protected boolean onSkipDocument(Document document) throws NotesException {
		// Keep filter expression in sync with server constraint (which is redundant, but may improve performance)!
		return super.onSkipDocument(document) // inherited skip
			|| document.getItemValueString("$crawlState").equalsIgnoreCase("orphan"); // hide orphans
	}

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) throws VrhException {
		// endpoint config
		config.setAllowedMethods("GET, OPTIONS, PATCH, POST");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);

		// ### resource config
		config.setFormName("ToolInstanceMembership");

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmToolInstanceMembershipsOfProject") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectRID")) // add project rid or nothing
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectUNID")) // add project unid or nothing
		);

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));

		// 02. payload
		config.getAttributeConfigs().put("detailInfo", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("emailAddress", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("firstName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("lastName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("name", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("roles", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());
		config.getAttributeConfigs().put("url", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));

		// 03. meta data
		config.getAttributeConfigs().put("$crawlState", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Originator", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Reviser", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());

		// 04. project details
		config.getAttributeConfigs().put("ProjectRID", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("ProjectUNID", VrhAttributeConfigFactory.createAttributeConfig());

		// 05. references
		config.getAttributeConfigs().put("ProjectParticipantUNID", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("ToolInstanceUNID", VrhAttributeConfigFactory.createAttributeConfig());

		return config;
	}

}
