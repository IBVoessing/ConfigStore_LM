package com.voessing.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import com.ibm.domino.xsp.module.nsf.NotesContext;

import lotus.domino.Database;
import lotus.domino.Item;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.Document;


/**
 * Konfigurationsdaten aus der config.nsf ermitteln (Server+Systemvariablen)
 * -> analog der LS-Funktionen in Common_Funcs 
 */ 
public class TGlobalConfig {

	//Server-Vars
	public static final String HTTP_ROOT = "HTTP_ROOT";
	public static final String MAIL_DOMAIN = "MAIL_DOMAIN";
	public static final String MAIL_SIG = "MAIL_SIG";
	public static final String TRANSFER_ROOT = "TRANSFER_ROOT";
	public static final String IMAGE_MAGICK_CMD = "IMAGE_MAGICK_CMD" ;
	
	public static final String ADMIN_SERVER  = "ADMIN_SERVER";
	public static final String EXTNAMES_DB = "EXTNAMES_DB";
	public static final String GLOBALSTAMM_DB = "GLOBALSTAMM_DB";
	public static final String KONTAKT_DB = "KONTAKT_DB";
	public static final String GRPNABSYNC_DB = "GRPNABSYNC_DB";
	public static final String CFG_DB = "CFG_DB";
	public static final String CONT_DB = "CONT_DB";
	public static final String RD_DB = "RD_DB";
	public static final String COMMONQUEUE_DB = "COMMONQUEUE_DB";
	public static final String COMMONCONFIG_DB = "COMMONCONFIG_DB";
	public static final String ACTLOG_DB = "ACTLOG_DB";
	public static final String KPI_DB = "KPI_DB";
	public static final String GPR_DB = "GPR_DB";
	public static final String PBU_DB = "PBU_DB";
	public static final String CCAPI_DB = "CCAPI_DB";
	public static final String INV_DB = "INV_DB";
	public static final String SSAPP_CFGBACKUP = "SSAPP_CFGBACKUP";
	public static final String APPACCESS_DB = "APPACCESS_DB";
	public static final String ARWF_DB = "ARWF_DB";
	public static final String VCDE_CFG_DB = "VCDE_CFG_DB";
	
	//zur Vermeidung von Speicher-Leaks sollten Notes-Dbs nicht als statische Objekte gehalten werden, da wir sie nicht kontrolliert recyclen k�nnen 
	private static String SYS_DB = "config.nsf";
	
	//daher: Bei Anforderung einer SYS oder SERVER Eigenschaft werden ALLE (definierten) Eigenschaften des angeforderten Dokuments ausgelesen
	//und in einer HashMap untergebracht. Normalerweise wir damit h�chstens 2x die DB ge�ffnet/Dokumente abgegriffen 
	
	//Sys-Config: hiervon gibt es nur eine
	private static HashMap<String, String> systemConfig = null;
	
	//Server-Config: hier kann es mehrere geben: schachteln: server->(config->value)
	private static HashMap<String, HashMap<String, String>> serverConfig = new HashMap<String, HashMap<String, String>>();
	
