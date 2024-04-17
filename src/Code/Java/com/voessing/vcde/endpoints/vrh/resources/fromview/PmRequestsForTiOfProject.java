package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.backend.VrhBackendOperator;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class PmRequestsForTiOfProject extends VrhDominoNotesDocumentHandler {

	private String urlParamEmailAdress = ""; // will be set in onRequest() and used in onSkipDocument();
	private String urlParamRID = ""; // will eventually set in onResponseAsCollection
	private String urlParamUID = ""; // will eventually set in onResponseAsCollection

	@Override
	protected String configSearchQueryServerConstraint(HttpServletRequest request) throws Exception {
		// ### always reduce response to the given project and deny all requests w/o project specification

		// mandatory filter by project
		return "(ProjectRID=\"" + urlParamRID + "\") & (ProjectUNID=\"" + urlParamRID + "\")";
	}

	@Override
	protected void onBeforeSave(Document document, boolean isNewDocument, Map<String, String[]> parameterMap, Map<String, ?> payload) throws Exception {
		super.onBeforeSave(document, isNewDocument, parameterMap, payload);

		// ### update details

		// update project details, if entity instance is assigned to a project
		if (!document.getItemValueString("ProjectRID").isEmpty() && !document.getItemValueString("ProjectUNID").isEmpty()) {
			VCDEShared.enrichDocumentWithProjectDetails(document, getSession(), document.getItemValueString("ProjectRID"), document.getItemValueString("ProjectUNID"));
		}

		// ### update tool details, if entity instance is assigned to a tool
		if (!document.getItemValueString("ToolUNID").isEmpty()) {
			VCDEShared.enrichDocumentWithToolDetails(document, document.getItemValueString("ToolUNID"));
		}

		// ### maintenance "Janitor" and "Originator" (when creating document) and "Reviser" (when editing existent document)
		if (isNewDocument) {
			document.replaceItemValue("Janitor", "frontend");
			document.replaceItemValue("Originator", "frontend");
		} else {
			document.replaceItemValue("Reviser", "frontend");
		}

		// ### create tool instance and enrich details
		// TODO
		@SuppressWarnings("serial")
		JsonJavaObject ti = VrhBackendOperator.createResourceItem(PmToolInstancesOfProject.class, null, new HashMap<String, Object>() {
			{
				// project
				put("ProjectRID", document.getItemValueString("ProjectRID"));
				put("ProjectUNID", document.getItemValueString("ProjectUNID"));

				// tool
				put("_helpToolUNID", document.getItemValueString("ToolUNID"));

				// payload
				put("Info", "Angefordertes Werkzeug: " + document.getItemValueString("tiTitle"));
				put("scope", document.getItemValueString("tiScope"));
				put("usageTypes", document.getItemValue("tiUsageTypes"));

				// meta data
				put("$forbidUserEditFor", new Vector<>(Arrays.asList("Info", "url")));
				put("$$helpAllowAutoForInfo", 1); // let the crawler set attribute
				put("$$helpAllowAutoForUrl", 1); // let the crawler set attribute
				put("Janitor", "crawler"); // let the crawler have control about the new TI
			}
		});

		// enrich details of TI
		document.replaceItemValue("ToolInstanceUNID", ti.getAsString("id"));
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws VrhException {
		// get potential url params
		urlParamEmailAdress = getRequestParameterValue(request, "emailaddress");
		urlParamRID = getRequestParameterValue(request, "ProjectRID");
		urlParamUID = getRequestParameterValue(request, "ProjectUNID");
	}

	@Override
	protected void onResponseAsCollection(HttpServletRequest request) throws VrhException {
		// mandatory project params
		if (urlParamRID == null || urlParamRID.isEmpty()) {
			throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectRID' was not given in request");
		}

		if (urlParamUID == null || urlParamUID.isEmpty()) {
			throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectUNID' was not given in request");
		}
	}

	@Override
	protected boolean onSkipDocument(Document document) throws NotesException {
		// Keep filter expression in sync with server constraint (which is redundant, but may improve performance)!
		return super.onSkipDocument(document) // inherited hide
			// || document.getItemValueString("$crawlState").equalsIgnoreCase("orphan") // hide orphans
			// || document.getItemValueInteger("$isHidden") == 1 // hide hidden
			|| (document.getItemValueInteger("$isSoftdeleted") == 1) // hide softdeleted
			|| (!urlParamEmailAdress.isEmpty() && !urlParamEmailAdress.equalsIgnoreCase(document.getItemValueString("emailAddress"))); // hide with other email adress if given in url
	}

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) {
		// endpoint config
		config.setAllowedMethods("GET, OPTIONS, PATCH, POST");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		config.withConcurrencyControl();

		// ### resource config
		config.setFormName("RequestForTi");
		// config.setSearchQueryServerConstraint("$isSoftdeleted <> 1"); // redundant to onFilterDocument(), but may improve performance (keep this server constraint and onFilterDocument() in sync!)

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmRequestsForTiOfProject") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectRID")) // add project rid or nothing
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectUNID")) // add project unid or nothing
		);

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));

		// 02. payload
		// config.getAttributeConfigs().put("address", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("company", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("emailAddress", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("emailAddressOther", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue(",")); // served as string, thus we implicitly split the incoming string into multi values
		// config.getAttributeConfigs().put("firstName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("isNonOperational", VrhAttributeConfigFactory.createAttributeConfig().withDefaultValue(Optional.of(0)).withInitialValue(Optional.of(0)));
		// config.getAttributeConfigs().put("lastName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("name", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("phone", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		// config.getAttributeConfigs().put("phoneOther", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue(",")); // served as string, thus we implicitly split the incoming string into multi values
		// config.getAttributeConfigs().put("roles", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());
		config.getAttributeConfigs().put("tiDescription", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("tiMembers", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue().withStructuredContent());
		config.getAttributeConfigs().put("tiOwner", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("tiTitle", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("(unbenannt)")));
		config.getAttributeConfigs().put("tiScope", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("tiUsageTypes", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());

		// 03. meta data
		// config.getAttributeConfigs().put("$isSoftdeleted", VrhAttributeConfigFactory.createAttributeConfig().withDefaultValue(Optional.of(0)).withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("Janitor", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Originator", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Reviser", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());

		// 04. project details
		// config.getAttributeConfigs().put("ProjectCID", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		// config.getAttributeConfigs().put("ProjectKST", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		// config.getAttributeConfigs().put("ProjectPNr", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectRID", VrhAttributeConfigFactory.createAttributeConfig());
		// config.getAttributeConfigs().put("ProjectTitle", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ProjectUNID", VrhAttributeConfigFactory.createAttributeConfig());

		// 05. tool details
		config.getAttributeConfigs().put("ToolName", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ToolPictureUrl", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ToolTitle", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ToolType", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("ToolUNID", VrhAttributeConfigFactory.createAttributeConfig());

		// 06. tool instance details
		config.getAttributeConfigs().put("ToolInstanceUNID", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());

		return config;
	}

}
