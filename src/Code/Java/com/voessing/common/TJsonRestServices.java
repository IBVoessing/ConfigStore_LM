package com.voessing.common;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.commons.util.io.json.JsonJavaObject;

public class TJsonRestServices implements Serializable {

/*
 * Begin member declaration
 */

//SCOPE = CLASS
	private static final long serialVersionUID = 1L;
	
	public static final String HTTP_GET = "GET";
	public static final String HTTP_POST = "POST";
	public static final String HTTP_PUT = "PUT";
	public static final String HTTP_PATCH = "PATCH";
	public static final String HTTP_DELETE = "DELETE";
	public static final String HTTP_OPTIONS = "OPTIONS";
	
	//logLevel: default=0 = no output
	private int logLevel = 0;

//SCOPE = CLASS, SUBCLASSES

	//hold one instance of the GSON wrapper for all operations (one-time-init = less overhead)
	//2017-06-22, dko: Direklt GSon verwenden, nicht den Wrapper (erfordert allPernissions in java.policy)
	Gson gson = null;

	//encoding
	CharsetEncoder encoder = null;
	CharsetDecoder decoder = null;
	String encoding = "";
	
/*
 * End member declaration
 */
	
	/*
	 * Beispielimplementierung für gson-Datumskonvertierung
	 * 



		CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
		
		Gson gson = null;
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		
		//alternative Date-Handler TEST
        gson = gsonBuilder
        .setDateFormat("dd.MM.yyyy")
        .setDateFormat(DateFormat.LONG)
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        .registerTypeAdapter(Date.class, new DateLongFormatTypeAdapter())
        .create();
		
		TResponseOutputHandler h = new TResponseOutputHandler();
		h.outputJson(response, data, encoder, gson);
		


	 * 
	 * 
	 * 
	 */
	
	
	

	public TJsonRestServices(String optJsonDateFormat) {
		
		//init once for optimal speed
		gson = new GsonBuilder().setDateFormat(optJsonDateFormat.equals("") ? "dd.MM.yyyy" : optJsonDateFormat).create();
		//gson = new GsonWrapper( optJsonDateFormat.equals("") ? "dd.MM.yyyy" : optJsonDateFormat);
		
		//use UTF-8 as default encoding
		setEncoding("utf-8");
	}

	
	//log function
	protected void doLog(String message, int logLevel) {
		if ((this.logLevel > 0) && (logLevel <= this.logLevel)) {
			System.out.println(this.getClass().getSimpleName() + ": " + message);
		}
	}

	//set logLevel. 0 = no log
	public void setLogLevel(int logLevel) {
		this.logLevel = logLevel;
		//System.out.println(this.getClass().getSimpleName() + ": logLevel changed to " + logLevel);
	}
	
	//set response encoding
	public void setEncoding(String encoding) {
		this.encoding = encoding;
		encoder = Charset.forName(encoding).newEncoder();
		decoder = Charset.forName(encoding).newDecoder();
		doLog("encoding/decoding set to: " + this.encoding, 3);
	}

	//create an error response with a common structure across all action API commands
	public static JsonJavaObject generateJSONError(String ID, String type, String category, String severity, String message) {
		
		JsonJavaObject json = new JsonJavaObject();
		json.putJsonProperty("$JSONERROR$", 1);	//Kennzeichnung, dass es ein standardisiertes Fehler-Objekt handelt
		json.putJsonProperty("id", ID);
		json.putJsonProperty("type", type);
		json.putJsonProperty("category", category);
		json.putJsonProperty("severity", severity);
		json.putJsonProperty("message", message);
		
		return json;
	}

	//create an error response with a common structure across all action API commands
	public static JsonJavaObject generateJSONError(String ID, String type, String category, String severity, String message, int responseCode) {
		
		JsonJavaObject json = new JsonJavaObject();
		json.putJsonProperty("$JSONERROR$", 1);	//Kennzeichnung, dass es ein standardisiertes Fehler-Objekt handelt
		json.putJsonProperty("id", ID);
		json.putJsonProperty("type", type);
		json.putJsonProperty("category", category);
		json.putJsonProperty("severity", severity);
		json.putJsonProperty("message", message);
		json.putJsonProperty("responseCode", responseCode);
		
		return json;
	}

}
