package com.voessing.common;

import java.util.ArrayList;
import java.util.Vector;

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.xsp.module.nsf.NotesContext;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import lotus.domino.ViewNavigator;

/*
 * analog SSApp: diese Klasse bietet Zugriff Objekte: primär zunächst via Lookup-Ansichten, die JSON-Objekte zurückliefern
 * benötigt für: z.B. Lookup der Portal Mitarbeiter Objekte in der JSON-Aufbereitung der Notesdaten
 * Ziel: einheitlich geregelter / fehlerbehandelter Zugriff auf Objekte
 * ohne cache-Funktionen als static access
 */
public class TJsonObjectsLoader {


	//Default = true: Objekte ingnorieren, wenn deren JSON-Konvertierung fehlschlägt
	private boolean option_ignore_jsonerrors = true;

	//Default = true: Nachschlagen Exakt (Default) oder mit Teilstring-Match
	private boolean option_lookup_exactMatch = true;
	
	//Default = keine: Die Session, die für Lookups verwendet werden soll (beim Öffnen von Datenbanken)
	private Session option_session_to_use = null;
	
	public enum Source {
		KONTAKTDB_PERSONS("Kontaktdatenbank.nsf", "(LookUpMultiJSON2)", 2),
		SSAPP_USERNOTIFICATIONCONFIG("SelfserviceUserData.nsf", "(LookUpCustomNotificationConfigMulti)", 1),
		ACTLOG_CATEGORIES("IBVActivityLogging.nsf", "(LookUpJSONCategoriesById)", 1);
		
		private String strDb;
		private String strView;
		private int intColIdx;
		
		private Source(String dbName, String viewName, int colIdx) {
			strDb = dbName;
			strView = viewName;
			intColIdx = colIdx;
		}
		
		public String getDatabaseName() {return strDb;};
		public String getViewName() {return strView;};
		public int getColIdx() {return intColIdx;};
	}
	
	/**
	 * 
	 * @return returns an ArrayList containing a null value to trigger the retrival of all objects in core function
	 */
	private ArrayList<String> getNullEntryList(){
		ArrayList<String> empty = new ArrayList<String>();
		empty.add(null);
		return empty;
	}
	
	private ArrayList<String> keyToKeyList(String key){
		ArrayList<String> keys = new ArrayList<String>();
		keys.add(key);
		return keys;
	}
	
	public void setIgnoreJsonException(boolean ignore) {
		option_ignore_jsonerrors = ignore;
	}

	public void setExactMatch(boolean exactMatch) {
		option_lookup_exactMatch = exactMatch;
	}
	
	public void setUseSession(Session useSession) {
		option_session_to_use = useSession;
	}

	/**
	 *  Getting all entries of a categorized view may result in unexpected behaviour (e.g. same Objects being listed x times like in the view
	 * @param ServerName Name of the Server where the Database is stored
	 * @param DatabaseName Name of the Database to fetch the view from
	 * @param ViewName Name of the View to be used 
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view
	 * @throws Exception
	 */
	public ArrayList<JsonJavaObject> getAllObjects(String ServerName, String DatabaseName, String ViewName, int ColIdx) throws Exception {	
		return getObjects(ServerName, DatabaseName, ViewName, getNullEntryList(), ColIdx);
	}
	/**
	 * Getting all entries of a categorized view may result in unexpected behaviour (e.g. same Objects being listed x times like in the view
	 * @param DB Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view
	 * @throws Exception
	 */
	public ArrayList<JsonJavaObject> getAllObjects(Database DB, String ViewName, int ColIdx) throws Exception {
		return getObjects(DB, ViewName, getNullEntryList(), ColIdx);
	}
	/**
	 * @param ServerName ServerName Name of the Server where the Database is stored
	 * @param DatabaseName Name of the Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Key Key to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view that match the Key
	 * @throws Exception
	 */
	//Wrapper, mit Benutzerrechten arbeiten
	public ArrayList<JsonJavaObject> getObjects(String ServerName, String DatabaseName, String ViewName, String Key, int ColIdx) throws Exception {
		return getObjects(ServerName, DatabaseName, ViewName, keyToKeyList(Key), ColIdx);
	}
	
