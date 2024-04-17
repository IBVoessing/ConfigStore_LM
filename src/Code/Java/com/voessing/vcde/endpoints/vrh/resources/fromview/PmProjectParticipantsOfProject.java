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

public class PmProjectParticipantsOfProject extends VrhDominoNotesDocumentHandler {

	private String urlParamEmailAdress = ""; // will be set in onRequest() and used in onSkipDocument();

	/*-
	@Override
	protected String configSearchQueryServerConstraint(HttpServletRequest request) throws Exception {
		// ### always reduce response to the given email address or project and deny all requests w/o project specification
	
		// String emailAddress = getRequestParamValue(request, "emailaddress");
		// if (emailAddress != null && !emailAddress.isEmpty()) {
		// return "@Contains(@LowerCase(emailAddress); @LowerCase('" + emailAddress + "'))";
		// }
	
		String rid = getRequestParamValue(request, "ProjectRID");
		if (rid == null || rid.isEmpty()) {
			// throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectRID' was not given in request");
		}
	
		String uid = getRequestParamValue(request, "ProjectUNID");
		if (uid == null || uid.isEmpty()) {
			// throw new VrhException(SC_UNPROCESSABLE_CONTENT, "parameter 'ProjectUNID' was not given in request");
		}
	
		// return "(ProjectRID=\"" + rid + "\") & (ProjectUNID=\"" + uid + "\")";
		return "";
	}
	*/

	@Override
	protected void onBeforeSave(Document document, boolean isNewDocument, Map<String, String[]> parameterMap, Map<String, ?> payload) throws Exception {
		super.onBeforeSave(document, isNewDocument, parameterMap, payload);

		// ### constraint checks and detail updates only when not softdeleting
		if (!(document.getItemValueInteger("$isSoftdeleted") >= 1)) {
			// check for unique email addresses
			VCDEShared.ppCheckUniqueEmailAddresses(document);

			// update project details, if entity instance is assigned to a project
			if (!document.getItemValueString("ProjectRID").isEmpty() && !document.getItemValueString("ProjectUNID").isEmpty()) {
				VCDEShared.enrichDocumentWithProjectDetails(document, getSession(), document.getItemValueString("ProjectRID"), document.getItemValueString("ProjectUNID"));
			}
		}

		// ### maintenance "Originator" (when creating document) and "Reviser" (when editing existent document)
		if (isNewDocument) {
			document.replaceItemValue("Originator", "frontend");
		} else {
			document.replaceItemValue("Reviser", "frontend");
		}
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws VrhException {
		urlParamEmailAdress = getRequestParameterValue(request, "emailaddress");
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

		// ### resource config
		config.setFormName("ProjectParticipant");
		config.setSearchQueryServerConstraint("$isSoftdeleted <> 1"); // redundant to onFilterDocument(), but may improve performance (keep this server constraint and onFilterDocument() in sync!)

		// view configuration
		config.setJsonViewConfig(new VrhJsonViewConfig("(vrh)\\endpoints\\PmProjectParticipantsOfProject") // define view
			.asDocumentCollection() // easy looping
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectRID")) // add project rid or nothing
			.addKey(getRequestParameterValueNullable(parameterMap, "ProjectUNID")) // add project unid or nothing
		);

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all
		config.getAttributeConfigs().put("notesUrl", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_NOTESURL));

		// 02. payload
		config.getAttributeConfigs().put("address", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("company", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("emailAddress", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("emailAddressOther", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue(",")); // served as string, thus we implicitly split the incoming string into multi values
		config.getAttributeConfigs().put("firstName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("isNonOperational", VrhAttributeConfigFactory.createAttributeConfig().withDefaultValue(Optional.of(0)).withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("lastName", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("name", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("phone", VrhAttributeConfigFactory.createAttributeConfig().withInitialValue(Optional.of("")));
		config.getAttributeConfigs().put("phoneOther", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue(",")); // served as string, thus we implicitly split the incoming string into multi values
		config.getAttributeConfigs().put("roles", VrhAttributeConfigFactory.createAttributeConfig().asMultiValue());

		// 03. meta data
		config.getAttributeConfigs().put("$isSoftdeleted", VrhAttributeConfigFactory.createAttributeConfig().withDefaultValue(Optional.of(0)).withInitialValue(Optional.of(0)));
		config.getAttributeConfigs().put("Originator", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());
		config.getAttributeConfigs().put("Reviser", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite());

		// 04. project details
		config.getAttributeConfigs().put("ProjectRID", VrhAttributeConfigFactory.createAttributeConfig());
		config.getAttributeConfigs().put("ProjectUNID", VrhAttributeConfigFactory.createAttributeConfig());

		return config;
	}

}
