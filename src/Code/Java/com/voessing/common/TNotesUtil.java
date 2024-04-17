package com.voessing.common;

import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.EmbeddedObject;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.View;

import org.openntf.domino.xsp.XspOpenLogUtil;

import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;

public class TNotesUtil {

	private static final String DEFAULT_STAT_LOG_DB = "SelfServiceLog.nsf";
	
	//original von org.iconuk.servicesdemo.util, function incinerate()
	//@SuppressWarnings({ "rawtypes", "unchecked" })
	@SuppressWarnings({ "unchecked" })
	public static void recycleNotesObject(final Object... args) {

		for (Object o : args) {
			if (o != null) {
				if (o instanceof lotus.domino.Base) {
					try {
						((Base) o).recycle();
					} catch (Throwable t) {
						// who cares?
					}
				} else if (o instanceof Map) {
					Set<Map.Entry> entries = ((Map) o).entrySet();
					for (Map.Entry<?, ?> entry : entries) {
						recycleNotesObject(entry.getKey(), entry.getValue());
					}
				} else if (o instanceof Collection) {
					Iterator i = ((Collection) o).iterator();
					while (i.hasNext()) {
						Object obj = i.next();
						recycleNotesObject(obj);
					}
				} else if (o.getClass().isArray()) {
					try {
						Object[] objs = (Object[]) o;
						for (Object ao : objs) {
							recycleNotesObject(ao);
						}
					} catch (Throwable t) {
						// who cares?
					}
				}
			}
		}
	}


	public static void stdErrorHandler(Exception e) {
		stdErrorHandlerCore(e, null);
	}
	
	public static void stdErrorHandler(Exception e, String additionalMessage) {
		stdErrorHandlerCore(e, additionalMessage);
	}

	private static void stdErrorHandlerCore(Exception e, String message) {
		try {
			if (message!=null) System.err.println("stdErrorHandlerCore(): " + message);
			e.printStackTrace();

			//XspOpenLogUtil.getXspOpenLogItem().logError(e);
			//if (message!=null) XspOpenLogUtil.getXspOpenLogItem().logEvent(null, message, Level.WARNING, null);
			
			String msg = e.getMessage();
			if (msg == null) msg = e.getClass().getCanonicalName();
			
			//2021-04-29, dko: entfernt, da vermutlich gar nicht notwendig
			//XspOpenLogUtil.getXspOpenLogItem().addFacesMessage(null, msg);
			
			//if (message!=null) msg += "; addMessage=" + message;
			if (message!=null) msg += "\n" + message;
			XspOpenLogUtil.getXspOpenLogItem().logErrorEx(e, msg, Level.WARNING, null);
			
		} catch (Throwable t) {
			//just make sure we handle any errors here
			System.err.println("ERROR in stdErrorHandler()");
			t.printStackTrace();
		}		
	}
	
	public static String dateToYYYYMMDD(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		return sdf.format(date);
	}
	
	public static void logEvent(String message) {
		XspOpenLogUtil.logEvent(null, message, Level.INFO, null);
	}

	/**
	 * log an event to openlog if doLog is true 
	 * @param message
	 * @param doLog
	 */
	public static void logEvent(String message, boolean doLog) {
		if (doLog) XspOpenLogUtil.logEvent(null, message, Level.INFO, null);
	}

	/**
	 * log an information via system.out.println() 
	 * @param msg
	 * @param doLog
	 */
	public static void conInfo(String msg, boolean doLog) {
		if (doLog) System.out.println("#conInfo: " + msg);
	}

	/**
	 * log an information via system.out.println() 
	 * @param msg
	 */
	public static void conInfo(String msg) {
		conInfo(msg, true);
	}

	/**
	 * log a warning via system.out.println() 
	 * @param msg
	 * @param doLog
	 */
	public static void conWarn(String msg, boolean doLog) {
		if (doLog) System.out.println("#conWarn: " + msg);
	}
	
	/**
	 * log a warning via system.out.println() 
	 * @param msg
	 */
	public static void conWarn(String msg) {
		conWarn(msg, true);
	}

	/**
	 * log an error via system.err.println() 
	 * @param msg
	 * @param doLog
	 */
	public static void conError(String msg, boolean doLog) {
		if (doLog) System.err.println("#conError: " + msg);
	}
	
