package com.voessing.ccwidget;

import java.util.Date;


import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.voessing.common.TDateTimeUtil;
import com.voessing.common.TJsonObjectsLoader;
import com.voessing.common.TNotesUtil;

import lotus.domino.ACL;
import lotus.domino.Agent;
import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.Item;
import lotus.domino.DocumentCollection;
import lotus.domino.View;

public class WidgetConfig {

	private static final String WIDGET_LOOKUP_VIEW = "(LookupToolInstanceByID)";
	
	//Methode unterscheidet je nach Access-Level, ob die Aktualisierung des lastAccessed-Feldes ermöglicht wird
	public static JsonJavaObject getDataForWidgetId(Database db, String widgetId) throws Exception {

		if (db.getCurrentAccessLevel() < ACL.LEVEL_EDITOR) {
			
			TJsonObjectsLoader cfgLoader = new TJsonObjectsLoader();
			//cfgLoader.setIgnoreJsonException(true);
			
			JsonJavaObject json = cfgLoader.getObject(db, WIDGET_LOOKUP_VIEW, widgetId, 1);
			
			//if (json!=null) System.out.println("Config: " + json.toString());
		
			return json;

		} else return getDataForWidgetIdv2(db, widgetId);
	}

	private static JsonJavaObject getDataForWidgetIdv2(Database db, String widgetId) throws Exception {

		View luView = null;
		Document doc = null;
		DateTime dt = null;
		DateTime savedDt = null;
		Item hlp = null;
		
		try {
			
			luView = db.getView(WIDGET_LOOKUP_VIEW);
			luView.setAutoUpdate(false);
			luView.refresh();

			JsonJavaFactory factory = JsonJavaFactory.instanceEx;
			
			JsonJavaObject json = null;
			
			doc = luView.getDocumentByKey(widgetId, true);
			if (doc!=null) {
				
				try {
					json = (JsonJavaObject) JsonParser.fromJson(factory, doc.getItemValueString("ConfigJSON"));
				} catch (JsonException e) {
					//do nothing
				}
				
				dt = db.getParent().createDateTime(new Date());
				dt.setAnyTime();
				
				boolean needUpdate = false;
				
				hlp = doc.getFirstItem("lastAccessed");
				
				if ((hlp!=null) && (hlp.getType() == Item.DATETIMES)) {
					savedDt = hlp.getDateTimeValue();
					needUpdate = (!savedDt.getDateOnly().equals(dt.getDateOnly()));
				} else needUpdate = true;
				
				
				//nur 1x am Tag speichern
				if (needUpdate) {
					doc.replaceItemValue("lastAccessed", dt);
					doc.save(true, false);
				}

			}
			
			return json;
			
		} finally {
			TNotesUtil.recycleNotesObject(savedDt, dt, hlp, doc, luView);
		}
		
	}

	
	
	
	public static JsonJavaArray getAllWidgetConfigs(Database db) throws Exception {
		
		//TJsonObjectsLoader cfgLoader = new TJsonObjectsLoader();
		//return cfgLoader.getObjects(db, WIDGET_LOOKUP_VIEW, "AllConfigs", 1);
		
		View luView = null;
		DocumentCollection dc = null;
		Document doc = null;
		Document nextDoc = null;
		DateTime savedDt = null;
		Item hlp = null;
		
		try {
			
			luView = db.getView(WIDGET_LOOKUP_VIEW);
			luView.setAutoUpdate(false);
			luView.refresh();

			JsonJavaFactory factory = JsonJavaFactory.instanceEx;
			
			JsonJavaArray jArr = new JsonJavaArray();
			
			dc = luView.getAllDocumentsByKey("AllConfigs", true);
			
			doc = dc.getFirstDocument();
			while (doc!=null) {
				
				JsonJavaObject json = new JsonJavaObject();
				
				json.put("resourceId", doc.getItemValueString("resourceid"));
				
				//2022-10-21, dko: fix Leer-Item-Access. Fallback ist dann lastUpdated
				hlp = doc.getFirstItem("lastUpdated");
				if ((hlp!=null) && (hlp.getType() == Item.DATETIMES)) {
					json.put("lastUpdated", TDateTimeUtil.toISOOffsetDateTime(hlp.getDateTimeValue().toJavaDate()));
				} else {
					json.put("lastUpdated", TDateTimeUtil.toISOOffsetDateTime(doc.getLastModified().toJavaDate()));
				}
				TNotesUtil.recycleNotesObject(hlp);
				
				//Lastaccess ohne Fallback
				hlp = doc.getFirstItem("lastAccessed");
				if ((hlp!=null) && (hlp.getType() == Item.DATETIMES)) {
					
					//System.out.println("item found");
					savedDt = hlp.getDateTimeValue();
					//System.out.println(savedDt);
					//System.out.println(savedDt.toJavaDate());
					
					json.put("lastAccessed", TDateTimeUtil.toISOOffsetDateTime(savedDt.toJavaDate()));
					savedDt.recycle();
					hlp.recycle();
				}
				
				try {
					JsonJavaObject jsonCfg = (JsonJavaObject) JsonParser.fromJson(factory, doc.getItemValueString("ConfigJSON"));
					json.put("config", jsonCfg);
				} catch (JsonException e) {
					//do nothing
				}
				
				jArr.add(json);
				
				nextDoc = dc.getNextDocument(doc);
				doc.recycle();
				doc = nextDoc;
			}
			
			return jArr;
			
		} finally {
			TNotesUtil.recycleNotesObject(hlp, doc, nextDoc, dc, luView);
		}
		
	}


	
	public static void saveConfig(Database db, String communityId, String widgetId, JsonJavaObject json) throws Exception {
		
		View vwRes = null;
		Document doc = null;
		Agent lsAgent = null;
		DateTime dt = null;
		
		try {
			
			vwRes = db.getView(WIDGET_LOOKUP_VIEW);
			
			doc = vwRes.getDocumentByKey(widgetId);
			
			if (doc==null) {
				doc = db.createDocument();
				doc.replaceItemValue("Form", "ToolInstance");
				doc.replaceItemValue("Originator", "VCDE node.js");
				doc.replaceItemValue("objectId", widgetId);
				doc.replaceItemValue("communityid", communityId);
			}
			
			doc.replaceItemValue("ConfigJSON", json.toString());
			
			
			dt = db.getParent().createDateTime(new Date());
			doc.replaceItemValue("lastUpdated", dt);
			
			dt.setAnyTime();
			doc.replaceItemValue("lastAccessed", dt);
			doc.replaceItemValue("Reviser", "VCDE node.js");
			
			
			doc.save(true, false);
			
			//run the agent with in-memory document as context
			//Agent-Ausfürhung ist optional, daher werden hier keine Exitcodes gesetzt/geprüft
			lsAgent = db.getAgent("(XSP-DecodeJSON)");
			if (lsAgent!=null) lsAgent.runWithDocumentContext(doc);
			
		} finally {
			TNotesUtil.recycleNotesObject(dt, lsAgent, doc, vwRes);
		}

		//TODO: Das ganze Acknowledge-Zeugs inkl. Agent für Files anpassen
		/*

		var id:String = viewScope.get("view_widgetInstanceId");
		var url:String = viewScope.get("view_targetUrl");
		
		var doc:NotesDocument;
		var vw:NotesView;
		
		//lookup doc by key resourceID
		vw = database.getView("(LookupResourceByID)");
		vw.refresh();
		
		doc = vw.getDocumentByKey(id, true);
		
		if (doc==null) {
			doc = database.createDocument();
			doc.replaceItemValue("Form", "Resource");
		}
		
		doc.replaceItemValue("resourceid", id);
		doc.replaceItemValue("url", url);
		
		if (database.queryAccessRoles(session.getEffectiveUserName()).contains("[Administration]")) {
			doc.replaceItemValue("ack", "1");
			doc.save();
		} else {
		
			doc.save();
		
			//Mail to user with administration role // support@pms.voessing.de
			var mDoc:NotesDocument;
			mDoc = database.createDocument();
			mDoc.replaceItemValue("Form", "Memo");
			mDoc.replaceItemValue("SendTo", "support@pms.voessing.de");
			mDoc.replaceItemValue("ReplyTo", viewScope.get("userData_email"));
			mDoc.replaceItemValue("Subject", "Widget-Freischaltung angefordert von " + session.getEffectiveUserName());
			
			var rt:NotesRichTextItem;

			rt = mDoc.createRichTextItem("Body");

			rt.appendText("1) user");
			rt.addNewLine();
			
			rt.appendText("userId: " + viewScope.get("userData_userId"));
			rt.addNewLine();
			
			rt.appendText("orgId: " + viewScope.get("userData_orgId"));
			rt.addNewLine();
			
			rt.appendText("displayName: " + viewScope.get("userData_displayName"));
			rt.addNewLine();
			
			rt.appendText("email: " + viewScope.get("userData_email"));
			rt.addNewLine(3);
			
			
			rt.appendText("2) source");
			rt.addNewLine();
			
			rt.appendText("resourceId: " + viewScope.get("sourceData_resourceId"));
			rt.addNewLine();
			
			rt.appendText("resourceName: " + viewScope.get("sourceData_resourceName"));
			rt.addNewLine();
			
			rt.appendText("resourceType: " + viewScope.get("sourceData_resourceType"));
			rt.addNewLine();
			
			rt.appendText("widgetInstanceId: " + viewScope.get("sourceData_widgetInstanceId"));
			rt.addNewLine();
			
			rt.appendText("orgId: " + viewScope.get("sourceData_orgId"));
			rt.addNewLine(3);
			

			rt.appendText("3) target");
			rt.addNewLine();
			rt.appendText("url: " + viewScope.get("view_targetUrl"));
			rt.addNewLine(3);
			
			//Link
			rt.appendText("Click here to approve widget");
			rt.addNewLine();
			
			rt.appendText(context.getUrl().getAddress().replace(view.getPageName(), '') + "/widgetApprove?OpenAgent&unid=" + doc.getUniversalID());
			
			mDoc.send(false);
			
		}
		
		viewScope.put("view_loadConfig", false);
*/		
		
	}
	

}