	/**
	 * @param ServerName ServerName Name of the Server where the Database is stored
	 * @param DatabaseName Name of the Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Keys Keys to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view that match the Keys
	 * @throws Exception
	 */
	//Wrapper, mit Benutzerrechten arbeiten
	public ArrayList<JsonJavaObject> getObjects(String ServerName, String DatabaseName, String ViewName, ArrayList<String> Keys, int ColIdx) throws Exception {
		
		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
		Database currentDB = nct.getCurrentDatabase();
		
		if (ServerName==null || ServerName.isEmpty()) ServerName = currentDB.getServer();
		
		if (option_session_to_use==null && currentDB.getServer().toLowerCase().equalsIgnoreCase(ServerName) && currentDB.getFilePath().toLowerCase().equalsIgnoreCase(DatabaseName)) {
			//aktuelle DB nutzen, nicht neu ?ffnen
			return getObjects(currentDB, ViewName, Keys, ColIdx);
		} else {
			//neues DB-Objekt erstellen (DB ?ffnen)
			Database DB = null;
			
			try {

				//Kein Zugriff auf eine existierende Datenbank: Abbruch, aber silent (ist kein Fehler, leere Menge zurückgeben ist hier richtig!)
				//-> der Versuch auf nicht existierende DB zuzugreifen fällt nicht hierunter - Exception wird in der Core-Routine ausgelöst
				try {
					if (option_session_to_use!=null) {
						//System.out.println("Current Session: " + session.getEffectiveUserName());
						//System.out.println("Session to use: " + option_session_to_use.getEffectiveUserName());						
						DB = option_session_to_use.getDatabase(ServerName, DatabaseName);
					} else {
						DB = session.getDatabase(ServerName, DatabaseName);
					}
				} catch (NotesException e) {
					//silent, only log to console
					System.out.println("Informational: " + ServerName + "!!" + DatabaseName + ", " + e.getLocalizedMessage());
					return new ArrayList<JsonJavaObject>();
				}

				return getObjects(DB, ViewName, Keys, ColIdx);
				
			} finally {
				TNotesUtil.recycleNotesObject(DB);
			}
			
		}
	}
	
	/**
	 * 
	 * @param DB Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Key Key to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view that match the Key
	 * @throws Exception
	 */
	//Wrapper
	public ArrayList<JsonJavaObject> getObjects(Database DB, String ViewName, String Key, int ColIdx) throws Exception {
		return getObjects(DB, ViewName, keyToKeyList(Key), ColIdx);
	}
	
	/**
	 * 
	 * @param DB Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Keys Keys to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns all objects of the view that match the Keys
	 * @throws Exception
	 */
	public ArrayList<JsonJavaObject> getObjects(Database DB, String ViewName, ArrayList<String> Keys, int ColIdx) throws Exception {
		return getObjectsCore(DB, ViewName, Keys, ColIdx, false);
	}

	/**
	 * 
	 * @param ServerName ServerName Name of the Server where the Database is stored
	 * @param DatabaseName Name of the Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Key Key to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns the first object of the view that matches the Key
	 * @throws Exception
	 */
	//Einzelobjekte-Anforderungen: über core-Routine abdecken
	public JsonJavaObject getObject(String ServerName, String DatabaseName, String ViewName, String Key, int ColIdx) throws Exception {

		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
		Database currentDB = nct.getCurrentDatabase();
		
		if (ServerName==null || ServerName.isEmpty()) ServerName = currentDB.getServer();

		if (option_session_to_use==null && currentDB.getServer().toLowerCase().equalsIgnoreCase(ServerName) && currentDB.getFilePath().toLowerCase().equalsIgnoreCase(DatabaseName)) {
			//aktuelle DB nutzen, nicht neu ?ffnen
			return getObject(currentDB, ViewName, Key, ColIdx);
		} else {
			//neues DB-Objekt erstellen (DB ?ffnen)
			Database DB = null;
			
			try {

				//Kein Zugriff auf eine existierende Datenbank: Abbruch, aber silent (ist kein Fehler, leere Menge zurückgeben ist hier richtig!)
				//-> der Versuch auf nicht existierende DB zuzugreifen fällt nicht hierunter - Exception wird in der Core-Routine ausgelöst
				try {
					
					if (option_session_to_use!=null) {
						//System.out.println("Current Session: " + session.getEffectiveUserName());
						//System.out.println("Session to use: " + option_session_to_use.getEffectiveUserName());						
						DB = option_session_to_use.getDatabase(ServerName, DatabaseName);
					} else {
						DB = session.getDatabase(ServerName, DatabaseName);
					}

					
					DB = session.getDatabase(ServerName, DatabaseName);
				} catch (NotesException e) {
					//silent, only log to console
					System.out.println("Informational: " + ServerName + "!!" + DatabaseName + ", " + e.getLocalizedMessage());
					return new JsonJavaObject();
				}

				return getObject(DB, ViewName, Key, ColIdx);
				
			} finally {
				TNotesUtil.recycleNotesObject(DB);
			}
			
		}
	}

