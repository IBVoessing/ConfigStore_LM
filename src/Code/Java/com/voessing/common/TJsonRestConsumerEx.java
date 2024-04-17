package com.voessing.common;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.StringTokenizer;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.Cookie;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;


/*
 * 
 * 2024-03-19, dko/lme:
 * 	- NEU
 * 		- doPatch(): Support per allowMethods() Hack
 * 
 * 2023-11-12, dko/lme:
 * 	- NEU
 * 		- doDelete()
 *  * 
 * 	- CHANGE
 * 		- doRequest()
 * 			- setRequestMethod() immer setzen, Bedingung für postData-Aufbereitung geändert
 * 			- Error-Stream: Ist es kein JSON, dann keine Exception mehr auslösen, sondern wie Content=leer behandeln
 * 
 * 2023-04-26, dko: 
 * 	- NEU:
 * 		- setEnableTLSv13(): Support f. TLS 1.3 aktivieren. Benötigt für Companyhouse.
 * 			-> Kann später der Standard werden, in höheren JAVA-Versionen ggf. Default
 * 
 * 2023-03-17, dko:
 *  - NEU
 *  	- PUT Unterstützung, dafür Anpassung interner Methoden
 * 
 * 2022-05-30, dko:
 * 	- ChANGE
 * 		- set default header "accept-encoding" to identity: 
 * 			Wer mit dem Consumer gzip erhalten will, muss dies zunächst per consumer.setRequestProperty() selbst anfordern
 * 		- accept gzip inbound stream (POST output als GZIP noch nicht implementiert)
 * 		- (indirekt: TResponseOutputHandler unterstützt gzip transparent je nach Anfrage-Header) 

 * 2021-15-15, dko:
 * 	- CHANGE
 * 		- bisher wurde Trustmanager, SSLContext und SSLFactory bei jedem Call neu initiiert
 * 		- nun wird dies nur einmalig bzw. beim Wechsel der variable "trustAllCerts" gemacht
 * 		- dies soll ein keep-alive ermöglichen
 * 		- außerdem wurde nun ein Catch-Block für die IOException angelegt, die den Error-Stream mit close(9 schließt (ebenfalls für keep-alive)
 * 
 * 2021-10-14, dko:
 * 	- CHANGE:
 * 		- bisher wurde im Fehlerfall der ResponseStream nicht ausgewertet, nun wird getErroprStream() angesetzt
 * 		- add "Accept"->*, wenn nicht vorhanden
 * 
 * 2021-09-30. dko:
 * 	- Set-Cookie-Direktive verarbeiten, Cookies domain-specific speichern und wieder anwenden bei Folgerequest (expires und path werden beachtet)
 * 
 * 2021-09-13. dko:
 * 	- setTrustAllCerts(): default = false. Mit true kann bei Zertifikatsfehlern trotzdem eine Verbindung aufgebaut werden 
 * 
 * 2021-07-05, dko: 
 * 	- ?bernahme des Stands von TJsonRestConsumer
 * 	- Anpassung: Exceptions statt Error-Catching, Response als Object statt konkret JsonJavaObject, damit auch JsonJavaArray verwendbar ist
 * 	- Umstellung auf Base64 von java.util
 * 
 */

public class TJsonRestConsumerEx extends TJsonRestServices {

	private static final String COOKIE_NNAME_TAG = "$$name$:";

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final char NAME_VALUE_SEPARATOR = '=';
	private static final String PATH = "path";
	private static final String EXPIRES = "expires";
	
	//2021-10-15, dko: den trustManager pro Instanz nur 1x instanziieren, das soll (ggf.) keep-Alive ermöglichen
	TrustManager[] restTrustMgr = null;
	SSLContext restSSLContext = null;
	SSLSocketFactory restSSLSocketFactory = null;
	
