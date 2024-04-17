package com.voessing.api;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.webapp.XspHttpServletResponse;
import com.voessing.common.TJsonRestProvider;

public class TApiEventGenerator {

	public static enum SEV {
		NONE, LOW, MEDIUM, HIGH, CRITICAL;
	}
	
	public static enum TYP {
		NOTE, WARNING, ERROR;
	}
	
	public static enum Event {

		API_UNAUTHORIZED			(TYP.ERROR, SEV.HIGH, "Access", "You are not authorized to perform this operation", XspHttpServletResponse.SC_UNAUTHORIZED),
		API_METHOD_NOT_ALLOWED		(TYP.ERROR, SEV.HIGH, "API", "This method is not implemented", XspHttpServletResponse.SC_METHOD_NOT_ALLOWED),
		
		APICMD_NO_POSTDATA			(TYP.ERROR, SEV.HIGH, "API", "No POST data", XspHttpServletResponse.SC_BAD_REQUEST),
		
		//Exceptions
		NOTES_EXCEPTION				(TYP.ERROR, SEV.HIGH, "Notes", "An error occured while accessing Domino database objects"),
		GENERAL_EXCEPTION			(TYP.ERROR, SEV.HIGH, "Java", "A general error occured"),
		LS_EXCEPTION				(TYP.ERROR, SEV.HIGH, "LS", "An error occured during processing a LotusScript agent"),
		
		//API
		API_NO_RESPONSEDATA			(TYP.WARNING, SEV.MEDIUM, "API", "Unable to generate response data for this operation"),
		
		API_NOT_IMPLEMENTED			(TYP.ERROR, SEV.HIGH, "API", "This API endpoint is not implemented", XspHttpServletResponse.SC_NOT_FOUND),
		APICMD_NOT_IMPLEMENTED		(TYP.ERROR, SEV.HIGH, "API", "This API command is not implemented", XspHttpServletResponse.SC_NOT_FOUND),

		APICMD_PARAM_MISSING		(TYP.ERROR, SEV.HIGH, "API", "A required parameter is missing", XspHttpServletResponse.SC_BAD_REQUEST),
		APICMD_PARAM_WRONGVALUE		(TYP.ERROR, SEV.HIGH, "API", "A parameter value is out of bounds or missing", XspHttpServletResponse.SC_BAD_REQUEST);

		private int responseCode = XspHttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		private SEV severity;
		private TYP type;
		private String category;
		private String message;

		private Event(TYP type, SEV severity, String category, String message)
		{
			this.type = type;
			this.severity= severity;
			this.category = category;
			this.message = message;
		}
		
		private Event(TYP type, SEV severity, String category, String message, int responseCode) {
			this.type = type;
			this.severity= severity;
			this.category = category;
			this.message = message;
			this.responseCode = responseCode;
		}
		
		public String getString() {
			return "["+category+", "+type.name()+"/" + severity.name() + ", " + responseCode + "] " + name() + " - " + message;
		}

		SEV getSeverity() {
			return this.severity;
		}
		
		int getresponseCode() {
			return this.responseCode;
		}

	}
	
	static JsonJavaObject generate(Event x) {
		return TJsonRestProvider.generateJSONError(x.name(), x.type.name(), x.category, x.severity.name(), x.message, x.responseCode);
	}

	static JsonJavaObject generate(Event x, String additionalInfo) {
		if (additionalInfo.equals("")) {
			return TJsonRestProvider.generateJSONError(x.name(), x.type.name(), x.category, x.severity.name(), x.message, x.responseCode);
		} else {
			String special = (x.message.equals("")) ? "" : " ";
			return TJsonRestProvider.generateJSONError(x.name(), x.type.name(), x.category, x.severity.name(), x.message + special + additionalInfo, x.responseCode);
		}
	}

	
}
