package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfig;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;
import com.voessing.xapps.utils.vrh.helpers.VrhHelper;
import com.voessing.xapps.utils.vrh.relations.VrhViewRelation;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class PmToolInstancesOfProject extends VrhDominoNotesDocumentHandler {
	/*-
	@Override
	protected String configSearchQueryServerConstraint(HttpServletRequest request) throws Exception {
		// eventually filter by IDs (needed for (My-Tools-dashlet))
		// TODO: move to onSkipDocument(), if still needed
		String toolInstanceUNIDs = getRequestParamValue(request, "toolinstanceunids");
		if (toolInstanceUNIDs != null && !toolInstanceUNIDs.isEmpty()) {
			return "@Contains(@Text(@DocumentUniqueID); " + String.join(":", toolInstanceUNIDs.split(",")) + ")";
		}
	
		return "";
	}
	*/

	// private void enrichToolInstanceWithToolDetails(Document targetDocument, String toolUNID) throws VrhException, NotesException {
	// if (toolUNID.isEmpty()) {
	// throw new VrhException("error when getting project details: empty ToolUNID");
	// }
	//
	// Database sourceDb = getOpenedDatabase(getSession().getDatabase(VCDEShared.PMTOOLS_SERVERNAME, VCDEShared.PMTOOLS_DATABASENAME, false)); // tools db
	// try {
	// Document sourceDocument = sourceDb.getDocumentByUNID(toolUNID); // tool document
	// try {
	// targetDocument.replaceItemValue("ToolName", sourceDocument.getItemValueString("name"));
	// targetDocument.replaceItemValue("ToolPictureUrl", sourceDocument.getItemValueString("pictureUrl"));
	// targetDocument.replaceItemValue("ToolTitle", sourceDocument.getItemValueString("title"));
	// targetDocument.replaceItemValue("ToolType", sourceDocument.getItemValueString("type"));
	// } finally {
	// VrhDominoHelper.recycle(sourceDocument);
	// }
	// } finally {
	// VrhDominoHelper.recycle(sourceDb);
	// }
	// }

	@Override
	protected void onBeforeChange(Document document, boolean isNewDocument, Map<String, String[]> parameterMap, Map<String, ?> payload) throws Exception {
		super.onBeforeChange(document, isNewDocument, parameterMap, payload);

		// check childs on softdelete
		if (VrhHelper.mapHasKeyIgnoreCase(payload, "$isSoftdeleted") && String.valueOf(((Double) payload.get("$isSoftdeleted")).intValue()).equalsIgnoreCase("1")) {
			checkChilds(document, "Das Werkzeug kann nicht gelöscht werden, da mit im noch Projektbeteiligte verbunden sind.");
		}
	}

	@Override
	protected void onBeforeSave(Document document, boolean isNewDocument, Map<String, String[]> parameterMap, Map<String, ?> payload) throws Exception {
		super.onBeforeSave(document, isNewDocument, parameterMap, payload);

		// ### update project details, if entity instance is assigned to a project
		if (!document.getItemValueString("ProjectRID").isEmpty() && !document.getItemValueString("ProjectUNID").isEmpty()) {
			VCDEShared.enrichDocumentWithProjectDetails(document, getSession(), document.getItemValueString("ProjectRID"), document.getItemValueString("ProjectUNID"));
		}

		// ### update tool details, if _helpToolUNID is included in payload
		if (VrhHelper.mapHasKeyIgnoreCase(payload, "_helpToolUNID")) {
			VCDEShared.enrichDocumentWithToolDetails(document, payload.get("_helpToolUNID").toString()); // _helpToolUNID is not part of the entities field scheme, just a payload property
		}

		// ### maintenance "Janitor" and "Originator" (when creating document) and "Reviser" (when editing existent document)
		if (isNewDocument) {
			document.replaceItemValue("Janitor", VrhHelper.mapHasKeyIgnoreCase(payload, "Janitor")
				? VrhHelper.mapGetValueIgnoreCase(payload, "Janitor")
				: "frontend");
			document.replaceItemValue("Originator", "frontend");
		} else {
			document.replaceItemValue("Reviser", "frontend");
		}
	}

	@Override
	protected boolean onGetAttribute(Document document, String attributeName, VrhAttributeConfig attributeConfig, JsonJavaObject responseObject) throws NotesException {
		switch (attributeName) {
		case "$$helpAllowAutoForInfo":
		case "$$helpAllowAutoForScope":
		case "$$helpAllowAutoForUrl":
		case "$$helpAllowAutoForUsageTypes":
			// transform multi value field to flag
			responseObject.put(attributeName, !VrhHelper.vectorContainsCaseInsensitive(document.getItemValue("$protectUserEditFor"), attributeName.replace("$$helpAllowAutoFor", "")) // construct field name to search for from attribute name
				? 1
				: 0);
			return true;

		default:
			return false;
		}
	}

	@Override
	protected boolean onSetAttribute(Document document, String attributeName, VrhAttributeConfig attributeConfig, Object attributeValue) throws NotesException {
		switch (attributeName) {
		case "$$helpAllowAutoForInfo":
		case "$$helpAllowAutoForScope":
		case "$$helpAllowAutoForUrl":
		case "$$helpAllowAutoForUsageTypes":
			// transform flag to multi value field
			@SuppressWarnings("unchecked")
			Vector<String> protectedFields = document.getItemValue("$protectUserEditFor");

			protectedFields = Double.valueOf(attributeValue.toString()).intValue() == 1
				? VrhHelper.vectorRemoveAllCaseInsensitive(protectedFields, attributeName.replace("$$helpAllowAutoFor", ""))
				: VrhHelper.vectorAddCaseInsensitive(protectedFields, attributeName.replace("$$helpAllowAutoFor", ""));

			document.replaceItemValue("$protectUserEditFor", protectedFields);
			return true;

		default:
			return false;
		}
	}

	/*
	 * Skip orphan or hidden or softdeleted documents.
	 * 
	 * @see com.voessing.xapps.utils.vrh.handler.VrhDominoResourceHandler#onFilterDocument(lotus.domino.Document)
	 */
	@Override
	protected boolean onSkipDocument(Document document) throws NotesException {
		// Keep filter expression in sync with server constraint (which is redundant, but may improve performance)!
		return super.onSkipDocument(document) // inherited hide
			|| document.getItemValueString("$crawlState").equalsIgnoreCase("orphan") // hide orphans
			|| document.getItemValueInteger("$isHidden") == 1 // hide hidden
			|| document.getItemValueInteger("$isSoftdeleted") == 1; // hide softdeleted
	}

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) throws Exception {
		// ### endpoint config
		config.setAllowedMethods("GET, OPTIONS, PATCH, POST");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);

		// ### resource config
		// config.setDatabaseName("VCDE-Config.nsf");
		// config.setDatabaseServerName("ibvdno03");
		config.setFormName("ToolInstance");
		// DEPRECATED config.setSearchQueryServerConstraint("$isHidden <> 1 & $isSoftdeleted <> 1"); // redundant to onFilterDocument(), but may improve performance (keep this server constraint and onFilterDocument() in sync!)
		config.withConcurrencyControl();

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmToolInstancesOfProject") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectRID")) // add project rid or nothing
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectUNID")) // add project unid or nothing
		);

		// relations
		config.addRelation(new VrhViewRelation(getDatabase(), "(vrh)\\relations\\timOfTi"));

		// ### attribute config
		
		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));
		config.getAttributeConfigs().put("httpUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_HTTPURL));

		// 02. payload
		config.getAttributeConfigs().put("Info", VrhAttributeConfigFactory.createAttributeConfig()); // was ToolInstanceInfo
		config.getAttributeConfigs().put("url", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of(""))); // was: ToolInstanceUrl
		config.getAttributeConfigs().put("scope", VrhAttributeConfigFactory.createAttributeConfig()); // was UsageScope
		config.getAttributeConfigs().put("usageTypes", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue()); // ToolUsageTypes

		// 03. meta data
		config.getAttributeConfigs().put("$crawlState", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("$forbidUserEditFor", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue().withInitialValue(Optional.of(Collections.emptyList())));
		config.getAttributeConfigs().put("$isSoftdeleted", VrhAttributeConfigFactory.createAttributeConfig().withDefaultValue(Optional.of(0)).withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("$isHidden", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite().withDefaultValue(Optional.of(0)));
		config.getAttributeConfigs().put("$protectUserEditFor", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Janitor", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Originator", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Reviser", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());

		// 04. project details
		config.getAttributeConfigs().put("ProjectCID", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectKST", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectPNr", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectRID", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("ProjectTitle", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectUNID", VrhAttributeConfigFactory.createAttributeConfig());

		// platform details
		config.getAttributeConfigs().put("platformSubContextId", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // was: jsonChannelName

		// tool details
		config.getAttributeConfigs().put("ToolName", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // was: ToolID
		// config.getAttributeConfigs().put("ToolPictureUrl", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ToolTitle", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // TODO: should be required => consolidate data
		config.getAttributeConfigs().put("ToolType", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // TODO: should be required => consolidate data

		// pseudo attributes
		config.getAttributeConfigs().put("$$helpAllowAutoForInfo", VrhAttributeConfigFactory.createAttributeConfigCustom().withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("$$helpAllowAutoForScope", VrhAttributeConfigFactory.createAttributeConfigCustom().withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("$$helpAllowAutoForUrl", VrhAttributeConfigFactory.createAttributeConfigCustom().withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("$$helpAllowAutoForUsageTypes", VrhAttributeConfigFactory.createAttributeConfigCustom().withInitialValue(Optional.of(0)));

		return config;
	}

}