	public TJsonRestConsumerEx(String optJsonDateFormat) {
		super(optJsonDateFormat);

		// fix to allow PATCH method
		allowMethods(TJsonRestServices.HTTP_PATCH);
		
		//einmalig instanziieren
		restTrustMgr = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType) {
			}
		}};
		
	}
	
	private static void allowMethods(String... methods) {
        try {
            Field methodsField = HttpsURLConnection.class.getSuperclass().getDeclaredField("methods");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);

            methodsField.setAccessible(true);

            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.addAll(Arrays.asList(methods));
            String[] newMethods = methodsSet.toArray(new String[0]);

            methodsField.set(null/*static field*/, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        	//silent: keine Exception auslösen, dann bleibt das Objekt eben ohne Patch-Support
            //throw new IllegalStateException(e);
        }
    }	
	
	private void initSSLContext(boolean forceCreateNew) throws NoSuchAlgorithmException, KeyManagementException {
		
		if (restSSLContext==null || forceCreateNew) {
			
			// Install the all-trusting trust manager instead of 'SSLContexts.createDefault()'
			
			SSLContext restSSlContext;
			
			if (enableTlsv13) {
				restSSlContext = SSLContext.getInstance("TLSv1.3");
			} else restSSlContext = SSLContext.getInstance("SSL");
						
			if (this.trustAllCerts) {
				
				restSSlContext.init(null, restTrustMgr, new java.security.SecureRandom());
				
			} else {
				restSSlContext.init(null, null, new java.security.SecureRandom());
			}
	
			restSSLSocketFactory = restSSlContext.getSocketFactory();
			
		}
		
	}
	
	//authentication
	private static enum Auth {
		NONE, BASIC, BEARER
	}
	private Auth authMethod = Auth.NONE;
	
	//2021-07-02, dko: Support f?r das Posten einfacher Daten im x-www-form-urlencoded-Format
	public static enum PostType {
		JSON, X_WWW_FORM_URLENCODED
	}
	private PostType postType = PostType.JSON;

	private String basicAuthUsername = "";
	private String basicAuthPassword = "";
	
	private String bearerAuthToken = "";
	
	//default timeout for connectiing to service
	private int defaultConnectionTimeout = 2000;
	
	//default timeout for reading from service (this includes service processing time like SQL queries etc)
	private int defaultReadTimeout = 60000;
	
	//default option for cache usage
	private boolean defaultUseCache = false;
	
	//default option f�r TrustManager-Override
	private boolean trustAllCerts = false;
	
	private int lastResponseCode = 0;
	private String lastResponseMessage = "";
	private JsonJavaObject lastRequestRestData = new JsonJavaObject();
	
	//default list of cookies that should be applied from request to REST request
	ArrayList<String> defaultUseCookies = new ArrayList<String>();

	private LinkedHashMap<String,Object> requestHeader = new LinkedHashMap<String,Object>();

	private HashMap<String, HashMap<String, HashMap<String,String>>> receivedCookiesStore = new HashMap<>();

	private boolean enableTlsv13 = false;

	public int getLastResponseCode() {
		return lastResponseCode;
	}

	public String getLastResponseMessage() {
		return lastResponseMessage;
	}

	public JsonJavaObject getLastRequestRestData() {
		return lastRequestRestData;
	}

	
	public void setPostType(PostType postType) {
		this.postType = postType;
	}
	
	//set default timeout for connecting to service
	public void setDefaultConnectionTimeout(int timeout) {
		this.defaultConnectionTimeout = timeout;
	}

	//set default timeout for reading from service (this includes service processing time like SQL queries etc)
	public void setDefaultReadTimeout(int timeout) {
		this.defaultReadTimeout = timeout;
	}

	//set option for cache usage
	public void setDefaultUseCache(boolean useCache) {
		this.defaultUseCache = useCache;
	}
	
	public void setTrustAllCerts(boolean doTrust) throws KeyManagementException, NoSuchAlgorithmException {
		this.trustAllCerts = doTrust;
		initSSLContext(true);
	}
	
	public void setEnableTLSv13(boolean enable) throws KeyManagementException, NoSuchAlgorithmException {
		this.enableTlsv13 = enable;
		initSSLContext(true);
	}
	
	//add the name of a cookie that should be applied from request to REST request (case insensitive, separate by semicolon)
	public void addDefaultUseCookies(String cookieName) {
		if (!cookieName.trim().equals("")) defaultUseCookies.add(cookieName.trim().toLowerCase());
	}

	//Basic Auth aktivieren
	public void setBasicAuth(String username, String password) {
		authMethod = Auth.BASIC;
		basicAuthUsername = username;
		basicAuthPassword = password;
	}

	public void setBearerAuth(String token) {
		authMethod = Auth.BEARER;
		bearerAuthToken = token;
	}

	private static String initURLComponent(String component) {
		return ((component==null) || component.trim().isEmpty()) ? "" : component;
	}
	

	public static String buildXAgentURL(String baseURL, String servlet, String pathInfo) {
		return buildXAgentURL(baseURL, servlet, pathInfo, new JsonJavaObject());
	}
	
	public static String buildXAgentURL(String baseURL, String servlet, String pathInfo, JsonJavaObject params) {
		
		LinkedHashMap<String,Object> paramsList = new LinkedHashMap<String,Object>();
		
		if (params!=null) {
			for (Map.Entry<String,Object> param : params.entrySet()) {
				paramsList.put(param.getKey(), param.getValue());
			}
		}
		
		return buildXAgentURL(baseURL, servlet, pathInfo, paramsList);
	}
	
	//only for xpage/xagent: build baseURL+servlet+pathinfo (unencoded) + params (encoded)
	public static String buildXAgentURL(String baseURL, String servlet, String pathInfo, LinkedHashMap<String,Object> params) {
		
		StringBuilder sb = new StringBuilder();
		
		String sURL = initURLComponent(baseURL);
		String sSrv = initURLComponent(servlet);
		String sPti = initURLComponent(pathInfo);
		
		String lastElement = "";
		
		//add Base URL if not empty
		if (!sURL.isEmpty()) {
			
			/*
			 * 2019-05-31, dko: dies f?hrt immer zu einem trailing slash, was aber Unsinn ist, wenn
			 * z.B. nur die baseURL verwendet wird ("xxx?OpenDocument/" ist nicht richtig
			 * -> Servlet und Pathinfo haben ihre eigene Pr?fung zur automatischen Erg?nzung eines leading backslahes, wenn erforderlich
			 * -> einzig bei den parametern passier dies nicht, d.h. nun werden keine xxx/?a=B&c=D Links mehr erstellt, was  aber vrstl. OK ist
			 * 
			//always end Base URL with /
			if (!sURL.endsWith("/")) sURL += "/";
			*/
			lastElement = sURL;
			sb.append(sURL);
		}
		
		//add Servlet if not empty, prefix a "/" if needed
		if (!sSrv.isEmpty()) {
			if (!lastElement.endsWith("/") && !sSrv.startsWith("/")) sb.append("/");
			sb.append(sSrv);
			lastElement = sSrv;			
		}
		
		//add PathInfo if not empty, prefix a "/" if needed
		if (!sPti.isEmpty()) {
			if (!lastElement.endsWith("/") && !sPti.startsWith("/")) sb.append("/");
			sb.append(sPti);
			lastElement = sPti;
		}
		
		//first separator for Parameters is always ?, for more & is used
		if (params!=null) {
			
			String separator = "?";
			
			//2019-05-31, dko: is "?" already in URL so far (e.g. in baseUrl wirn "xxx?OpenDocument", then don't use again, use & instead
			if (sb.toString().contains("?")) separator = "&";
			
			for (Map.Entry<String,Object> param : params.entrySet()) {
				
				String name = param.getKey();
				String value = String.valueOf(param.getValue());
				
				//nur Parameter verwenden, deren Wert nicht mit $ beginnt
				//und nur Parameter verwenden, deren Wert nicht leer ist
				if (!value.isEmpty() && !value.startsWith("$")) {
					sb.append(separator);
					separator = "&";
					
					try {
						name = URLEncoder.encode(name, "UTF-8");
						value = URLEncoder.encode(value, "UTF-8");
						
						sb.append(name);
						sb.append("=");
						sb.append(value);
						
					} catch (UnsupportedEncodingException e) {
						TNotesUtil.stdErrorHandler(e);
					}
					
				}
				
	        }
		}
		
		return sb.toString();
	}

	public void addRequestHeader(String key, Object value) {
		this.requestHeader.put(key, value);
	}

	public void clearRequestHeader() {
		this.requestHeader.clear();
	}
	
	//issue a GET request and retrieve data from a JSON service using the defaults for all parameters
	public Object doGet(String url) throws Exception {
		return doGet(url, defaultUseCookies, defaultUseCache);
	}

	//issue a GET request and retrieve data from a JSON service using the defaults for all other parameters
	public Object doGet(String url, ArrayList<String> useCookies, boolean useCache) throws Exception {
		return doGet(url, defaultConnectionTimeout, defaultReadTimeout, useCookies, useCache);
	}
	
	//issue a GET request and retrieve data from a JSON service providing full list of parameters (except postData)
	public Object doGet(String url, int conTimeout, int readTimeout, ArrayList<String> useCookies, boolean useCache) throws Exception {
		return doRequest(url, conTimeout, readTimeout, useCookies, useCache, null, HTTP_GET);
	}

	//issue a POST request and retrieve data from a JSON service using the defaults for all parameters
	public Object doPost(String url, JsonJavaObject postData) throws Exception {
		return doPost(url, defaultUseCookies, defaultUseCache, postData);
	}
	
	//issue a POST request and retrieve data from a JSON service using the defaults for all other parameters
	public Object doPost(String url, ArrayList<String> useCookies, boolean useCache, JsonJavaObject postData) throws Exception {
		return doPost(url, defaultConnectionTimeout, defaultReadTimeout, useCookies, useCache, postData);
	}
	
	//issue a POST request and retrieve data from a JSON service providing full list of parameters (except postData)
	public Object doPost(String url, int conTimeout, int readTimeout, ArrayList<String> useCookies, boolean useCache, JsonJavaObject postData) throws Exception {
		return doRequest(url, conTimeout, readTimeout, useCookies, useCache, postData, HTTP_POST);
	}

	// issue a PATCH request and retrieve data from a JSON service using the defaults for all parameters
	public Object doPatch(String url, JsonJavaObject patchData) throws Exception {
		return doPatch(url, defaultUseCookies, defaultUseCache, patchData);
	}

	// issue a PATCH request and retrieve data from a JSON service using the defaults for all other parameters
	public Object doPatch(String url, ArrayList<String> useCookies, boolean useCache, JsonJavaObject patchData) throws Exception {
		return doPatch(url, defaultConnectionTimeout, defaultReadTimeout, useCookies, useCache, patchData);
	}

	// issue a PATCH request and retrieve data from a JSON service providing full list of parameters (except postData)
	public Object doPatch(String url, int conTimeout, int readTimeout, ArrayList<String> useCookies, boolean useCache, JsonJavaObject patchData) throws Exception {
		return doRequest(url, conTimeout, readTimeout, useCookies, useCache, patchData, HTTP_PATCH);
	}
	
	//2023-03-17, dko: einfache PUT Implementation
	public Object doPut(String url, JsonJavaObject postData) throws Exception {
		return doRequest(url, defaultConnectionTimeout, defaultReadTimeout, defaultUseCookies, defaultUseCache, postData, HTTP_PUT);
	}
	
	//2023-12-06, lma: einfache DELETE Implementation
	//ACHTUNG damit das funktioniert muss auch in doRequest was geändert werden!!!!!  Zeile 522 und danach vorher war da eine If else 
	public Object doDelete(String url) throws Exception {
		return doRequest(url, defaultConnectionTimeout, defaultReadTimeout, defaultUseCookies, defaultUseCache, null, HTTP_DELETE);
	}
	
	//GET/POST Core: issue a GET request without postData or a POST request with JSON postData to a JSON service and get JSON response data
	private Object doRequest(String url, int conTimeout, int readTimeout, ArrayList<String> useCookies, boolean useCache, JsonJavaObject postData, String requestType) throws Exception {

		//init result data
		Object resultData = null;
		
		lastResponseCode = 0;
		lastResponseMessage = "";
		lastRequestRestData = new JsonJavaObject();
		
		doLog("doRequest() REST: " + url, 1);
		doLog("  parameters: conTimeout=" + conTimeout + ", readTimeout=" + readTimeout + ", applyRequestCookies=" + useCookies.toString() + ", useCache=" + useCache, 2);
		
		//Vorbereitungen
		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();

		HttpsURLConnection urlCon = null;
		
		try{
		
			//open new url connection and set properties
			URL myUrl = new URL(url);
			
			//2020-02-20, dko: Default=TLS 1.0, aktiviere alle SSL-Protokolle (SSL3, TLS1,1.1,1.2)
			//siehe https://www.ibm.com/support/knowledgecenter/en/SSYKE2_7.1.0/com.ibm.java.security.component.71.doc/security-component/jsse2Docs/protocols.html#protocols
			//siehe http://blog.nashcom.de/nashcomblog.nsf/dx/traveler-server-not-connecting-to-microsoft-sql-server-using-only-tls-1.2.htm?opendocument&comments
			//SSLContext sc = SSLContext.getInstance("SSL");
			
			/*
			// Install the all-trusting trust manager instead of 'SSLContexts.createDefault()'
			SSLContext sc = SSLContext.getInstance("SSL");
			
			if (this.trustAllCerts) {
				
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs,
							String authType) {
					}

					public void checkServerTrusted(X509Certificate[] certs,
							String authType) {
					}
				} };
				
				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				
			} else {
				sc.init(null, null, new java.security.SecureRandom());
			}
			
			/*
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
					sc,
					new String[] { "TLSv1.2" }, 
					null, 
					SSLConnectionSocketFactory.getDefaultHostnameVerifier());
			*/
			
			//2021-10-15, dko: use predefined context and facorty
			initSSLContext(false);
			
			//URLConnection urlCon = myUrl.openConnection();
			urlCon = (HttpsURLConnection) myUrl.openConnection();
			//urlCon.setSSLSocketFactory(sc.getSocketFactory());
			
			urlCon.setSSLSocketFactory(restSSLSocketFactory);
			
			if (authMethod==Auth.BASIC) {
				doLog("Using Basic Auth", 2);
				//String header = "Basic " + new String(Base64.encode((basicAuthUsername + ":" + basicAuthPassword).getBytes()));
				String header = "Basic " + Base64.getEncoder().encodeToString((basicAuthUsername + ":" + basicAuthPassword).getBytes());
				urlCon.addRequestProperty("Authorization", header);
			} else if (authMethod==Auth.BEARER) {
				doLog("Using Bearer Auth", 2);
				String header = "Bearer " + bearerAuthToken;
				urlCon.addRequestProperty("Authorization", header);
			}
			
			//apply all current cookies to the new request (keep authentication if same server/server group)
			@SuppressWarnings("unchecked")
			Map<String, Cookie> cookies = externalContext.getRequestCookieMap();
			
			for(Map.Entry<String, Cookie> entry : cookies.entrySet()){
				if (useCookies.contains(entry.getKey().toLowerCase())) {
					urlCon.addRequestProperty ("Cookie" , entry.getKey() + "=" + entry.getValue().getValue());
					doLog("Add cookie: " + entry.getKey() + "=" + entry.getValue().getValue().toString(), 2);
				} else {
					doLog("Ignore cookie: " + entry.getKey() + "=" + entry.getValue().getValue().toString(), 3);
				}
			}

			//2021-09-30, dko: vormals empfangene Cookies anwenden
			applyReceivedCookies(urlCon);
			
			urlCon.setConnectTimeout(conTimeout);
			urlCon.setReadTimeout(readTimeout);
			urlCon.setUseCaches(useCache);
			
			//Request Headers
			for (Map.Entry<String,Object> param : requestHeader.entrySet()) {
				urlCon.addRequestProperty(param.getKey(), String.valueOf(param.getValue()));
	        }
			
			//2021-10-14, dko: wird von bestimmten APIs benötigt (z.B. MSGraph), sonst 400 BAD REQUEST oder 500 internal error
			if (urlCon.getRequestProperty("Accept")==null) urlCon.addRequestProperty("Accept", "*/*"); 
			
			//2022-05-27, dko: User-Agent und Accept-Encoding-Defaults hinzufügen (für CompanyHouse API fehlte da was)
			if (urlCon.getRequestProperty("User-Agent")==null) urlCon.addRequestProperty("User-Agent", this.getClass().getSimpleName());
			if (urlCon.getRequestProperty("Accept-Encoding")==null) urlCon.addRequestProperty("Accept-Encoding", "identity");

			//2023-12-11, dko: Request-Method immer setzen
			urlCon.setRequestMethod(requestType);
			
			//if (postData==null || requestType.equals(HTTP_GET)) {
				//issue a GET: no need to set the properties explicit because these are the defaults
				//urlCon.setRequestMethod(HTTP_GET);	= default
				//urlCon.setDoInput(true);	= default
				//urlCon.setDoOutput(false);	= default
				
			if (postData!=null) {

				//Default, auch, wenn postData leer ist, verwenden
				String contentType = "application/json";
				
				byte[] utf8JsonString = new byte[]{}; // myvar = "Any String you want".getBytes();
				
				if (postType==PostType.JSON) {
	
					//first of all transform object to String as we need it for determine the content length later
					utf8JsonString = gson.toJson(postData).getBytes(encoder.charset());
					
				} else if (postType==PostType.X_WWW_FORM_URLENCODED) {
					
					contentType = "application/x-www-form-urlencoded";
					
					//JSON-Objekt (wir nehmen FLAT an) zu einem String umbauen
					StringBuilder postDataString = new StringBuilder();
					
					for (Entry<String, Object> entry:postData.entrySet()) {
						
						if (postDataString.length() > 0) postDataString.append("&");
				        
				        postDataString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
				        postDataString.append("=");
				        postDataString.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
						
					}
					
					utf8JsonString = postDataString.toString().getBytes(encoder.charset());

				}
				
				//WRITE POST
				//urlCon.setRequestMethod(requestType);	//hier: falsch, muss außerhalb gesetzt werden
				urlCon.setDoOutput(true);
	
				urlCon.setRequestProperty("Cache-Control", "no-cache");
				urlCon.setRequestProperty("Content-Type", contentType + ";charset=" + encoding);
				urlCon.setRequestProperty("Content-Length", String.valueOf(utf8JsonString.length));

				//for(Entry<String, List<String>> entry : urlCon.getRequestProperties().entrySet()){
				//	doLog("REQUEST Header fields: " + entry.getKey() + ": " + entry.getValue().toString(), 3);
				//}
				
				doLog("  " + requestType + " details: " + contentType + "#"+ encoding + ", length=" + String.valueOf(utf8JsonString.length), 2);
				
				//Convert to JSON and output to the stream as a byte stream to workaround non-utf-8-characters
				DataOutputStream out = new DataOutputStream(urlCon.getOutputStream());
				out.write(utf8JsonString, 0, utf8JsonString.length);
				
				//2017-05-30, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
				out.close();
				
			}
			
		//RESPONSE

			lastResponseCode = urlCon.getResponseCode();
			lastResponseMessage = urlCon.getResponseMessage();
			
			doLog("urlCon.getResponseCode(): " + lastResponseCode + ": " + lastResponseMessage, 3);
			
			//try to read the content type to extract content encoding type
			String contentType = urlCon.getContentType();
			if (contentType==null) contentType="";
			doLog("urlCon.getContentType(): " + contentType, 2);
			
			//debug Header fields
			for(Entry<String, List<String>> entry : urlCon.getHeaderFields().entrySet()){
				doLog("Header fields: " + entry.getKey() + ": " + entry.getValue().toString(), 3);
				
				//Handle Set-Cookie-directives
				if (entry.getKey()!=null) {
					if (entry.getKey().equalsIgnoreCase("set-cookie")) {
						
						// let's determine the domain from where these cookies are being sent
						String domain = getDomainFromHost(urlCon.getURL().getHost());
						
						HashMap<String, HashMap<String,String>> domainStore; // this is where we will store cookies for this domain
						
						// now let's check the store to see if we have an entry for this domain
						if (receivedCookiesStore.containsKey(domain)) {
						    // we do, so lets retrieve it from the store
						    domainStore = receivedCookiesStore.get(domain);
						} else {
						    // we don't, so let's create it and put it in the store
						    domainStore = new HashMap<>();
						    receivedCookiesStore.put(domain, domainStore);    
						}
						
						/*
						LinkedList<String> hlp = new LinkedList<String>();
						hlp.addAll(entry.getValue());
						hlp.add("DOMRELAYSTATE=; expires=Sat, 01-Jan-2000 00:00:01 GMT; Path=/; HttpOnly");
						hlp.add("Test1=; Path=/; HttpOnly");
						hlp.add("Test2");
						*/
						
						for(String newCookie : entry.getValue()) {

							HashMap<String, String> cookie = new HashMap<>();
							StringTokenizer st = new StringTokenizer(newCookie, ";");
							
							// the specification dictates that the first name/value pair
							// in the string is the cookie name and value, so let's handle
							// them as a special case: 
							
							if (st.hasMoreTokens()) {
							    String token  = st.nextToken();
							    doLog("token: " + token, 4);
							    
							    String name;
							    String value;
							    
							    if (token.indexOf(NAME_VALUE_SEPARATOR)!=-1) {
							    	//Name/Value-Pärchen
							    	name = token.substring(0, token.indexOf(NAME_VALUE_SEPARATOR)).trim();
							    	value = token.substring(token.indexOf(NAME_VALUE_SEPARATOR) + 1, token.length());
							    } else {
							    	//kein Name/Value-Pärchen
							    	name = token.trim();
							    	value = null;
							    }
							    
							    domainStore.put(name, cookie);
							    cookie.put(COOKIE_NNAME_TAG + name, value);
							}
					    
							//other properties like path, expires etc.
							while (st.hasMoreTokens()) {
							    String token  = st.nextToken();
							    doLog("token: " + token, 4);
							    if (token.indexOf(NAME_VALUE_SEPARATOR)!=-1) {
							    	cookie.put(token.substring(0, token.indexOf(NAME_VALUE_SEPARATOR)).toLowerCase().trim(), token.substring(token.indexOf(NAME_VALUE_SEPARATOR) + 1, token.length()));
							    } else cookie.put(token.toLowerCase().trim(), null);
							}
							
							doLog("..... stored cookie for: " + domain + ": " + cookie.toString(), 3);
							
						}
						
					}	//key=set-cookie
					
				}	//entry key != null
				
			}
			
			//try to read encoding
			String encoding = urlCon.getContentEncoding();
			if (encoding==null) encoding="";
			doLog("urlCon.getContentEncoding(): " + encoding, 3);
			
			//parse charset from content-type 
			String encodingCharset = "";

			String[] values = contentType.split(";"); // values.length should be 2
			
			for (String value : values) {
			    value = value.trim();

			    if (value.toLowerCase().startsWith("charset=")) {
			        encodingCharset = value.substring("charset=".length());
			    	doLog("parsed encodingCharset from content type: " + value, 3);
			    }
			}

			if (encodingCharset.equals("")) {
				encodingCharset = "UTF-8"; //Assumption
				doLog("encodingCharset fallback: UTF-8", 3);
			}
			
			doLog("using encodingCharset: " + encodingCharset, 2);
			
			//create decoder for that encoding
	        CharsetDecoder decoder = Charset.forName(encodingCharset).newDecoder();
	        
			//according to tips on StackOverflow, the constructor with String should not be used, since it doesn't fire an exception if something goes wrong
			//InputStreamReader isR = new InputStreamReader(is, "UTF-8");

			//get the connections input stream and decorate it with a reader using the correct encoding
	        boolean isSuccess = false;
	        InputStream is;
	        if (lastResponseCode >= 200 && lastResponseCode < 300) {
	        	is = urlCon.getInputStream();
	        	isSuccess = true;
	        } else {
	        	is = urlCon.getErrorStream();
	        }
	        
	        if (is!=null) {

	        	InputStreamReader isR = null;
	        	
	        	//2022-05-30, dko: handle gzip encoding
	        	if (encoding.equalsIgnoreCase("gzip")) {
	        		//System.out.println("Using GZIPInputStream");
	        		isR = new InputStreamReader(new GZIPInputStream(is), decoder);
	        	} else {
	        		isR = new InputStreamReader(is, decoder);
	        	}

		        try {
					//use GSON fromJson and pass the reader as source, create a JsonJavaObject from the JSON String
					//data = (JsonJavaObject) gson.fromJson(isR, JsonJavaObject.class);
					
		        	JsonJavaFactory factory = JsonJavaFactory.instanceEx;
		        	
		        	//2021-03-15, dko: ist contentType="application/json", dann wird ein JSON-Body erwartet, der normal zu parsen ist. Ansonsten soll das Parsing probiert, aber im Fehlerfall ein leeres JSON liefern
		        	//2021-07-05, dko: unabh?ngig vom contentType parsen, bei einem Fehler mit contentLength>0 gehen wir von einem parse-Error aus, ansonsten von einer Antwort ohne Content
		        	
	        		//expects JSON object: raise error, if it's not parsing correctly
	        		try {
	        			resultData = JsonParser.fromJson(factory, isR);
	        		} catch (JsonException e) {
	        		
	        			//Mehr als 0 Byte im Content = eine Antwort mit Content
	        			if (isSuccess && urlCon.getContentLengthLong()>0) {
	        				//ResponseCode = OK, aber es kam etwas zurück, was JSON sein sollte: Dann echter Parse-Error
	        				//System.err.println("Error Message => " + lastResponseMessage);
	        				//Vorschlag: Statt einen parsing Error zu schmeißen wäre es doch sinvoller den nicht JSON Response Text einfach in ein Json zu packen 
	        				//Viele APIs geben leider bei manchen Fehlern nur Text zurück... 
	        				//resultData = new JsonJavaObject("error", lastResponseMessage);
	        				throw e;
	        			} else {
	        				//A) content scheint leer zu sein (Response ohne Content) 
	        				//B) content ist nicht leer, aber es ist ein Response-Fehlerocde 
	        				//-> resultData mit einem neuen Object instanziieren, damit bei aufrufender Prozedur kein null zurückkommt
	        				doLog("Response ohne Content", 3);
	        				resultData = new Object();
	        			}
	        		}
		        } finally {
					//2017-05-30, dko: close, see http://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
					isR.close();
		        }
	
	        }
	        
		} catch (IOException io) {
				
			//close the error stream (to avoid issues with keep-alive)
			//we could read it but not needed for now
			if (urlCon!=null && urlCon.getErrorStream()!=null) urlCon.getErrorStream().close();
			
			throw io;
			
		} finally {
			
			//Rest-Infos immer versuchen anzuh?ngen, bei Fehlschlag still weiter
			try {
				
				//add some diagnostic properties to data object
				lastRequestRestData = new JsonJavaObject();
				
				lastRequestRestData.putJsonProperty("ResponseCode", lastResponseCode);
				lastRequestRestData.putJsonProperty("ResponseMessage", lastResponseMessage);
	
				//Header fields
				JsonJavaObject responseHeader = new JsonJavaObject();
				for(Entry<String, List<String>> entry : urlCon.getHeaderFields().entrySet()){
					//avoid nullpointer exception: e.g. HTTP 200/OK has no key
					if (entry.getKey()!=null) {
						JsonJavaArray vArr = new JsonJavaArray();
						vArr.add(entry.getValue().toArray(new String[0]));
						//String[] stringArray = entry.getValue().toArray(new String[0]);
						responseHeader.putJsonProperty(entry.getKey(), vArr);
					}
				}
				
				lastRequestRestData.put("ResponseHeaderFields", responseHeader);
	
			} finally {
				//2017-05-30, dko: probeweise hinzugef?gt
				urlCon.disconnect();
			}
		}
		
		return resultData;
		
	}

	//source: http://www.hccp.org/java-net-cookie-how-to.html
	private String getDomainFromHost(String host) {
		if (host.indexOf(".") != host.lastIndexOf(".")) {
			return host.substring(host.indexOf(".") + 1);
		} else {
			return host;
		}
	}

	private boolean isNotExpired(String cookieExpires) {

		if (cookieExpires == null) return true;
		
		//use HttpCookie helper to decode "expires" without hassle
		cookieExpires = "Dummy=1; path=/; expires=" + cookieExpires;
		List<HttpCookie> parse = HttpCookie.parse(cookieExpires);
		return !parse.get(0).hasExpired();
		
	}

	//source: http://www.hccp.org/java-net-cookie-how-to.html
	private boolean comparePaths(String cookiePath, String targetPath) {
		if (cookiePath == null) {
			return true;
		} else if (cookiePath.equals("/")) {
			return true;
		} else if (targetPath.regionMatches(0, cookiePath, 0, cookiePath.length())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Prior to opening a URLConnection, calling this method will set all
	 * unexpired cookies that match the path or subpaths for thi underlying URL
	 *
	 * The connection MUST NOT have been opened method or an IOException will be
	 * thrown.
	 *
	 * @param conn
	 *            a java.net.URLConnection - must NOT be open, or IOException
	 *            will be thrown
	 * @throws java.io.IOException
	 *             Thrown if conn has already been opened.
	 */
	private void applyReceivedCookies(HttpsURLConnection urlCon) throws Exception {

		// let's determine the domain and path to retrieve the appropriate cookies
		URL url = urlCon.getURL();
		String domain = getDomainFromHost(url.getHost());

		HashMap<String, HashMap<String,String>> domainStore = receivedCookiesStore.get(domain);
		if (domainStore == null) return;
		
		String path = url.getPath();
		
		Iterator cookieNames = domainStore.keySet().iterator();
		while (cookieNames.hasNext()) {
			String cookieName = (String) cookieNames.next();
			HashMap<String,String> cookie = domainStore.get(cookieName);
			// check cookie to ensure path matches and cookie is not expired
			// if all is cool, add cookie to header string
			if (comparePaths(cookie.get(PATH), path) && isNotExpired(cookie.get(EXPIRES))) {
				
				//Cookies ohne Value werden ohne = übergeben
				String cookieStr = cookieName;
				if (cookie.get(COOKIE_NNAME_TAG + cookieName)!=null) cookieStr += "=" + cookie.get(COOKIE_NNAME_TAG + cookieName);
				
				urlCon.addRequestProperty("Cookie", cookieStr);
				doLog("Add received cookie: " + cookieStr, 2);
			}
		}
		
	}
	
}
