package com.voessing.common;

import java.io.ByteArrayOutputStream;

//dko: Package Name ggf. sp�ter noch anpassen. Entwicklung/Federf�hrung = AZE Schablone

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;

/*
 * 
 * 2023-09-07, dko:
 * 	- isCORSPreflight() hinzugefügt
 * 
 * 2021-10-01, dko: 
 * 	- getRequestData2() hinzugefügt, da getRequestData() fix auf JsonJavaObject ausgelegt ist
 * 		- Parameter getAsString: erlaubt Rückgabe als String
 * 		- content-type "application/json" -> Object (kann Json Objekt sein oder ein Array (ArrayList mit Json Objekten in dem Fall)
 * 
 * 
 */
//


public class TJsonRestProvider extends TJsonRestServices implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	//protected TResponseOutputHandler responseHandler = new TResponseOutputHandler();
	
	public TJsonRestProvider(String optJsonDateFormat) {
		super(optJsonDateFormat);
	}

	//2021-10-01, dko: getAsString: erlaubt Rückgabe als String, und application/json -> Object (kann Json Objekt sein oder ein Array (ArrayList mit Json Objekten in dem Fall)
	public Object getRequestData2(ExternalContext externalContext, boolean getAsString) {
		
		Object data = null;

		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
		String method = request.getMethod().toUpperCase();
		
		if (method.equals(HTTP_POST) || method.equals(HTTP_PUT) || method.equals(HTTP_PATCH)) {

			doLog("  getRequestData2: " + method + " detected: getContentLength=" + request.getContentLength() + ", getCharacterEncoding=" + request.getCharacterEncoding() + ", getContentType=" + request.getContentType(), 1);

			InputStream is;
			
			try {
				
				is = request.getInputStream();
			
				if (request.getContentType().equalsIgnoreCase("application/json") && !getAsString) {

					//it's JSON! -> Kann JsonJavaObject oder ArrayList (anstelle von JsonJavaArray) sein
					
					InputStreamReader isR = new InputStreamReader(is, decoder);
					
					//use GSON fromJson and pass the reader as source, create a JsonJavaObject from the JSON String
					//data = (JsonJavaObject) gson.fromJson(isR, JsonJavaObject.class);
					
					JsonJavaFactory factory = JsonJavaFactory.instanceEx;
					data = JsonParser.fromJson(factory, isR);
				
				} else {
					
					//getAsString requested
					
					ByteArrayOutputStream result = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					for (int length; (length = is.read(buffer)) != -1; ) {
						result.write(buffer, 0, length);
					}
					
					data = result.toString("UTF-8");
					
				}
				
				doLog("  post data (json.toString()): " + data.toString(), 4);
				
			} catch (Exception e) {
				TNotesUtil.stdErrorHandler(e);
			}

		}
		
		return data;
		
	}
	
	//diese Methode kann nur JsonJavaObject, aber Requests können auch mit anderen Daten erfolgen 
	public JsonJavaObject getRequestData(ExternalContext externalContext) {
		
		JsonJavaObject data = null;
		
		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
		String method = request.getMethod().toUpperCase();
		
		if (method.equals(HTTP_POST) || method.equals(HTTP_PUT) || method.equals(HTTP_PATCH)) {

			doLog("  " + method + " detected: getContentLength=" + request.getContentLength() + ", getCharacterEncoding=" + request.getCharacterEncoding() + ", getContentType=" + request.getContentType(), 2);

			InputStream is;
			
			try {
				is = request.getInputStream();
				InputStreamReader isR = new InputStreamReader(is, decoder);
			
				//use GSON fromJson and pass the reader as source, create a JsonJavaObject from the JSON String
				//data = (JsonJavaObject) gson.fromJson(isR, JsonJavaObject.class);
				
				JsonJavaFactory factory = JsonJavaFactory.instanceEx;
				data = (JsonJavaObject) JsonParser.fromJson(factory, isR);
				
				doLog("  post data (json.toString()): " + data.toString(), 3);
				
			} catch (Exception e) {
				TNotesUtil.stdErrorHandler(e);
			}

		}
		
		return data;
	}
	
	
	@Deprecated
	//better use TResponseOutputHandler 
	public void outputResponse(FacesContext context, JsonJavaObject json) {

		TResponseOutputHandler.outputJson(context, json, encoder, gson);
		
		/*
		//Prepare
		ExternalContext externalContext = context.getExternalContext();
		XspHttpServletResponse response = (XspHttpServletResponse) externalContext.getResponse();

		//deactivate xsp cache: will deactivate the gzip compression as well
		response.disableXspCache(false);
		
		//set output format, encoding and other options
		response.setHeader("Cache-Control", "no-cache");
		response.setContentType("application/json;charset=" + encoding);
		response.setCharacterEncoding(encoding);
		
		try {
			
			//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			
			byte[] utf8JsonString = gson.toJson(json).getBytes(encoder.charset());

			out.write(utf8JsonString, 0, utf8JsonString.length);
			
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			
			// Stop the page from further processing;
			context.responseComplete();
			
		}
*/
	}
	
	//extract a parameter value as an integer value or use default if param is not available
	public static String extractParamString(Map<String, Object> requestParams, String paramName, String defaultValue) {
		return (requestParams.containsKey(paramName)) ? requestParams.get(paramName).toString().trim() : defaultValue;
	}
	
	public static String extractParamString(JsonJavaObject requestParams, String paramName, String defaultValue) {
		return (requestParams.containsKey(paramName)) ? requestParams.getAsString(paramName) : defaultValue;
	}

	//extract a parameter value as a string value or use default if param is not available
	public static int extractParamInt(Map<String, Object> requestParams, String paramName, int defaultValue) {
		return (requestParams.containsKey(paramName)) ? Integer.parseInt(requestParams.get(paramName).toString().trim()) : defaultValue;
	}

	public static int extractParamInt(JsonJavaObject requestParams, String paramName, int defaultValue) {
		return (requestParams.containsKey(paramName)) ? requestParams.getAsInt(paramName) : defaultValue;
	}

	public static Date extractParamDate(Map<String, Object> requestParams, String paramName, Date defaultValue) throws ParseException {
		return extractParamDate(requestParams, paramName, defaultValue, null);
	}

	public static Date extractParamDate(Map<String, Object> requestParams, String paramName, Date defaultValue, String optDateFmtString) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat( (optDateFmtString==null || optDateFmtString.equals("")) ? "yyyyMMdd" : optDateFmtString);
		return (requestParams.containsKey(paramName)) ? format.parse(requestParams.get(paramName).toString().trim()) : defaultValue;
	}

	//create an action response with a common structure across all action API commands
	public static JsonJavaObject createActionAPIResponse(boolean success, String optHint) {
		
		JsonJavaObject json = new JsonJavaObject();
		json.putJsonProperty("success", success);
		json.putJsonProperty("message", optHint);

		return json;
	}
	
	//Feststellen, ob es sich um einen OPTIONS-Preflight (CORS) handelt
	public static boolean isCORSPreflight(ExternalContext externalContext) {
		
		boolean isPreflight = false;
		
		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
		String rqMethod = request.getMethod().toUpperCase();

		if (request.getHeader("Origin")!=null && rqMethod.equalsIgnoreCase(TJsonRestServices.HTTP_OPTIONS) && request.getHeader("Access-Control-Request-Method")!=null) {

			//System.out.println("..Origin: " + request.getHeader("Origin"));
			//System.out.println("..Access-Control-Request-Method: " + request.getHeader("Access-Control-Request-Method"));
			
			isPreflight = true;
			
		}
		
		return isPreflight;
		
	}

}