	/**
	 * 
	 * @param DB Database to fetch the view from
	 * @param ViewName Name of the View to be used
	 * @param Key Key to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns the first object of the view that matches the Key
	 * @throws Exception
	 */
	//Einzelobjekte-Anforderungen: über core-Routine abdecken
	public JsonJavaObject getObject(Database DB, String ViewName, String Key, int ColIdx) throws Exception {
		ArrayList<JsonJavaObject> tmp = getObjectsCore(DB, ViewName, keyToKeyList(Key), ColIdx, true);
		return (tmp.size()>0) ? tmp.get(0) : null;
	}
	
	/**
	 * 
	 * @param lookupView View to be used for the lookup
	 * @param Key Key to me matched against the first column values (column has to be sorted)
	 * @param ColIdx Index of which column where the JSON Strings to be parsed are located in
	 * @return returns the first object of the view that matches the Key
	 * @throws Exception
	 */
	//Einzelobjekte-Anforderungen: über core-Routine abdecken
	public JsonJavaObject getObject(View lookupView, String Key, int ColIdx) throws Exception {
		ArrayList<JsonJavaObject> tmp = getObjectsCore(lookupView, keyToKeyList(Key), ColIdx, true);
		return (tmp.size()>0) ? tmp.get(0) : null;
	}

