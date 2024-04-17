package com.voessing.vcde.endpoints.vrh.resources.fromview;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.vcde.endpoints.vrh.resources.VCDEShared;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfig;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigFactory;
import com.voessing.xapps.utils.vrh.configs.VrhAttributeConfigValueType;
import com.voessing.xapps.utils.vrh.configs.VrhJsonViewConfig;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.handler.VrhDominoNotesDocumentHandler;
import com.voessing.xapps.utils.vrh.helpers.VrhHelper;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class PmProjects extends VrhDominoNotesDocumentHandler {

	// VI Projekte: IBV-Proj.nsf
	// VV Projekte: VEPRO-Pr.nsf
	// VP Projekte: IBVPL-Proj.nsf
	// VI-ARGEN Projekte: IBVArgen2.nsf

	final static Map<String, String> MANDANT_DBNAMES = Stream.of(new String[][] {
		{
			"vi",
			"IBV-Proj.nsf" },
		{
			"vv",
			"VEPRO-Pr.nsf" },
		{
			"vp",
			"IBVPL-Proj.nsf" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
	final static String PARAMNAME_MANDANT = "$mandant";

	String currentMandant = "";
	String currentMandantDbName = "";

	@Override
	protected boolean onGetAttribute(Document document, String attributeName, VrhAttributeConfig attributeConfig, JsonJavaObject responseObject) {
		switch (attributeName) {
		case "$mandant":
			// calculate the $mandant attribute (which is need in the frontend) by request param (see onRequest())
			responseObject.putString(attributeName, this.currentMandant);
			return true;

		default:
			return false;
		}
	}

	@Override
	protected void onRequest(HttpServletRequest request) throws VrhException {
		if (!hasRequestParam(request, PARAMNAME_MANDANT)) {
			throw new VrhException(HttpServletResponse.SC_BAD_REQUEST, "missing query param " + PARAMNAME_MANDANT);
		}

		this.currentMandant = "";

		// if (!MANDANT_DBNAMES.containsKey(getRequestParamValue(request, PARAMNAME_MANDANT).trim())) {
		if (!VrhHelper.mapHasKeyIgnoreCase(MANDANT_DBNAMES, getRequestParameterValue(request, PARAMNAME_MANDANT).trim())) {
			throw new VrhException(HttpServletResponse.SC_BAD_REQUEST, "unsupported value for request param " + PARAMNAME_MANDANT + " (" + getRequestParameterValue(request, PARAMNAME_MANDANT).trim() + ")");
		}

		// this.currentMandant = getRequestParamValue(request, PARAMNAME_MANDANT).trim();
		this.currentMandant = VrhHelper.mapGetKeyIgnoreCase(MANDANT_DBNAMES, getRequestParameterValue(request, PARAMNAME_MANDANT).trim());
		// this.currentMandantDbName = MANDANT_DBNAMES.get(this.currentMandant);
		this.currentMandantDbName = VrhHelper.mapGetValueIgnoreCase(MANDANT_DBNAMES, this.currentMandant);
	}

	@Override
	protected boolean onSkipDocument(Document document) throws NotesException {
		// Keep filter expression in sync with server constraint (which is redundant, but may improve performance)!
		return super.onSkipDocument(document) // inherited hide
			|| (!document.getItemValueString("AbgeschlossenGesamt").isEmpty()) // hide abgeschlossen;
			|| (!document.getItemValueString("IsHauptPB").equalsIgnoreCase("1")); // hide other PB
	}

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig config, Map<String, String[]> parameterMap) {
		// ### endpoint config
		config.setAcceptMissingFormOnSingleResourceRequest(true);
		config.setAllowedMethods("GET, OPTIONS");
		config.getAllowedOriginsForAccessControl().addAll(VCDEShared.allowedOriginsForAccessControl);
		config.withoutConcurrencyControl();

		// ### resource config
		config.setDatabaseName(this.currentMandantDbName); // depends on query param $mandant
		config.setDatabaseServerName("ibvdno03");
		config.setFormName("Projektblatt");
		config.setJsonViewConfig(new VrhJsonViewConfig("(VCDEProjektauswahlJSON)", 1).withKeys(new Vector<Object>(Arrays.asList(new String[] {
			"Projektblatt" })), true)); // get resource collection by keyed view
		config.setSearchQueryServerConstraint("IsHauptPB=\"1\" & AbgeschlossenGesamt=\"\"");

		// ### attribute config

		// 01. generic fields
		config.getAttributeConfigs().put(getRequestParamNameId(), null); // ID param does not need any configuration at all

		// 02. payload
		config.getAttributeConfigs().put("$mandant", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // calculated attribute, needed in frontend
		config.getAttributeConfigs().put("AbgeschlossenGesamt", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // calculated attribute, needed in frontend
		config.getAttributeConfigs().put("IsHauptPB", VrhAttributeConfigFactory.createAttributeConfig().asIgnoreOnWrite()); // calculated attribute, needed in frontend
		config.getAttributeConfigs().put("ProjektNr", VrhAttributeConfigFactory.createAttributeConfig().fromItem("Projektnummer").withDefaultValue(Optional.of("(N.V.)"))); // TODO: should be required => consolidate data
		config.getAttributeConfigs().put("ProjektTitel", VrhAttributeConfigFactory.createAttributeConfig().fromItem("Projektname").withDefaultValue(Optional.of("(N.V.)"))); // TODO: should be required => consolidate data
		config.getAttributeConfigs().put("SYNC_SOURCEDBREPLICAID", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_RID)); // TODO: should be required => consolidate data
		config.getAttributeConfigs().put("SYNC_SOURCEUNID", VrhAttributeConfigFactory.createAttributeConfig(VrhAttributeConfigValueType.MAVT_UNID)); // TODO: should be required => consolidate data

		return config;
	}

}