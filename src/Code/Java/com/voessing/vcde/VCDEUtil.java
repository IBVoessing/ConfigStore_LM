package com.voessing.vcde;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.voessing.common.TNotesUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.MIMEEntity;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;
import lotus.domino.Stream;


public class VCDEUtil {

	public static void sendMail(List<String> recipients, String subject, String html) throws Exception {
		Session session = NotesContext.getCurrent().getCurrentSession();
		session.setConvertMIME(false); // Do not convert MIME to RT

		Database db = session.getCurrentDatabase();
		Document mail = db.createDocument();

		mail.replaceItemValue("Form", "Memo");
		mail.replaceItemValue("SendTo", new Vector<String>(recipients));
		mail.replaceItemValue("Subject", subject);

		Stream stream = session.createStream();
		stream.writeText(html);

		MIMEEntity bodyHTML = mail.createMIMEEntity();
		bodyHTML.setContentFromText(stream, "text/html;charset=UTF-8", MIMEEntity.ENC_NONE);

		stream.close();
		mail.closeMIMEEntities(true);

		mail.send();
		mail.recycle();

		session.setConvertMIME(true); // Restore conversion
	}

	public static void sendMailToCurrUser(String subject, String html) throws Exception{
		String userName = NotesContext.getCurrent().getCurrentSession().getEffectiveUserName();
		sendMail(Arrays.asList(userName), subject, html);
	}

	public static String getGraphEMLId(String name) {
		// Input: "someText_id.eml" or "id.eml" -> id
		// we want to extract the id from the name
		String result = name.substring(0, name.lastIndexOf("."));

		if (name.contains("_")) {
			result = result.substring(result.lastIndexOf("_") + 1, result.length());
		}

		return result;
	}

