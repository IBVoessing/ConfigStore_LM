package com.voessing.common;

import java.util.HashMap;
import java.util.LinkedHashMap;

import com.ibm.domino.xsp.module.nsf.NotesContext;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

/*
 * 2021-09-30, dko: 
 * 	- Initial Release, analog Common_Config TAppAccessStore
 * 	- OFFEN: Zugriff erfolgt mit Domino-Userrechten. Damit nicht per anonymous-Xsp-Context verwendbar. Ggf. überlegen, ob wir auch server-context-Zugriuffsebene unterstützen wollen
 * 		- oder ob wir anonyme xsps (per AC angesteuere für Datenabgleiche) nur noch authentifiziert laufen lassen
 * 
 */
/* Klasse ist vollkommen static ausgelegt, sollte im Kontext eines USERS nicht weiter eingesetzt werden */
public class TVAppCredStore {

	private static final String IDX_QUALIFIER = "$$idx$";
	private static HashMap<String,LinkedHashMap<String,String>> credCache = new HashMap<>();
	
	private static Database getCredStoreDb() throws NotesException {
		
		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
		
		return session.getDatabase(session.getCurrentDatabase().getServer(), TGlobalConfig.getSystemVar(TGlobalConfig.APPACCESS_DB));
	}
	
	public static String getValueByName(String key, String propertyName) {
		
		retrieve(key);
		
		if (credCache.containsKey(key.toLowerCase())) {
			
			LinkedHashMap<String, String> values = credCache.get(key.toLowerCase());
			
			if (values.containsKey(propertyName.toLowerCase())) {
				
				return values.get(propertyName.toLowerCase());
				
			} else return "";
			
		} else return "";
		
	}
	
	public static String getValueByNr(String key, int idx) {
		return getValueByName(key, IDX_QUALIFIER + idx);
	}
	
	//Abfrage ausführen
	private static void retrieve(String key) {
		
		if (!credCache.containsKey(key.toLowerCase())) {
			
			Database db = null;
			View v = null;
			Document doc = null;
			
			try {
				
				db = getCredStoreDb();
				
				v = db.getView("(LookupAppAccess)");
				v.refresh();
				
				doc = v.getDocumentByKey(key, true);
				
				LinkedHashMap<String,String> values = new LinkedHashMap<>();
				
				for (int i=1; i<=10; i++) {
					String propName = doc.getItemValueString("PropertyName_" + i).trim();
					String propValue = doc.getItemValueString("PropertyValue_" + i);
					
					if (propName.isEmpty()) propName = "$property" + i;
					
					values.put(propName.toLowerCase(), propValue);
					
					//für Zugriff per Index, einen speziellen Bezeichner zusätzlich verwenden
					values.put(IDX_QUALIFIER+i, propName);
				}

				credCache.put(key.toLowerCase(), values);
				
			} catch (NotesException e) {
				TNotesUtil.stdErrorHandler(e);
			} finally {
				TNotesUtil.recycleNotesObject(doc, v, db);
			}
			
		}
		
	}
	
}
