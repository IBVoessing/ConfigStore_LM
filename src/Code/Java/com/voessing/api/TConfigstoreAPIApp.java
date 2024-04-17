package com.voessing.api;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.ibm.xsp.webapp.XspHttpServletResponse;
import com.voessing.ccwidget.WidgetConfig;
import com.voessing.common.TJsonRestProvider;
import com.voessing.common.TNotesUtil;
import com.voessing.common.TResponseOutputHandler;

import lotus.domino.Database;
import lotus.domino.Session;

public class TConfigstoreAPIApp extends TJsonRestProvider implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Date objCreated = new Date();
	private String userName = "unknown";
	
	//Constructor - Managed Bean needs a public Constructor without arguments	
	public TConfigstoreAPIApp() {
		
		super("");
		
		this.setLogLevel(1);
		
		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
		
		
		try {
			this.userName = session.getEffectiveUserName();
		} catch (Exception e) {
			//silent
		}
			
		doLog("#### " + this.getClass().getName() + " // " + this.userName + " // " + this.objCreated, 1);

	}
	
	private void sendError(FacesContext context, String apiCommand, int respCode, String respStr) {
		doLog(apiCommand + ": " + respCode + "/" + respStr, 2);
		TResponseOutputHandler.sendHttpError(context, respCode, respStr);
	}

	
	public void API_Call() {

		Session saas = null;
		Database saasDb = null;
		
		//Vorbereitungen
		NotesContext nct = NotesContext.getCurrent();
		
		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();

		//Abgriff der Pfadinfo (nach XPage-Name): / anh?ngen f?r den Fall, dass Seite ohne weitere Pathinfo aufgerufen wurde 
		String pathInfo = externalContext.getRequestPathInfo() + "/dummy";
		String[] pathArray = pathInfo.split("/");

		//API-Command steht immer an Stelle 2 (Index=1)
		String apiCommand = pathArray[1].toUpperCase();

		@SuppressWarnings("unchecked")
		Map<String, Object> requestParams = externalContext.getRequestParameterMap();

		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
		String rqMethod = request.getMethod().toUpperCase();

		String endpoint = externalContext.getRequestServletPath().toUpperCase();
		doLog("API-CALL CS-1: " + rqMethod + " " + endpoint + ": " + pathInfo + ", PARAMS: " + requestParams.toString(), 1);
		
		try {
			
			if (endpoint.equals("/API.XSP")) {

				if (apiCommand.equals("CONFIG")) {
					
					//new Impl.
					
					if (rqMethod.equalsIgnoreCase(HTTP_POST)) {
						//Post
						
						//nur mit richtiger Anmeldung
						if (!this.userName.equalsIgnoreCase("node js")) {
							sendError(context, apiCommand, XspHttpServletResponse.SC_UNAUTHORIZED, "POST requires dedicated user account");
						} else {
							
							String id = extractParamString(requestParams, "id", null);
							if (id==null || id.isEmpty()) throw new TApiException(TApiEventGenerator.Event.APICMD_PARAM_MISSING, "id");
							
							JsonJavaObject postRequestData =  getRequestData(externalContext);
							
							if (postRequestData!=null) {
								
								WidgetConfig.saveConfig(nct.getCurrentDatabase(), "", id, postRequestData);
								
								TResponseOutputHandler.sendHttpStatus(context, XspHttpServletResponse.SC_OK);
								
							} else {
								sendError(context, apiCommand, XspHttpServletResponse.SC_BAD_REQUEST, "No Post Data");
							}
							
						}	//usercheck
						
					} else if (rqMethod.equalsIgnoreCase(HTTP_GET)) {
						//Get
						
						String id = extractParamString(requestParams, "id", null);
						if (id==null || id.isEmpty()) throw new TApiException(TApiEventGenerator.Event.APICMD_PARAM_MISSING, "id");
						
						//2021-04-19, dko: um das LastAccessedDate-Feld zu ändern, brauchen wir Schreibrechte
						//-> entweder via public access arbeiten oder mit einer Server-Sitzung: erstmal Serversitzung versuchen
						
						//Wenn anonym, dann Zugriff mit SessionAsSigner notwendig, oder Server
						saas = Factory.getSession(SessionType.NATIVE);
						saasDb = saas.getDatabase(nct.getCurrentDatabase().getServer(), nct.getCurrentDatabase().getFilePath(), false);
						
						JsonJavaObject json = WidgetConfig.getDataForWidgetId(saasDb, id);
						
						if (json!=null) {
							TResponseOutputHandler.outputJson(context, json);
						} else {
							sendError(context, apiCommand, XspHttpServletResponse.SC_NOT_FOUND, "config not found");
						}
						
					} else {
						sendError(context, apiCommand, XspHttpServletResponse.SC_METHOD_NOT_ALLOWED, rqMethod);
					}
			
				} else if (apiCommand.equals("CONFIGS")) {
					
					//2021-03-29, dko: Alle Konfigurationen ausgeben
					if (rqMethod.equalsIgnoreCase(HTTP_GET)) {
						
						//nur mit richtiger Anmeldung
						if (!this.userName.equalsIgnoreCase("node js")) {
							sendError(context, apiCommand, XspHttpServletResponse.SC_UNAUTHORIZED, "requires dedicated user account");
						} else {
							TResponseOutputHandler.outputJson(context, WidgetConfig.getAllWidgetConfigs(nct.getCurrentDatabase()));
						}
						
					} else {
						sendError(context, apiCommand, XspHttpServletResponse.SC_METHOD_NOT_ALLOWED, rqMethod);
					}
					
				} else {
					sendError(context, apiCommand, XspHttpServletResponse.SC_NOT_FOUND, "Wrong Command");
				}
				
			} else {
				sendError(context, apiCommand, XspHttpServletResponse.SC_NOT_FOUND, "Wrong Endpoint");
			}
			
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
			try {
				sendError(context, apiCommand, XspHttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			} finally {}
		} finally {
			TNotesUtil.recycleNotesObject(saasDb, saas);
		}
		
		
	}
	
}
