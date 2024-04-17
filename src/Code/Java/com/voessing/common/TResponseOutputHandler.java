package com.voessing.common;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.webapp.XspHttpServletResponse;

/*
 * 2023-09-07, dko:
 * 	- Handling für CORS Preflights hinzugefügt
 * 
 * 2023-08-09, dko: 
 * 	- Fehlender "Content-Length"-Header auch bei SendError() mit Text und ohne Content hinzugefügt, sonst Fehler bei 12.0.2 LS Http Request
 * 
 * 2023-06-14, dko:
 * 	- Fehlender "Content-Length"-Header verursacht ab 12.0.2 Probleme bei NotesHttpRequest
 *  - Ursache: disableXspCache(false) (schaltet auch die Default-GZIP Kompression ab)
 *  - Fix: 
 *  	- Content-Length wird vor dem Schreiben gesetzt
 *  	- die bisherige GZIP-Implementierung wird durch die Default ersetzt, indem im Fall von gzip-Anforderung disableXspCache(false) nicht ausgeführt wird
 *  		--> dies erfolgt in der getPreparedResponse()-Methode
 * 
 * 2022-05-30, dko: 
 * 	- getPreferredContentEncoding(): Liest "Accept-Encoding" aus und setzt das gewünschte encoding (gzip hat Vorrang)
 *  - outputJson(): berücksichtigt gzip, wenn erlaubt
 *  - outputStream() diesbezüglich zunächst nicht angepasst
 *  
 * 2022-05-06, dko: getPreparedResponse(): mirror Origin as CORS header if allowed for this Origin
 * 2020-07-10, dko: Umstellung aller Methoden und der default_-Member auf static
 */

public class TResponseOutputHandler implements Serializable {
		
	/**
	 * 
	 */
	
	public static enum Disposition {

		INLINE						("inline"),
		ATTACHMENT					("attachment");
		
		private String type;

		private Disposition(String type)
		{
			this.type = type;
		}
		
	};
	
	private static final long serialVersionUID = 1L;
	
	private static Gson default_gson = new GsonBuilder().setDateFormat("dd.MM.yyyy").create();
	private static CharsetEncoder default_encoder = Charset.forName("utf-8").newEncoder();
	
	private static ArrayList<String> allowedOriginsOverrideCors = new ArrayList<>(
			Arrays.asList(
					"https://yapps.voessing.de",
					"https://related-cobra-allowing.ngrok-free.app",
					"https://5b29t4qt-5173.euw.devtunnels.ms"
			));

	public TResponseOutputHandler() {
	}

	//access Response and prepare it with general defaults which are valid for all response output types
	public static XspHttpServletResponse getPreparedResponse(FacesContext context) {
		
		//Prepare
		ExternalContext externalContext = context.getExternalContext();
		XspHttpServletResponse response = (XspHttpServletResponse) externalContext.getResponse();

		//deactivate xsp cache: will deactivate the gzip compression as well
		//2023-06-14, dko: wird kein gzip angefordert, dann deaktivieren der internen gzip-Methodik
		if (!getPreferredContentEncoding(externalContext).equalsIgnoreCase("gzip")) {
			response.disableXspCache(false);
		}
		
		//set output format, encoding and other options
		response.setHeader("Cache-Control", "no-cache");

		//2022-05-06, dko: mirror Origin as CORS header if allowed for this Origin
		Map hdr = externalContext.getRequestHeaderMap();
		if (hdr.get("Origin")!=null) {
			
			String origin = hdr.get("Origin").toString();
			
			//CORS-Header hinzufügen, wenn die Origin erlaubt ist
			if (allowedOriginsOverrideCors.contains(origin.toLowerCase())) {
				response.setHeader("Access-Control-Allow-Origin", origin);
				
				//die anderen könnten weiterhin aus der Serverkonfiguration (Rules) kommen, wir setzen Sie aber immer, d.h. unabhängig vom Responsecode
				//-> Serverkonfig überschreibt die Angaben hier
				response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, Accept-Encoding");
				response.setHeader("Access-Control-Allow-Credentials", "true");
			}
		}
		
		return response;
	}