	/**
	 * log an error via system.err.println() 
	 * @param msg
	 */
	public static void conError(String msg) {
		conError(msg, true);
	}
	
	/**
	 * log a debug message via system.out.println() 
	 * @param msg
	 * @param doLog
	 */
	public static void conDebug(String msg, boolean doLog) {
		if (doLog) System.out.println("#conDebug: " + msg);
	}
	
	/**
	 * log a debug message via system.out.println() 
	 * @param msg
	 */
	public static void conDebug(String msg) {
		conDebug(msg, true);
	}
	
	//gibt die Datei-Extension inkl. "." zur�ck oder Leerstring, wenn keine da ist
	public static String getFileExtension(String fileName) {
	    try {
	        return fileName.substring(fileName.lastIndexOf("."));
	    } catch (Exception e) {
	        return "";
	    }
	}
	

	//Gibt die genannte Anlage zur�ck. Ist kein genannt, dann wird die erste zur�ckgegeben
	@SuppressWarnings("unchecked")
	public static FileInputStream getAttachmentAsFileInputStream(String serverName, String dbName, String viewName, Vector<Object> keys, StringBuilder attachmentFilename) throws Exception {
		
		Database db = null;
		View view = null;
		Document doc = null;
		EmbeddedObject eo = null;
		
		boolean useCurrentDatabase = (serverName==null || dbName==null) || (serverName!=null && serverName.equalsIgnoreCase("") && dbName!=null && dbName.equalsIgnoreCase(""));
		
		try {
			NotesContext nct = NotesContext.getCurrent();
			Session session = nct.getCurrentSession();
			
			if (useCurrentDatabase) {
				db = session.getCurrentDatabase();
			} else db = session.getDatabase(serverName, dbName);
			

			if (!(db.isOpen())) {
				db.open();
			}
			
			view = db.getView(viewName);
			view.refresh();
			
			doc = view.getDocumentByKey(keys, true);
			
			String attachmentName = attachmentFilename.toString();
			
			if (attachmentName.equalsIgnoreCase("")) {
				//if no AttachmentName is provided, get the first Attachment
				Vector<String> attachments = session.evaluate("@AttachmentNames", doc);
				eo = doc.getAttachment(attachments.get(0));
				
				attachmentName = eo.getName();
				attachmentFilename.setLength(0);
				attachmentFilename.append(attachmentName);
				
			} else{
				eo = doc.getAttachment(attachmentName);
			}

			//may raise an error if attachment is not found
			return (FileInputStream) eo.getInputStream();

		} catch (Exception e) {
			
			String dbgInfo = "";
			
			try {
				dbgInfo = "DB " + db.getServer() + "!!" + db.getFilePath();
				dbgInfo += "; View: " + viewName;
				dbgInfo += "; Keys: " + keys.toString();
				System.err.println("ERROR: TNotesUtil.getAttachmentAsFileInputStream: " + dbgInfo);
				TNotesUtil.logEvent("getAttachmentAsFileInputStream() Error Details: " + dbgInfo) ;
			} catch (Exception e1) {
				//do nothing
			}
			
			//Rethrow
			throw e;
			
		} finally {
			TNotesUtil.recycleNotesObject(eo, doc, view, ((useCurrentDatabase) ? null : db));
		}
		
	}