	private ArrayList<JsonJavaObject> getObjectsCore(View luView, ArrayList<String> Keys, int ColIdx, boolean singleValue) throws Exception {
		
		ArrayList<JsonJavaObject> JSONObjects = new ArrayList<JsonJavaObject>();
		
		ViewEntry entry = null;
		ViewEntry tempEntry = null;
		ViewNavigator vNav = null;
		ViewEntryCollection vec = null;

		try {
			
			//DB must be open

			JsonJavaFactory factory = JsonJavaFactory.instanceEx;
			
			int jsonErrorsLogged = 0;
			
			int cacheSize = (singleValue) ? 0 : 64;

			for (String key: Keys) {
				//key == null is all entries operation mode which is handled in else and works for categorized views
				if (luView.isCategorized() && key != null) {
					
					//2021-09-03, dko: option_lookup_exactMatch - bei categorized Views wird das so dann nicht mehr gehen
					if (!option_lookup_exactMatch) {
						throw new Exception("getObjectsCore(): cannot perform partial match on categorized view");
					}
						
					vNav = luView.createViewNavFromCategory(key, cacheSize);
					//VN_ENTRYOPT_NOCOUNTDATA we are not interested in the number of children, we can go a little faster
					vNav.setEntryOptions(ViewNavigator.VN_ENTRYOPT_NOCOUNTDATA);
					entry = vNav.getFirst();
					
					//2016-12-01, dko/rhi: Bug-Fix, siehe http://www-01.ibm.com/support/docview.wss?uid=swg1LO82679
					if (entry != null && (!entry.isDocument())) {
						System.err.println("getObjectsCore(): entry is NO Document!!!!");
						entry = null;
					}
					
				} else {
					if(key == null){
						//get all entries operation mode
						vec = luView.getAllEntries();
					}else{
						vec = luView.getAllEntriesByKey(key, option_lookup_exactMatch);
					}
					entry = vec.getFirstEntry();
				}
	
				while (entry != null) {
					
					Vector<?> columnValues = entry.getColumnValues();
					String colJson = String.valueOf(columnValues.get(ColIdx));
					JsonJavaObject json = null;
					
					try {
						
						//2016-11-14, dko: avoid errors when parsing empty strings
						if (colJson.length()>0) {
							
							json = (JsonJavaObject) JsonParser.fromJson(factory, colJson);
					
							if (json != null) {
								JSONObjects.add(json);
							}
						}
						
					} catch (JsonException e) {
						jsonErrorsLogged++;
						if (jsonErrorsLogged<=5) {
							//nicht mehr als 5 Fehler protokollieren
							//getparent() -> muss nicht recycled werden, da wir hier keine neue Instanz erzeugen, sondern eine Referenz 
							//auf eine vorhandene bekommen
							TNotesUtil.stdErrorHandler(e, "JsonException: getObjectsCore(): " + 
									"db=" + luView.getParent().getServer() + "!!" + luView.getParent().getFilePath() + ", view=" + luView.getName() + 
									", key: " +	key +
									", exactMatch: " + option_lookup_exactMatch + 
									", entry: " + (entry.isDocument() ? entry.getUniversalID() : "other(no document)") +
									", isValid: " + entry.isValid() + 
									", colIdx: " + ColIdx +
									", colJson: " + colJson);
						}
						
						//ggf. Exception neu auslösen
						if (!option_ignore_jsonerrors) throw e;
					}
	
					//bei Einzelwertabfragen: nicht den nächsten Datensatz suchen, sondern hier abbrechen
					if (singleValue) return JSONObjects;
					
					if (luView.isCategorized() && key != null) {
						tempEntry = vNav.getNext();
					} else {
						tempEntry = vec.getNextEntry();
					}
					
					entry.recycle();
					entry = tempEntry;
				}
				
				//recyclen pro Key (davon unabh?ngig finales recyclen via finally, egal ob Fehler oder normaler Durchlauf)
				TNotesUtil.recycleNotesObject(entry, tempEntry, vec, vNav);
			}
			
			return JSONObjects;
			
		} catch (Exception e) {
			String dbgInfo = "";
			
			try {
				dbgInfo = "DB " + luView.getParent().getServer() + "!!" + luView.getParent().getFilePath();
			} catch (NotesException e1) {
				dbgInfo = "DB undefined";
			}
			
			dbgInfo += "; View: " + luView.getName();
			dbgInfo += "; Keys: " + Keys.toString();
			dbgInfo += "; ColIdx: " + ColIdx;
			
			System.err.println("Exception: getObjectsCore(): " + dbgInfo);
			
			TNotesUtil.stdErrorHandler(e, dbgInfo);
			
			//weiterreichen der Exception nach erfolgter Protokollierung der Details
			throw e;
			
		} finally {
			TNotesUtil.recycleNotesObject(entry, tempEntry, vec, vNav);
		}
		
	}
	
	//CORE: für Multi + Single  
	private ArrayList<JsonJavaObject> getObjectsCore(Database DB, String ViewName, ArrayList<String> Keys, int ColIdx, boolean singleValue) throws Exception {
		
		View luView = null;
		
		try {
			
			luView = DB.getView(ViewName);
			luView.setAutoUpdate(false);
			luView.refresh();

			return getObjectsCore(luView, Keys, ColIdx, singleValue);
			
		} finally {
			TNotesUtil.recycleNotesObject(luView);
		}
		
	}

	//Wrapper für predefined source
	public ArrayList<JsonJavaObject> getObjects(Source predefined, ArrayList<String> Keys) throws Exception {
		return getObjects(null, predefined.strDb, predefined.strView, Keys, predefined.intColIdx);
	}

	public ArrayList<JsonJavaObject> getObjects(Source predefined, String Key) throws Exception {
		return getObjects(null, predefined.strDb, predefined.strView, keyToKeyList(Key), predefined.intColIdx);
	}
	
	public JsonJavaObject getObject(Source predefined, String Key) throws Exception {
		return getObject(null, predefined.strDb, predefined.strView, Key, predefined.intColIdx);
	}
	
}