	public static void sendHttpCORSResponse(FacesContext context, ArrayList<String> optAllowedMethods) {
		
		//Prepare
		ExternalContext externalContext = context.getExternalContext();
		XspHttpServletResponse response = (XspHttpServletResponse) externalContext.getResponse();
		
		//Es wird kein GZIP benötigt
		response.disableXspCache(false);
		
		//Keine Daten zurücksenden
		response.setContentLength(0);
		
		try {
		
			Map hdr = externalContext.getRequestHeaderMap();
			if (hdr.get("Origin")!=null) {
				
				String origin = hdr.get("Origin").toString();
				
				//spezifische CORS-Preflight-Header hinzufügen, wenn die Origin erlaubt ist
				if (allowedOriginsOverrideCors.contains(origin.toLowerCase())) {
					
					ArrayList<String> allowedMethods = new ArrayList<>();
					
					if (optAllowedMethods!=null) {
						allowedMethods.addAll(optAllowedMethods);
					} else {
						//Defaults
						allowedMethods.add(TJsonRestServices.HTTP_GET);
						allowedMethods.add(TJsonRestServices.HTTP_POST);
						allowedMethods.add(TJsonRestServices.HTTP_PUT);
						allowedMethods.add(TJsonRestServices.HTTP_PATCH);
						allowedMethods.add(TJsonRestServices.HTTP_DELETE);
					}
					
					if (allowedMethods.size()>0) {
						
						//Options bei Bedarf hinzufügen
						if (!allowedMethods.contains(TJsonRestServices.HTTP_OPTIONS)) allowedMethods.add(TJsonRestServices.HTTP_OPTIONS);
						
						//analog getPreparedResponse()
						response.setHeader("Access-Control-Allow-Origin", origin);
						
						//die anderen könnten weiterhin aus der Serverkonfiguration (Rules) kommen, wir setzen Sie aber immer, d.h. unabhängig vom Responsecode
						//-> Serverkonfig überschreibt die Angaben hier
						response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, Accept-Encoding");
						response.setHeader("Access-Control-Allow-Credentials", "true");
						
						//Access-Control-Allow-Methods
						response.setHeader("Access-Control-Allow-Methods", allowedMethods.stream().collect(Collectors.joining(", ")));
						
						//Access-Control-Max-Age: 1 Tag
						response.setHeader("Access-Control-Max-Age", "86400");
						
						response.setStatus(200);
						
					} else response.sendError(XspHttpServletResponse.SC_FORBIDDEN);
					
				} else response.sendError(XspHttpServletResponse.SC_FORBIDDEN);
				
			} else response.sendError(XspHttpServletResponse.SC_FORBIDDEN);

		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
	}
	
	public static void sendHttpStatus(FacesContext context, int responseCode) {
		
		XspHttpServletResponse response = getPreparedResponse(context);
		
		try {

			//2023-08-09, dko: Sonst Fehler bei 12.0.2 LS Http Request
			response.setContentLength(0);

			response.setStatus(responseCode);
			
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
	}
	
	public static void sendHttpError(FacesContext context, int responseCode) {
		
		//set default Error
		if (responseCode<=0) responseCode = XspHttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		
		XspHttpServletResponse response = getPreparedResponse(context);
		
		try {
			
			//2023-08-09, dko: Sonst Fehler bei 12.0.2 LS Http Request
			response.setContentLength(0);
			
			response.sendError(responseCode);
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
	}
	
	public static void sendHttpError(FacesContext context, int responseCode, String message) {
		
		if (message==null || message.length()==0) {
			//keine eigene Nachricht -> Standardmethode verwenden
			sendHttpError(context, responseCode);
		} else {
		
			//set default Error
			if (responseCode<=0) responseCode = XspHttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			
			XspHttpServletResponse response = getPreparedResponse(context);
			
			try {

				//analog AZE Report Fehlerbehandlung
				response.setStatus(responseCode);
				response.setContentType("text/html");
				response.setCharacterEncoding("UTF-8");

				byte[] utf8JsonString = message.getBytes(default_encoder.charset());
				
				//2023-08-09, dko: Sonst Fehler bei 12.0.2 LS Http Request
				response.setContentLength(utf8JsonString.length);
				
				//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
				DataOutputStream out = new DataOutputStream(response.getOutputStream());
				out.write(utf8JsonString, 0, utf8JsonString.length);

			} catch (IOException e) {
				TNotesUtil.stdErrorHandler(e);
			} catch (Exception e) {
				TNotesUtil.stdErrorHandler(e);
			} finally {
				// Stop the page from further processing;
				context.responseComplete();
			}
			
		}
		
	}

	public static void sendHttpError(FacesContext context, int responseCode, JsonJavaObject json) {
		
		if (json==null) {
			
			//keine eigene Nachricht -> Standardmethode verwenden
			sendHttpError(context, responseCode);
			
		} else {
		
			//set default Error
			if (responseCode<=0) responseCode = XspHttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			
			XspHttpServletResponse response = getPreparedResponse(context);
			
			try {

				response.setStatus(responseCode);
				response.setContentType("application/json;charset=" + default_encoder.charset().name());
				response.setCharacterEncoding(default_encoder.charset().name());

				byte[] utf8JsonString = default_gson.toJson(json).getBytes(default_encoder.charset());

				//2023-06-14, dko
				response.setContentLength(utf8JsonString.length);
				
				//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
				DataOutputStream out = new DataOutputStream(response.getOutputStream());
				out.write(utf8JsonString, 0, utf8JsonString.length);

				//do not close output stream here
				
			} catch (Exception e) {
				TNotesUtil.stdErrorHandler(e);
			} finally {
				// Stop the page from further processing;
				context.responseComplete();
			}
			
		}
		
	}
	
	/* d. alsgaard, https://www.dalsgaard-data.eu/blog/use-smileys-or-emojis-with-your-ibm-domino-rest-services/
	 * 
	 * So far I have had the call to the processing function in the bean in the �afterRenderResponse� event of the XPage. 
	 * This turns out to be important. And I cannot remember why I ended up calling it there instead of in the �beforeRenderResponse� event.
	 * This concept has served me well � it shows all the special characters and performs well. That is until someone sent a smiley
	 * The problem is that there are not enough bits in �utf-8� to show all of the characters in the world. 
	 * However, there are some built-in magic that can use two bytes to represent a character 
	 * (�utf-16� uses two bytes to show all characters � just like the good old Lotus double byte character). 
	 * The beauty is that we normally don�t have to care about it � the programming tools that we use will normally seamlessly handle this. 
	 * However, we need to work with the �bytes� as opposed to �characters� to allow the seamless handling. 
	 * So we need to send a byte stream as opposed to a String back as a response to the http request that wanted to read some data from 
	 * our service. To make this happen we need to use a �DataOutputStream� instead of the �ResponseWriter� that I used above. 
	 */
	
	public static void outputJson(FacesContext context, JsonJavaObject json) {
		outputJson(context, json, default_encoder, default_gson);
	}
	
	@Deprecated
	public static void outputJsonOldBis20230614(FacesContext context, JsonJavaObject json, CharsetEncoder encoder, Gson gson) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());
			
			byte[] utf8JsonString = gson.toJson(json).getBytes(encoder.charset());
			
			String useEncoding = getPreferredContentEncoding(context.getExternalContext());
			
			
			if (useEncoding.equalsIgnoreCase("gzip")) {
				
				response.setHeader("Content-Encoding", "gzip");
				
				//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
				GZIPOutputStream out = new GZIPOutputStream(response.getOutputStream());

				out.write(utf8JsonString, 0, utf8JsonString.length);
				out.finish();	//wichtig bei GZip
				
				/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
				//DO NOT: out.close();: Führt zu Fehlern
		        z.B.
		        
		        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
		        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
		        */
				
			} else {
				
				//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
				DataOutputStream out = new DataOutputStream(response.getOutputStream());
	
				out.write(utf8JsonString, 0, utf8JsonString.length);
				
				/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
				//DO NOT: out.close();: Führt zu Fehlern
		        z.B.
		        
		        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
		        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
		        */
			}

			
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
		
/* bis 2022-05-30
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());
			
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
	
	
	public static void outputJson(FacesContext context, JsonJavaObject json, CharsetEncoder encoder, Gson gson) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());
			
			byte[] utf8JsonString = gson.toJson(json).getBytes(encoder.charset());
			
			//2023-06-14, dko
			response.setContentLength(utf8JsonString.length);
				
			//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			out.write(utf8JsonString, 0, utf8JsonString.length);
			
			/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
			//DO NOT: out.close();: Führt zu Fehlern
	        z.B.
	        
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
	        */
		
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
	}
	
	public static void outputJson(FacesContext context, JsonJavaArray jsonArr) {
		outputJson(context, jsonArr, default_encoder, default_gson);
	}
	
	public static void outputJson(FacesContext context, JsonJavaArray jsonArr, CharsetEncoder encoder, Gson gson) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());
			