	public static double roundConvergent(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_EVEN);
	    return bd.doubleValue();
	}
	

	/**
	* Rundet den übergebenen Wert auf die Anzahl der übergebenen Nachkommastellen
	*
	* @param value ist der zu rundende Wert.
	* @param decimalPoints ist die Anzahl der Nachkommastellen, auf die gerundet werden soll.
	*/
	public static double round(double value, int decimalPoints) {
		double d = Math.pow(10, decimalPoints);
		return Math.round(value * d) / d;
	}

	//von SSUtil übernommen 
	public static StringBuffer removeUTFCharacters(String data){
		Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
		Matcher m = p.matcher(data);
		StringBuffer buf = new StringBuffer(data.length());
		while (m.find()) {
			String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
			m.appendReplacement(buf, Matcher.quoteReplacement(ch));
		}
		m.appendTail(buf);
		return buf;
	}

	//Vector zu einer ArrayList konvertieren
    public static ArrayList<Object> vectorToArrayListOld(Vector<Object> vector){
    	if (vector == null){return null;}
	    return new ArrayList<Object>(vector);
	}

	//Vector zu einer ArrayList konvertieren
    @SuppressWarnings("unchecked")
	public static ArrayList vectorToArrayList(Vector vector){
    	if (vector == null){return null;}
	    return new ArrayList(vector);
	}

    public static JsonJavaArray jsonJavaArrayFromArrayList(ArrayList<JsonJavaObject> list) {
    	JsonJavaArray jArr = new JsonJavaArray();
    	jArr.addAll(list);
		return jArr;
    }
    
	public static String createSSAppObjectLink(String strObjectType, String strObjUnid) {
		return "https://xapps.voessing.de/Selfservice.nsf/Object.xsp?option="+strObjectType+"&unid="+strObjUnid;
	}
	
	public static void logStatDb(String logMsg) {
		logStatDb(null, null, logMsg, null, null);
	}

	public static void logStatDb(String logMsg, String optAdditionalInfo) {
		logStatDb(null, null, logMsg, null, optAdditionalInfo);
	}

	public static void logStatDb(String optCategory, String logMsg, Long optDuration, String optAdditionalInfo) {
		logStatDb(null, optCategory, logMsg, optDuration, optAdditionalInfo);
	}
	
	public static void logStatDb(String optTargetDBName, String optCategory, String logMsg, Long optDuration, String optAdditionalInfo) {
		
		//exit if no log provided
		if (logMsg==null || logMsg.isEmpty()) return;
		
		Database db = null;
		Document doc = null; 
		DateTime dt = null;
		
		try {

			NotesContext nct = NotesContext.getCurrent();
			Session session = nct.getCurrentSession();

			db = session.getDatabase(session.getCurrentDatabase().getServer(), (optTargetDBName==null || optTargetDBName.isEmpty()) ? DEFAULT_STAT_LOG_DB : optTargetDBName);
			
			if (db.isOpen()) {
				
				doc = db.createDocument();
				
				doc.replaceItemValue("Form", "Log");

				dt = session.createDateTime(new Date());
				
				doc.replaceItemValue("Time", dt);
				doc.replaceItemValue("User", session.getEffectiveUserName());
				doc.replaceItemValue("Category", (optCategory==null) ? "" : optCategory);
				doc.replaceItemValue("Message", logMsg);
				doc.replaceItemValue("Duration", (optDuration==null) ? "" : optDuration);
				doc.replaceItemValue("Additional", (optAdditionalInfo==null) ? "" : optAdditionalInfo);
				
				doc.save(true, false);
			}
			
		} catch (Exception e) {
			//silent - catch all
			//TNotesUtil.stdErrorHandler(e, logMsg);
		} finally {
			recycleNotesObject(dt, doc, db);
		}
		
	}
	
	public static Item replaceDocItemValue(Document doc, String itemName, Date javaDate) throws NotesException {
		
		DateTime dt = null;
		
		try {
			dt = doc.getParentDatabase().getParent().createDateTime(javaDate);
			return doc.replaceItemValue(itemName, dt);
		} finally {
			TNotesUtil.recycleNotesObject(dt);
		}
		
	}

	/**
	 * Von einem NotesItem den ersten Wert als Java Datum zurückgeben, wenn das Feld vorhanden und ein DATETIME-Feld ist. ansonsten 0
	 * 
	 * @param doc
	 * @param itemName
	 * @throws NotesException 
	 */
	public static Date getFirstItemValueAsDate(Document doc, String itemName) throws NotesException {
		
		Item it = null;
		
		try {
			if (doc.hasItem(itemName)) {
				it = doc.getFirstItem(itemName);
				if ( (it!=null) && (it.getType() == Item.DATETIMES) ) {
					return it.getDateTimeValue().toJavaDate();
				}
			}
		} finally {
			recycleNotesObject(it);
		}

		return null;
		
	}
	
	public static Document createNewDocument(Database db, String formName) throws NotesException {
		
		Document newDoc = null;
		
		if (db!=null && db.isOpen()) {
			
			newDoc = db.createDocument();
			newDoc.replaceItemValue("Form", formName);
			newDoc.replaceItemValue("$ConflictAction", "1");

		}

		return newDoc;
		
	}

	//INDEV: ggf. je nach Typ ausprogrammieren
	public static boolean replaceUnequalItemSingleString(Document doc, String itemName, String singleValue) throws NotesException {
		//In Notes.Items wird offenbar 0D0A gespeichert. Evtl. auch nur unter Windows? Jedenfalls beim Inhaltsvergleich mit 0D0A beidseitig ersetzen durch 0A
		if (!doc.getItemValueString(itemName).replace("\r\n", "\n").equals(singleValue.replace("\r\n", "\n"))) {
			doc.replaceItemValue(itemName, singleValue). recycle();
			return true;
		} else return false;
	}
	
	
	/**
	 * 
	 * @param doc
	 * @param itemName is the name of the item to be replaced in the doc
	 * @param value  can be from type Number(Integer, Double, ...), String, DateTime and Vectors filled with these types 
	 * @return returns true if the document entry has been updated (item != value)
	 * @throws NotesException
	 */
	public static <T> boolean replaceUnequalItem(Document doc, String itemName, T value) throws NotesException {
		if(!equalsItem(doc, itemName, value)){
			doc.replaceItemValue(itemName, value). recycle();
			return true;
		} else return false;
	}
	
	/**
	 * 
	 * @param doc
	 * @param itemName is the name of the item searched in the provided doc
	 * @param value  can be from type Number(Integer, Double, ...), String, DateTime and Vectors filled with these types 
	 * @return returns true if the value and the found item are equal
	 * @throws NotesException
	 */
	/**
	 * 
	 * @param doc
	 * @param itemName is the name of the item searched in the provided doc
	 * @param value    can be from type Number(Integer, Double, ...), String,
	 *                 DateTime, RichTextItems and Vectors filled with these types
	 * @return returns true if the value and the found item are equal
	 * @throws NotesException
	 */
	@SuppressWarnings("unchecked")
	public static <T> boolean equalsItem(Document doc, String itemName, T value) throws NotesException {

		if (value == null) {
			throw new NotesException(1337, "The provided value was null");
		} else if (!isValidInput(value)) {
			throw new NotesException(1337, "Unsupported input type!");
		}
		// get the current value as Vector
		Vector<T> current = doc.getItemValue(itemName);
		// set the input (value) as vector if not already
		Vector<T> input = null;
		if (!(value instanceof Vector)) {
			input = new Vector<>();
			input.add(value);
		} else {
			// be sure to make a copy as we will modify the input in the case of string
			// for other datatypes we wont be changing the values of input
			input = new Vector<>((Vector<T>) value);
		}

		// base case
		if (current.size() != input.size()) {
			return false;
		}

		// in case T is String we need to replace /r/n to /n as domino stores only /n
		// and other functions generate /r/n
		// this does nothing if T is not a String
		handleStrings(current, input);

		if (isVectorOfNumber(input)) {
			Vector<Double> inputD = toDoubleVector((Vector<Number>) input);
			return current.equals(inputD);
			// if we get an DateTime vector we need to convert both value and the found item
			// to Vector<Date>
		} else if (isVectorOfDateTime(input)) {
			// convert the given value to a date vector
			Vector<Date> inputJD = new Vector<>();
			for (DateTime date : (Vector<DateTime>) input) {
				inputJD.add((Date) toJavaDate(date));
			}
			// convert the found item to a date vector
			Vector<Date> currentJD = new Vector<>();
			for (DateTime date : (Vector<DateTime>) current) {
				currentJD.add((Date) toJavaDate(date));
			}
			return inputJD.equals(currentJD);
		} else if (isVectorOfRTI(input)) {
			// RichTextItems can only be single value
			if (!current.isEmpty() && !input.isEmpty()) {
				RichTextItem currentItem = null;
				String inputUFS = null;
				String currentUFS = null;
				try {
					// we have to get the stored RTI
					currentItem = (RichTextItem) doc.getFirstItem(itemName);
					inputUFS = ((RichTextItem) input.get(0)).getUnformattedText();
					currentUFS = currentItem.getUnformattedText();
				} catch (Exception e) {
					return false;
				} finally {
					currentItem.recycle();
				}
				return currentUFS.equals(inputUFS);
			}
			return false;
		}
		// if we have no special case we can compare them
		return current.equals(input);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> boolean isValidInput(T value) {
		if (value instanceof Vector<?>) {
			if (((Vector<?>) value).isEmpty()) {
				return true;
			}
			for (T v : (Vector<T>) value) {
				if (v instanceof Number || v instanceof String || v instanceof DateTime || v instanceof RichTextItem)
					return true;
			}
		}
		return value instanceof Number || value instanceof String
				|| value instanceof DateTime || value instanceof RichTextItem;
	}

	private static Vector<Double> toDoubleVector(Vector<Number> input) {
		Vector<Double> res = new Vector<>();
		for (Number i : (Vector<Number>) input) {
			res.add(i.doubleValue());
		}
		return res;
	}

	public static boolean isVectorOfRTI(Vector<?> value) {
		return !value.isEmpty() && value.get(0) instanceof RichTextItem;
	}

	public static boolean isVectorOfString(Vector<?> value) {
		return !value.isEmpty() && value.get(0) instanceof String;
	}

	public static boolean isVectorOfDateTime(Vector<?> value) {
		return !value.isEmpty() && value.get(0) instanceof DateTime;
	}

	public static boolean isVectorOfNumber(Vector<?> value) {
		return !value.isEmpty() && value.get(0) instanceof Number;
	}

	private static <T> void handleStrings(Vector<T> v1, Vector<T> v2) {
		tryStrReplace(v1);
		tryStrReplace(v2);
	}

	@SuppressWarnings("unchecked")
	private static <T> void tryStrReplace(Vector<T> vec) {
		try {
			((Vector<String>) vec).replaceAll(v -> v.replaceAll("\r\n", "\n"));
		} catch (Exception e) {
			// honsetly i dont care :^) i did catch you already you cant make code make nono
			// anymore
		}
	}
	
	@SuppressWarnings("unchecked")
	/**
	 * 
	 * @param obj allows to input a DateTime or Vector<DateTime>
	 * @return the converted versions > Date or Vector<Date> if the provided Object cannot be converted it returns null
	 * @throws NotesException
	 */
	public static Object toJavaDate (Object obj) throws NotesException{
		if(obj instanceof DateTime){
			return ((DateTime) obj).toJavaDate();
		}
		else if(obj instanceof Vector && !((Vector<?>) obj).isEmpty() &&((Vector<?>) obj).get(0) instanceof DateTime){
			Vector<Date> res = new Vector<>();
			for(DateTime d: (Vector<DateTime>) obj){
				res.add(d.toJavaDate());
			}
			return res;
		}
		return null;
	}
	
		
	public static String getProjectNumberFromString(String str, double minConfidence) {

		//V1234
		//VI-1234	
		
		final boolean doLog = false;
		
		final double CONF_YEAR_MATCH = -0.5;	//Abzug für mögliche Jahreszahl
		final double CONF_COUNT 	 = -0.1;	//Abzug pro weiterem Treffer
		final double CONF_START_POS  = -0.014;	//Bis zu 14 Stellen für erstes Match zulassen
		
		//Initial: 1.0 Confidence = Full
		double confidence = 1.0;
		
		String prjNr = null;
		
		//Pattern pattern = Pattern.compile("(\\b\\d{4}\\b)");
		//dko: better Match, taken from https://stackoverflow.com/questions/60835956/extract-a-specific-length-number-from-a-string-javascript 
		//Pattern pattern = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");	//dko: neg. Lookbehind geht wohl in Java nicht so gut
		//Pattern pattern = Pattern.compile("\\b(?:\\D*)(\\d{4})(?:\\D*)\\b");		//bge, 9.12.21
		
		Pattern pattern = Pattern.compile("(?:\\b|_)(?:\\D*)(\\d{4})(?:\\D*)(?:\\b|_)");		//dko, 14.12.21
		
		
		int i = 0;
		
		Matcher matcher = pattern.matcher(str);
		
		//SPÄTER: man könnte auch alle erstmal extrahieren und bzgl. potenzieller Jahreszahl bewerten, und bei mehreren Treffern dann den besten nach Nähe auswählen
		
		conInfo("Pattern: " + pattern.pattern(), doLog);
		
						
		while (matcher.find()) {

			i++;

			conInfo("Match " + i + ": " + matcher.group(1) + ", " + matcher.toString(), doLog);
			
			//1. Auftreten: hier bewerten wir die Nähe zum Startindex der zeichenkette
			if (i==1) {
				
				prjNr = matcher.group(1);
				
				int startIndex = Math.max(0, matcher.start(1));

				//System.out.println("Start-Index Gruppe: " + matcher.start(1));
				
				//Startindex * Faktor von Confidence abziehen
				confidence += (startIndex*CONF_START_POS);
				//System.out.println("confidence new: " + confidence);
				
			} else {

				//System.out.println("Match: " + i + ": " + matcher.group(1) + ", Start=" + matcher.start(1));
				
				//Mehrfach-Auftreten von Confidence abziehen
				confidence += CONF_COUNT;
				
				//System.out.println("confidence new: " + confidence);
			}
			
			if (i==10) break;
		}
		

		//2021-12-13, dko: Werte, die eine Jahreszahl sein könnten, reduzieren die Confidence
		//-> 6 Jahre zurück, 1 Jahr vor
		if (prjNr!=null) {
			int asInt = Integer.valueOf(prjNr);
			int year = Calendar.getInstance().get(Calendar.YEAR);
			if (asInt >= year-5 && asInt <= year + 1) {
				confidence += CONF_YEAR_MATCH;
				//System.out.println("year detected, confidence new: " + confidence);
			}
		}
		
		confidence= Math.max(0.0,  confidence);
		
		if (confidence >= minConfidence) {
			return prjNr;
		} else return null;
		
	}
	
	/**Try to get the ProjectNumber from a given String
	 * Default Confidence: 0.7 for matching a project number (=exclude potential year numbers, but allow some multiple matches)
	 * 
	 * @param str
	 * @return prjNr oder null
	 */
    public static String getProjectNumberFromString(String str) {
    	
    	return getProjectNumberFromString(str, 0.7);
    	
    	/*
    	String prjNr = null;
		
		
		//Pattern pattern = Pattern.compile("(\\b\\d{4}\\b)");
		
		//dko: better Match, taken from https://stackoverflow.com/questions/60835956/extract-a-specific-length-number-from-a-string-javascript 
		//Pattern pattern = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");	//dko: neg. Lookbehind geht wohl in Java nicht so gut
		Pattern pattern = Pattern.compile("\\b(?:\\D*)(\\d{4})(?:\\D*)\\b");		//bge

		
		//dko: nur die ersten 15 Zeichen
		if (str.length()>15) str = str.substring(0, 14);
		
		Matcher matcher = pattern.matcher(str);
		if (matcher.find()) {
			prjNr = matcher.group(1);
		}
		
		return prjNr;
    	*/
	}

    /**
     * https://newbedev.com/java-equivalent-to-javascript-s-encodeuricomponent-that-produces-identical-output
     * 
     * Encodes the passed String as UTF-8 using an algorithm that's compatible
     * with JavaScript's <code>encodeURIComponent</code> function. Returns
     * <code>null</code> if the String is <code>null</code>.
     * 
     * @param s The String to be encoded
     * @return the encoded String
     */
    public static String encodeURIComponent(String s)
    {
      String result = null;

      try
      {
        result = URLEncoder.encode(s, "UTF-8")
                           .replaceAll("\\+", "%20")
                           .replaceAll("\\%21", "!")
                           .replaceAll("\\%27", "'")
                           .replaceAll("\\%28", "(")
                           .replaceAll("\\%29", ")")
                           .replaceAll("\\%7E", "~");
      } catch (UnsupportedEncodingException e) {
    	  //This exception should never occur.
    	  result = s;
      }

      return result;
    }
    
    /**
     * 
     * @param date requires a Java Date 
     * @return returns a lotus.domino DateTime to be used with a document field of the type Date/Time... The returned DateTime needs to be recycled after use!!!
     * @throws NotesException
     */
    public static DateTime dateToDateTime(Date date) throws NotesException{
		return NotesContext.getCurrent().getCurrentSession().createDateTime(date);
	}

    /**
     * 
     * @param original JsonJavaObject to be deep Copied / Cloned
     * @return returns an new Object with no side effects to the input Object
     */
    public static JsonJavaObject deepCopyJsonObject(JsonJavaObject original) {
	       JsonJavaObject copy = null;
	       try {
	           copy = (JsonJavaObject) JsonParser.fromJson(JsonJavaFactory.instanceEx, original.toString());
	       } catch (JsonException e) {
	           TNotesUtil.stdErrorHandler(e, "original: " + original);
	       }
	       return copy;
	}

}