	public static byte[] inputStreamToByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];
		
		while ((nRead = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}

		buffer.flush();
		return buffer.toByteArray();
	}

	/**
	 * Parses a timestamp string in the format "yyyy-MM-dd'T'HH:mm:ss'Z'" and returns a Date object.
	 *
	 * @param timestamp the timestamp string to parse
	 * @return the parsed Date object, or null if the parsing fails
	 */
	public static Date parseTeamsTimestamp(String timestamp) {
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			return formatter.parse(timestamp);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Retrieves a document from the database based on its unique identifier (UNID).
	 *
	 * @param db The database from which to retrieve the document.
	 * @param unid The unique identifier (UNID) of the document.
	 * @return The document with the specified UNID, or null if it is not found.
	 * @throws NotesException If an error occurs while retrieving the document.
	 */
	public static Document getDocumentByUNID(Database db, String unid) {
		try {
			return db.getDocumentByUNID(unid);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Generates detailed information about a user and their role inheritence.
	 * 
	 * @param user The user object containing user information.
	 * @param translation The translation object containing role translations.
	 * @return A string containing the generated detailed information.
	 * @throws NotesException
	 */
	@SuppressWarnings("unchecked")
	public static String generateTimDetailInfo(JsonJavaObject user, JsonJavaObject translation, Document ti) throws NotesException {

		String toolInstanceName = ti.getItemValueString("name");

		StringBuilder text = new StringBuilder();
		List<String> roles = (List<String>) user.get("roles");

		text.append("Name: ").append(user.getString("firstName")).append(" ").append(user.getString("lastName")).append("\n");
		roles = translateRoles(roles, translation);

		// make sure that the roles are unique
		roles = new ArrayList<>(new HashSet<>(roles));

		text.append("Rollen: ").append(roles).append("\n\n");
		text.append("Rollenherleitung: \n");

		List<Object> allResolves = (List<Object>) user.getAsList("allResolves");

		for (int i = 0; i < allResolves.size(); i++) {
			JsonJavaObject resolve = (JsonJavaObject) allResolves.get(i);

			boolean isNative = isNative(resolve);

			List<String> topLevelRoles;

			if (isNative) {
				topLevelRoles = roles;
			} else {
				topLevelRoles = (List<String>) resolve.get("topLevelRoles");
				topLevelRoles = translateRoles(topLevelRoles, translation);
			}

			text.append("(").append(i).append(")").append("\n");

			if (resolve.containsKey("resolvePath") && resolve.getAsList("resolvePath").size() > 0) {
				text.append("Der Nutzer wurde in ").append(toolInstanceName).append(" über die Gruppe(n): ").append(String.join(" -> ", (List<String>) resolve.get("resolvePath")))
						.append(" berechtigt.\n");
			} else {
				text.append("Der Nutzer wurde in ").append(toolInstanceName).append(" persönlich berechtigt.\n");
			}

			text.append("Resultierende Rolle(n): ").append(String.join(", ", topLevelRoles)).append("\n");

			text.append("\n");
		}

		// remove all \n at the end of the string
		text = new StringBuilder(text.toString().replaceAll("\\n+$", ""));

		return text.toString();
	}

	private static boolean isNative(JsonJavaObject user) {
		if (user.containsKey("resolvePath")) {
			return false;
		}
		return true;
	}

	private static List<String> translateRoles(List<String> roles, JsonJavaObject translation) {
		List<String> translatedRoles = new ArrayList<>();
		for (String role : roles) {
			if (translation != null && translation.containsKey(role.toLowerCase())) {
				translatedRoles.add(translation.getString(role.toLowerCase()));
			} else {
				translatedRoles.add(role);
			}
		}
		return translatedRoles;
	}

	/**
	 * Updates the TIM roles in the specified document with the given values.
	 * 
	 * @param doc the document to update
	 * @param itemName the name of the item to update
	 * @param value the new values for the TIM roles
	 * @param translation the translation document for role translation (optional)
	 * @return true if the TIM roles were successfully updated, false otherwise
	 * @throws NotesException if an error occurs during the update process
	 */
	public static boolean updateTIMRoles(Document doc, String itemName, Vector<String> value, JsonJavaObject translation) throws NotesException {
		// if the value is empty or null we dont need to update
		if (value == null || value.isEmpty()) {
			return false;
		}
		// if the translation document is available we try to translate the roles
		if (translation != null) {
			Vector<String> translatedRoles = new Vector<>();
			for (String role : value) {
				// try to translate the role if the role is not in the translation document we
				// use the original role
				translatedRoles.add((String) translation.getOrDefault(role.toLowerCase(), role));
			}
			value = translatedRoles;
		}

		// ensure that the roles are unique
		value = new Vector<>(new HashSet<>(value));

		return replaceUnequalItem(doc, itemName, value);
	}

	/**
	 * Adds a value to a multivalue field in a Lotus Notes document.
	 * 
	 * @param doc The Lotus Notes document to modify.
	 * @param itemName The name of the multivalue field.
	 * @param value The value to add to the multivalue field.
	 * @return true if the value was added successfully, false otherwise.
	 * @throws NotesException if there is an error accessing or modifying the document.
	 */
	@SuppressWarnings("unchecked")
	public static boolean addToDocumentMultivalue(Document doc, String itemName, Object value) throws NotesException {
		if (value == null || (value instanceof String && ((String) value).isEmpty())) {
			return false;
		}

		Vector<Object> values = doc.getItemValue(itemName);
		if (!values.contains(value)) {
			values.addElement(value);
			doc.replaceItemValue(itemName, values).recycle();
			return true;
		}
		return false;
	}

	/**
	 * Adds multiple values to a document's multivalue field.
	 * 
	 * @param doc The document to add values to.
	 * @param itemName The name of the multivalue field.
	 * @param value The values to be added.
	 * @return True if values were added and the document needs to be saved, false otherwise.
	 * @throws NotesException if an error occurs while accessing the document.
	 */
	@SuppressWarnings("unchecked")
	public static boolean addToDocumentMultivalue(Document doc, String itemName, Vector<Object> value) throws NotesException {
		if (value == null || value.isEmpty()) {
			return false;
		}

		Vector<Object> values = doc.getItemValue(itemName);
		boolean needSave = false;
		for (Object v : value) {
			if (!values.contains(v) && !(v instanceof String && ((String) v).isEmpty())) {
				values.addElement(v);
				needSave = true;
			}
		}
		if (needSave) {
			doc.replaceItemValue(itemName, values).recycle();
		}
		return needSave;
	}

	/**
	 * Adds an item to the list of fields that are forbidden for user edit in a document.
	 * 
	 * @param doc The document to modify.
	 * @param itemName The name of the field to add to the forbidden list.
	 * @throws NotesException if there is an error accessing or modifying the document.
	 */
	@SuppressWarnings("unchecked")
	public static void addToForbidUserEditFor(Document doc, String itemName) throws NotesException {
		Vector<String> protectedFields = doc.getItemValue("$forbidUserEditFor");
		protectedFields.replaceAll(String::toLowerCase);
		String safeItemName = itemName.toLowerCase();

		if (!protectedFields.contains(safeItemName)) {
			protectedFields.addElement(safeItemName);
			doc.replaceItemValue("$forbidUserEditFor", protectedFields).recycle();
			doc.save();
		}
	}

	/**
	 * Updates the list of protected fields for which user edit is forbidden in the given document.
	 * 
	 * @param doc The document to update.
	 * @param newProtectedFields The new list of protected fields that will be set (old settings will be gone).
	 * @return {@code true} if the list of protected fields was updated, {@code false} otherwise.
	 * @throws NotesException If an error occurs while updating the document.
	 */
	@SuppressWarnings("unchecked")
	public static boolean updateForbidUserEditFor(Document doc, Vector<String> newProtectedFields) throws NotesException {
		Vector<String> protectedFields = doc.getItemValue("$forbidUserEditFor");

		// convert all to lower case
		protectedFields.replaceAll(String::toLowerCase);
		newProtectedFields.replaceAll(String::toLowerCase);

		if (!protectedFields.equals(newProtectedFields)) {
			doc.replaceItemValue("$forbidUserEditFor", newProtectedFields).recycle();
			return true;
		}
		return false;
	}

	/**
	 * Used in graph groups related usersyncs
	 * 
	 * Builds an address string based on the provided user information.
	 * 
	 * @param doc The document object.
	 * @param user The user object containing the address information.
	 * @return The formatted address string.
	 * @throws Exception If an error occurs during the address building process.
	 */
	public static String buildTIMAddressGraph(Document doc, JsonJavaObject user) throws Exception {
		StringBuilder addressBuilder = new StringBuilder();

		String streetAddress = user.getAsString("streetAddress");
		String city = user.getAsString("city");
		String postalCode = user.getAsString("postalCode");
		String country = user.getAsString("country");

		if (streetAddress != null && !streetAddress.isEmpty()) {
			addressBuilder.append(streetAddress).append(", ");
		}
		if (postalCode != null && !postalCode.isEmpty()) {
			addressBuilder.append(postalCode).append(" ");
		}
		if (city != null && !city.isEmpty()) {
			addressBuilder.append(city).append(", ");
		}
		if (country != null && !country.isEmpty()) {
			addressBuilder.append(country);
		}

		// check if the last char is "," and remove it
		if (addressBuilder.length() > 0 && addressBuilder.charAt(addressBuilder.length() - 1) == ',') {
			addressBuilder.deleteCharAt(addressBuilder.length() - 1);
		}

		String address = addressBuilder.toString().trim();
		return address;
	}

	/**
	 * Used in graph groups related usersyncs
	 * 
	 * Updates the phone numbers in the given document with the phone numbers obtained from the API. The first phone number in the API response
	 * is considered the main phone number, while the rest are treated as other phone numbers. If any of the phone numbers are different from
	 * the existing values in the document, the corresponding fields are updated.
	 * 
	 * @param doc The document to update.
	 * @param user The JSON object containing the phone numbers obtained from the API.
	 * @return true if any phone number was updated and the document needs to be saved, false otherwise.
	 * @throws Exception if an error occurs during the update process.
	 */
	@SuppressWarnings("unchecked")
	public static boolean updateTIMPhoneNumbers(Document doc, JsonJavaObject user) throws Exception {
		boolean needSave = false;

		// get the phone numbers from the api
		List<String> apiPhoneNumbers = (List<String>) (Object) user.getAsList("businessPhones");

		if (apiPhoneNumbers.size() > 0) {
			// the first phonenumber is the main one
			needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "phone", apiPhoneNumbers.get(0)) || needSave;
			apiPhoneNumbers.remove(0);
			// the rest are other phone numbers
			Vector<String> phoneOther = new Vector<>(apiPhoneNumbers);
			needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "phoneOther", phoneOther) || needSave;
		}
		return needSave;
	}

	/**
	 * Parses a full name into first name and last name parts. firstName contains all parts except the last one, lastName contains the last
	 * part. If the fullname has no spaces, the whole name is put into lastName.
	 *
	 * @param fullName the full name to be parsed
	 * @return a map containing the first name and last name parts
	 */
	public static Map<String, String> parseFullName(String fullName) {
		Set<String> compoundLastNames = new HashSet<>(Arrays.asList("Kleine Kuhlmann"));
		Map<String, String> nameParts = new HashMap<>();
		String[] splitName = fullName.trim().split("\\s+");

		if (fullName.contains(",")) {
			// lastName, firstName format
			String[] namePartsArray = fullName.split(",");
			nameParts.put("firstName", namePartsArray[1].trim());
			nameParts.put("lastName", namePartsArray[0].trim());
		} else if (fullName.contains(".")) {
			// lastName.firstName format
			String[] namePartsArray = fullName.split("\\.");
			nameParts.put("firstName", namePartsArray[0].trim());
			nameParts.put("lastName", namePartsArray[1].trim());
		} else if (splitName.length == 2 && splitName[0].contains("-")) {
			// special case for names like Neubauer-Liszt Michaela where the last name is
			// Neuabuer-Liszt
			nameParts.put("firstName", splitName[0]);
			nameParts.put("lastName", splitName[1]);
		} else if (splitName.length >= 2) {
			String lastName = splitName[splitName.length - 1];
			String firstName = String.join(" ", Arrays.copyOfRange(splitName, 0, splitName.length - 1));

			// Check for compound last names
			for (String compoundLastName : compoundLastNames) {
				if (fullName.contains(compoundLastName)) {
					lastName = compoundLastName;
					firstName = fullName.replace(compoundLastName, "").trim();
					break;
				}
			}

			nameParts.put("firstName", firstName);
			nameParts.put("lastName", lastName);
		} else {
			nameParts.put("firstName", "");
			nameParts.put("lastName", fullName);
		}

		// make sure that the first and last name if they are not empty are capitalized
		// firstName can be a compound name so we need to capitalize each part
		if (!nameParts.get("lastName").isEmpty()) {
			nameParts.put("lastName", capitalizeFirstLetter(nameParts.get("lastName")));
		}
		if (!nameParts.get("firstName").isEmpty()) {
			String[] lastNameParts = nameParts.get("firstName").split("\\s+");
			StringBuilder capitalizedLastName = new StringBuilder();
			for (String part : lastNameParts) {
				capitalizedLastName.append(capitalizeFirstLetter(part)).append(" ");
			}
			nameParts.put("firstName", capitalizedLastName.toString().trim());
		}

		return nameParts;
	}

	public static String capitalizeFirstLetter(String input) {
		if (input == null || input.isEmpty() || input.equals("von")) {
			return input;
		}
		return input.substring(0, 1).toUpperCase() + input.substring(1);
	}

	/**
	 * Returns the full Voessing company name based on the given company acronym.
	 *
	 * @param companyAcronym the acronym of the company
	 * @return the full company name corresponding to the given acronym
	 */
	public static String getFullVoessingCompanyName(String companyAcronym) {
		companyAcronym = companyAcronym.toLowerCase().trim();
		String companyName = "";
		switch (companyAcronym) {
			case "egv":
				companyName = "Entwicklungsgesellschaft Vössing mbH";
				break;
			case "vb":
				companyName = "J.W. Vössing Bau GmbH";
				break;
			case "vc":
				companyName = "Ingenieurbüro Dipl.-Ing. H. Vössing (Beijing) Ltd.";
				break;
			case "vi":
				companyName = "Vössing Ingenieurgesellschaft mbH";
				break;
			case "vi-argen":
				companyName = "VI-Argen";
				break;
			case "vp":
				companyName = "Voessing Polska Sp. z o.o.";
				break;
			case "vq":
				companyName = "Voessing Qatar WLL";
				break;
			case "vv":
				companyName = "Vössing Vepro Ingenieurgesellschaft mbH";
				break;
		}
		return companyName;
	}

	/**
	 * Updates the tool-related fields of a document with the values from another document.
	 * 
	 * @param doc the document to be updated
	 * @param docWTI the document with the tool information
	 * @return true if any field was updated, false otherwise
	 * @throws NotesException if an error occurs while accessing the documents
	 */
	public static boolean addToolRelation(Document doc, Document docWTI) throws NotesException {
		boolean needSave = false;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "toolName", docWTI.getItemValueString("toolName")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "toolUnid", docWTI.getItemValueString("toolUnid")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "toolTitle", docWTI.getItemValueString("toolTitle")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "toolType", docWTI.getItemValueString("toolType")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "toolPictureUrl", docWTI.getItemValueString("toolPictureUrl")) || needSave;
		return needSave;
	}

	/**
	 * Updates the project-related fields of a document with the values from another document.
	 * 
	 * @param doc the document to be updated
	 * @param docWPI the document with the project information
	 * @return true if any field was updated, false otherwise
	 * @throws NotesException if an error occurs while accessing the documents
	 */
	public static boolean addProjectRealtion(Document doc, Document docWPI) throws NotesException {
		boolean needSave = false;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectID", docWPI.getItemValueString("ProjectID")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectPNr", docWPI.getItemValueString("ProjectPNr")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectTitle", docWPI.getItemValueString("ProjectTitle")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectRID", docWPI.getItemValueString("ProjectRID")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectUNID", docWPI.getItemValueString("ProjectUNID")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectCID", docWPI.getItemValueString("ProjectCID")) || needSave;
		needSave = VCDEUtil.updateIfDifferentAndAllowed(doc, "ProjectKST", docWPI.getItemValueString("ProjectKST")) || needSave;
		return needSave;
	}

	/**
	 * Converts a vector of items to a list of strings.
	 * 
	 * @param items a vector of items to be converted
	 * @return a list of strings representing the items
	 */
	public static List<String> itemsToList(Vector<Item> items) {
		List<String> res = new ArrayList<>();
		items.forEach(item -> res.add(item.toString()));
		TNotesUtil.recycleNotesObject(items);
		return res;
	}

	/**
	 * This method checks if all the given RichTextItems have the same unformatted text.
	 * 
	 * @param items an array of RichTextItems to compare
	 * @return true if all the items have the same text, false otherwise (also false if one of the items is null)
	 */
	public static boolean equalsRichTextItem(RichTextItem... items) throws NotesException {
		// Check if the items array is null or empty
		if (items == null || items.length == 0) {
			return false;
		}
		// Get the first Item
		RichTextItem firstItem = items[0];
		if (firstItem == null)
			return false;
		// Get the unformatted text of the first item
		String firstText = firstItem.getUnformattedText();
		// Loop through the rest of the items
		for (int i = 1; i < items.length; i++) {
			if (items[i] == null) {
				return false;
			}
			// Get the unformatted text of the current item
			String currentText = items[i].getUnformattedText();
			// Compare the text with the first one
			if (!firstText.equals(currentText)) {
				// If they are not equal, return false
				return false;
			}
		}
		// If all items have the same text, return true
		return true;
	}

	/**
	 * Recycels given Objects. Documents of a DocumentCollection will also be recylced in this processed aswell aas the document collection
	 * itself Supports all types of Domino Objects, Maps, Collections and Arrays containing all types of Domino Objects
	 * 
	 * @param args
	 */
	@SuppressWarnings({"unchecked"})
	public static void recycleNotesObject(final Object... args) {

		for (Object o : args) {
			if (o != null) {
				if (o instanceof lotus.domino.DocumentCollection) {
					recycleDocumentCollectionEntrys((DocumentCollection) o);
					try {
						((DocumentCollection) o).recycle();
					} catch (Throwable t) {
						// who cares?
					}
				} else if (o instanceof lotus.domino.Base) {
					try {
						((Base) o).recycle();
					} catch (Throwable t) {
						// who cares? v2
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

	/**
	 * Recycels all Documents inside of an DocumentCollection
	 * 
	 * @param dc
	 */
	public static void recycleDocumentCollectionEntrys(DocumentCollection dc) {
		Document d = null;
		Document tmp = null;

		try {
			while (d != null) {
				tmp = d;
				d = dc.getNextDocument();
				tmp.recycle();
			}
		} catch (Exception e) {
			// das ja doof :((
		} finally {
			VCDEUtil.recycleNotesObject(tmp, d);
		}
	}

	/**
	 * Puts all inputs provided into an Vector<String>
	 * 
	 * @param args
	 * @return
	 */
	@SafeVarargs
	public static <T> Vector<T> createVectorWith(T... args) {
		return new Vector<>(Arrays.asList(args));
	}

	/**
	 * Updates a field in a document if it is empty and allowed to be updated.
	 * 
	 * @param doc The document to update.
	 * @param itemName The name of the field to update.
	 * @param value The new value for the field.
	 * @return true if the field was updated, false otherwise.
	 * @throws NotesException if there is an error accessing the document.
	 */
	@SuppressWarnings("unchecked")
	public static boolean updateIfEmptyAndAllowed(Document doc, String itemName, Object value) throws NotesException {

		// check if the value is empty or null then we dont need to update
		if ((value instanceof Collection && ((Collection<?>) value).isEmpty()) || (value instanceof String && ((String) value).isEmpty()) || value == null) {
			return false;
		}

		// check if the field is allowed to be updated
		Vector<String> protectedFields = doc.getItemValue("$protectUserEditFor");
		protectedFields.replaceAll(String::toLowerCase);
		String safeItemName = itemName.toLowerCase();
		if (protectedFields.contains(safeItemName)) {
			return false;
		}
		if (doc.getItemValue(itemName).isEmpty()) {
			doc.replaceItemValue(itemName, value).recycle();
			return true;
		}
		return false;
	}

	/**
	 * Updates an item of the given document only if its different and not blacklisted by $protectUserEditFor (doc)
	 * 
	 * @param doc document to be edited
	 * @param itemName name of the item to be potentially replaced
	 * @param singleValue value to replace the existing value
	 * @return
	 * @throws NotesException
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	public static boolean updateStringIfDifferentAndAllowed(Document doc, String itemName, String singleValue) throws NotesException {
		// check if the field is allowed to be updated
		Vector<String> protectedFields = doc.getItemValue("$protectUserEditFor");
		protectedFields.replaceAll(String::toLowerCase);
		String safeItemName = itemName.toLowerCase();
		if (protectedFields.contains(safeItemName)) {
			return false;
		}
		return replaceUnequalItemSingleString(doc, itemName, singleValue);
	}

	/**
	 * Updates an item of the given document only if its different and not blacklisted by $protectUserEditFor (doc)
	 * 
	 * @param doc document to be edited
	 * @param itemName name of the item to be potentially replaced
	 * @param value to replace the existing value
	 * @return
	 * @throws NotesException
	 */
	public static <T> boolean updateIfDifferentAndAllowed(Document doc, String itemName, T value) throws NotesException {
		if (doc.getItemValue("$protectUserEditFor").contains(itemName)) {
			return false;
		}
		return replaceUnequalItem(doc, itemName, value);
	}

	/**
	 * Replaces the rich text item with the given name in the document with the given value, if they are not equal. If the current item is null,
	 * a new one is created. The method recycles the current and value items after the replacement.
	 * 
	 * @param doc the document to modify
	 * @param itemName the name of the rich text item to replace
	 * @param value the new value of the rich text item
	 * @return true if the replacement was done, false if the items were equal
	 * @throws NotesException if an error occurs while accessing or modifying the document
	 */
	public static boolean replaceUnequalRichTextItem(Document doc, String itemName, RichTextItem value) throws NotesException {
		RichTextItem current = (RichTextItem) doc.getFirstItem(itemName);
		if (!equalsRichTextItem(current, value)) {
			if (current != null) {
				current.remove();
			}
			current = doc.createRichTextItem(itemName);
			current.appendRTItem(value);
			VCDEUtil.recycleNotesObject(current, value);
			return true;
		}
		return false;
	}

	/**
	 * Updates an item of the given document only if its different from the current item value
	 * 
	 * @param doc
	 * @param itemName is the name of the item to be replaced in the doc
	 * @param singleValue to replace the existing value
	 * @return
	 * @throws NotesException
	 */
	public static boolean replaceUnequalItemSingleString(Document doc, String itemName, String singleValue) throws NotesException {
		// if we modify a ToolInstance we need to add the field to the
		// $protectUserEditFor
		if (doc.getItemValueString("Form").trim().equals("ToolInstance")) {
			addToForbidUserEditFor(doc, itemName);
		}

		if (singleValue == null) {
			// System.out.println("The provided value for " + itemName + " was null (" + doc.getUniversalID() + "");
			return false;
		}

		// In Notes.Items wird offenbar 0D0A gespeichert. Evtl. auch nur unter Windows?
		// Jedenfalls beim Inhaltsvergleich mit 0D0A beidseitig ersetzen durch 0A
		if (!doc.getItemValueString(itemName).replace("\r\n", "\n").equals(singleValue.replace("\r\n", "\n"))) {
			doc.replaceItemValue(itemName, singleValue).recycle();
			return true;
		} else
			return false;
	}

	/**
	 * 
	 * @param doc
	 * @param itemName is the name of the item to be replaced in the doc
	 * @param value can be from type Number(Integer, Double, ...), String, DateTime and Vectors filled with these types
	 * @return returns true if the document entry has been updated (item != value)
	 * @throws NotesException
	 */
	public static <T> boolean replaceUnequalItem(Document doc, String itemName, T value) throws NotesException {
		// if we modify a ToolInstance we need to add the field to the
		// $protectUserEditFor
		if (doc.getItemValueString("Form").trim().equals("ToolInstance")) {
			addToForbidUserEditFor(doc, itemName);
		}

		if (value == null) {
			// String logMsg = "The provided value for " + itemName + " was null in the document with the UNID: " + doc.getUniversalID();
			// TNotesUtil.logEvent(logMsg);
			// System.out.println(logMsg);
			return false;
		}

		if (!equalsItem(doc, itemName, value)) {
			doc.replaceItemValue(itemName, value).recycle();
			return true;
		} else
			return false;
	}

	/**
	 * 
	 * @param doc
	 * @param itemName is the name of the item searched in the provided doc
	 * @param value can be from type Number(Integer, Double, ...), String, DateTime, RichTextItems and Vectors filled with these types
	 * @return returns true if the value and the found item are equal
	 * @throws NotesException
	 */
	@SuppressWarnings("unchecked")
	public static <T> boolean equalsItem(Document doc, String itemName, T value) throws NotesException {

		if (value == null) {
			throw new NotesException(1337, "The provided value was null!");
		} else if (!isValidInput(value)) {
			throw new NotesException(1337, "Unsupported input type!");
		}
		// get the current value as Vector
		Vector<T> current = doc.getItemValue(itemName);
		// set the input (value) as vector if not already
		Vector<T> input = null;
		if (!(value instanceof Vector)) {
			input = new Vector<>();
			// special case for string as an vector with an empty string != empty vector
			if (!(value instanceof String && ((String) value).isEmpty())) {
				input.add(value);
			}
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
		return value instanceof Number || value instanceof String || value instanceof DateTime || value instanceof RichTextItem;
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
	public static Object toJavaDate(Object obj) throws NotesException {
		if (obj instanceof DateTime) {
			return ((DateTime) obj).toJavaDate();
		} else if (obj instanceof Vector && !((Vector<?>) obj).isEmpty() && ((Vector<?>) obj).get(0) instanceof DateTime) {
			Vector<Date> res = new Vector<>();
			for (DateTime d : (Vector<DateTime>) obj) {
				res.add(d.toJavaDate());
			}
			return res;
		}
		return null;
	}

}