	//Variablen, die mit getServerVar() abgegriffen werden sollen, wenn der Abgriff mit getSystemVar() versucht wird
	private static ArrayList<String> redirect_serverVarNames = new ArrayList<String>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		{
			add(HTTP_ROOT);
			add(MAIL_DOMAIN);
			add(MAIL_SIG);
			add(TRANSFER_ROOT);
		}
	};
	
	/**
	 * Konfig-Datenbank ge�ffnet zur�ckgeben, Quelle = aktueller Server
	 */ 
	private static Database getSysDb() throws NotesException {
		
		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
		
		return session.getDatabase(session.getCurrentDatabase().getServer(), SYS_DB);
	}
	
	@SuppressWarnings("unchecked")
	public static String getSystemVar(String entry) throws NotesException {
		
		if (redirect_serverVarNames.contains(entry.toUpperCase())) {
			return getServerVar(entry);
		} else {
			
			//sofern noch nicht initialisiert, jetzt die Systemconfig einmal nachschlagen
			if (systemConfig==null) {
				
				//System.out.println("Init systemConfig");
				
				systemConfig = new HashMap<String, String>();
				
				Database sysDb = null;
				View v = null;
				Document doc = null;
				
				try {
					sysDb = getSysDb();
					
					v = sysDb.getView("LookupSystemConfig");
					v.refresh();
					
					doc = v.getFirstDocument();
					
					//Eintragen der relevanten Werte: Alle Items, die mit _ beginnen!
					Vector<Item> items = doc.getItems();
					for (int i=0; i<items.size(); i++) {
						Item it = items.get(i);
						if (it.getName().startsWith("_")) {
							systemConfig.put(it.getName().substring(1).toUpperCase(), it.getValueString());
							//System.out.println("..read: " + it.getName().substring(1).toUpperCase() + "->" + it.getValueString());
						}
						it.recycle();
					}
					
					
					//System.out.println("... count: " + systemConfig.size());
					
				} finally {
					TNotesUtil.recycleNotesObject(doc, v, sysDb);
				}
			}	//init sysConfig
			
			if (systemConfig.containsKey(entry.toUpperCase())) {
				return systemConfig.get(entry.toUpperCase());
			} else {
				//System.out.println("systemConfig " + entry.toUpperCase() + " not found (size=" + systemConfig.size() + ")");
				return "";
			}
			
		}
		
	}

	public static String getServerVar(String entry) throws NotesException {
		return getServerVar(entry, "");
	}
	
	@SuppressWarnings("unchecked")
	public static String getServerVar(String entry, String optServerName) throws NotesException {

		NotesContext nct = NotesContext.getCurrent();
		Session session = nct.getCurrentSession();
	
		if (optServerName.isEmpty()) {
			if (session.isOnServer()) {
				optServerName = session.getUserName();
			} else {
				optServerName = session.getCurrentDatabase().getServer();
			}
		}
		
		Name hlpName = session.createName(optServerName);
		optServerName = hlpName.getCommon().toLowerCase();
		hlpName.recycle();
		
		//ggf. f�r diesen Server initialisieren
		if (!serverConfig.containsKey(optServerName)) {

			//System.out.println("Init serverConfig for server: " + optServerName);
			
			serverConfig.put(optServerName, new HashMap<String, String>());
			
			Database sysDb = null;
			View v = null;
			Document doc = null;
			
			try {
				sysDb = getSysDb();
				
				v = sysDb.getView("LookupServerConfig");
				v.refresh();
				
				doc = v.getDocumentByKey(optServerName, true);
				if (doc==null) {
					doc = v.getDocumentByKey("-default-", true);
				}
				
				if (doc!=null) {
					
					//System.out.println("..using config: " + doc.getItemValueString("Server"));
					
					//Eintragen der relevanten Werte: Alle Items, die mit _ beginnen!
					Vector<Item> items = doc.getItems();
					for (int i=0; i<items.size(); i++) {
						Item it = items.get(i);
						if (it.getName().startsWith("_")) {
							serverConfig.get(optServerName).put(it.getName().substring(1).toUpperCase(), it.getValueString());
							//System.out.println("..read: " + it.getName().substring(1).toUpperCase() + "->" + it.getValueString());
						}
						it.recycle();
					}
				}
				
				//System.out.println("... count: " + serverConfig.get(optServerName).size());
				
			} finally {
				TNotesUtil.recycleNotesObject(doc, v, sysDb);
			}
			
		}
		
		//Abgriff
		if (serverConfig.get(optServerName).containsKey(entry.toUpperCase())) {
			return serverConfig.get(optServerName).get(entry.toUpperCase());
		} else {
			//System.out.println("serverConfig " + entry.toUpperCase() + " for server " + optServerName + " not found (size=" + serverConfig.get(optServerName).size() + ")");
			return "";
		}
		
	}
	
}