			byte[] utf8JsonString = gson.toJson(jsonArr).getBytes(encoder.charset());

			//2023-06-14, dko
			response.setContentLength(utf8JsonString.length);
				
			//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			out.write(utf8JsonString, 0, utf8JsonString.length);
			
			/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
			//DO NOT: out.close();: Führt zu Fehlern
	        z.B.
	        
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
	        */
			
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
/* bis 2022-05-30
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());
			
			//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			
			byte[] utf8JsonString = gson.toJson(jsonArr).getBytes(encoder.charset());

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

	private static void setDisposition(XspHttpServletResponse response, Disposition d, String optFileName) {
		if (d!=null) {
			String dispo = d.type;

			//add Filename if available
			//2019-11-25, dko: Filename mit Leerzeichen kann zu Fehlern im FX führen (wird nach 1. Leerzeichen abgeschnitten)
			//if (optFileName!=null && !optFileName.isEmpty()) dispo += "; filename=" + optFileName;
			
			if (optFileName!=null && !optFileName.isEmpty()) dispo += "; filename=\"" + optFileName.replace("\"", "-") + "\"";
			
			response.setHeader("Content-Disposition", dispo);
		}
	}

	private static void outputStream(FacesContext context, XspHttpServletResponse response, ByteArrayOutputStream responseByteStream) {
		
		try {

			//2023-06-14, dko
			response.setContentLength(responseByteStream.size());
			
	        //Write data
	        responseByteStream.writeTo(response.getOutputStream());

			/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
			//DO NOT: out.close();: Führt zu Fehlern
	        z.B.
	        
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
	        */
	        
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing;
			context.responseComplete();
		}
		
	}
	
	public static void outputXlsx(FacesContext context, ByteArrayOutputStream responseByteStream, String optFileName, Disposition d) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
        response.setDateHeader("Expires", -1);
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		setDisposition(response, d, optFileName);

		outputStream(context, response, responseByteStream);
	}
	
	public static void outputPptx(FacesContext context, ByteArrayOutputStream responseByteStream, String optFileName, Disposition d) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
        response.setDateHeader("Expires", -1);
        response.setContentType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
		setDisposition(response, d, optFileName);

		outputStream(context, response, responseByteStream);
	}
	
	public static void outputDocx(FacesContext context, ByteArrayOutputStream responseByteStream, String optFileName, Disposition d) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
        response.setDateHeader("Expires", -1);
		response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		setDisposition(response, d, optFileName);

		outputStream(context, response, responseByteStream);
	}
	
	public static void outputZip(FacesContext context, ByteArrayOutputStream responseByteStream, String optFileName, Disposition d) {

		XspHttpServletResponse response = getPreparedResponse(context);
		
        response.setDateHeader("Expires", -1);
		response.setContentType("application/zip");
		setDisposition(response, d, optFileName);

		outputStream(context, response, responseByteStream);
	}
	
	//more output formats to implement...

	/*
	 * Direkt in eine HttpServletResponse ausgeben, die bereits vorbereitet ist
	 * -> z.B. bei xe:restService / CustomServiceBean
	 */
	public static void outputJson(HttpServletResponse response, JsonJavaArray json, CharsetEncoder encoder, Gson gson) {
		
		try {

			response.setContentType("application/json;charset=" + encoder.charset().name());
			response.setCharacterEncoding(encoder.charset().name());

			byte[] utf8JsonString = gson.toJson(json).getBytes(encoder.charset());
			
			//2023-04-16, dko: Länge des Content setzen
			response.setContentLength(utf8JsonString.length);
			
			//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
			DataOutputStream out = new DataOutputStream(response.getOutputStream());
			out.write(utf8JsonString, 0, utf8JsonString.length);
			
			/*2017-06-23, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
			//DO NOT: out.close();: F�hrt zu Fehlern
	        z.B.
	        
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0211E: Exception thrown. For more detailed information, please consult error-log-0.xml located in C:/IBM/Domino/data/domino/workspace/logs
	        [0DB4:000E-0F14] 23.06.2017 11:35:32   HTTP JVM: CLFAD0229E: Security exception occurred servicing request for: /AZEApp.nsf/Retrieve.xsp/GetProjectBookingHelperDetails - HTTP Code: 500. For more detailed information, please consult error-log-0.xml located
	        */
			
		} catch (IOException e) {
			TNotesUtil.stdErrorHandler(e);
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		} finally {
			// Stop the page from further processing
			//-> hier nicht notwendig, da kein Context vorhanden ist
		}

	}

	public static String getPreferredContentEncoding(ExternalContext externalContext) {
		
		String preferredEncoding = "";
		
		Map hdr = externalContext.getRequestHeaderMap();
		
		//2022-05-30, dko: Transparent handle encoding
		if (hdr.get("Accept-Encoding")!=null) {
			
			String encoding = hdr.get("Accept-Encoding").toString();
			
			if (encoding.toLowerCase().contains("gzip")) {
				preferredEncoding = "gzip";
			}
			
		}
		
		return preferredEncoding;
		
	}
	
}
