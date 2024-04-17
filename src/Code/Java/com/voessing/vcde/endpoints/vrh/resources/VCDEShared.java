package com.voessing.vcde.endpoints.vrh.resources;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Vector;

import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.helpers.VrhDominoHelper;
import com.voessing.xapps.utils.vrh.helpers.VrhHelper;

import lotus.domino.Database;
import lotus.domino.DbDirectory;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class VCDEShared {

	public final static HashSet<String> allowedOriginsForAccessControl = new HashSet<>(Arrays.asList(
		// allowed origins:
		"https://related-cobra-allowing.ngrok-free.app", // dev environment mpe: ngrok url
		"https://5b29t4qt-5173.euw.devtunnels.ms" // dev environment mpe: VS Code port forwarding
	));

	// public final static String PMTOOLS_DATABASENAME = "VCDE-Config_MP.nsf"; // "VCDE-Config.nsf";
	// public final static String PMTOOLS_SERVERNAME = "ibvdno04"; // "ibvdno03";

	public final static String PMTOOLS_DATABASENAME = "VCDE-Config.nsf";
	public final static String PMTOOLS_SERVERNAME = "ibvdno03";

	/*-
	// Method to perform case-insensitive search
	@Deprecated
	private static boolean containsStringCaseInsensitive(Vector<?> vector, String searchString) {
		for (Object element : vector) {
			if (((String) element).trim().equalsIgnoreCase(searchString.trim())) {
				return true;
			}
		}
		return false;
	}
	*/

	/*-
	// Method to check for duplicates
	private static boolean hasDuplicates(Vector<String> vector) {
		for (int i = 0; i < vector.size(); i++) {
			if (containsStringCaseInsensitive(vector, vector.get(i))) {
				return true; // Found a duplicate
			}
		}
	
		return false; // No duplicates found
	}
	*/

	public static void enrichDocumentWithProjectDetails(Document targetDocument, Session session, String projectRid, String projectUid) throws VrhException, NotesException {
		if (projectRid.isEmpty()) {
			throw new VrhException("error when getting project details: empty ProjectRID");
		}

		if (projectUid.isEmpty()) {
			throw new VrhException("error when getting project details: empty ProjectUNID");
		}

		// find project document and copy project details
		DbDirectory dir = session.getDbDirectory(null);
		try {
			Database sourceDb = dir.openDatabaseByReplicaID(projectRid); // projects db
			try {
				Document sourceDocument = sourceDb.getDocumentByUNID(projectUid); // project document
				try {
					targetDocument.replaceItemValue("ProjectCID", sourceDocument.getItemValueString("Firma"));
					targetDocument.replaceItemValue("ProjectKST", sourceDocument.getItemValueString("Kostenstelle"));
					targetDocument.replaceItemValue("ProjectPNr", sourceDocument.getItemValueString("PNRSort"));
					targetDocument.replaceItemValue("ProjectTitle", sourceDocument.getItemValueString("ProjektName"));
				} finally {
					VrhDominoHelper.recycle(sourceDocument);
				}
			} finally {
				VrhDominoHelper.recycle(sourceDb);
			}
		} finally {
			VrhDominoHelper.recycle(dir);
		}
	}

	public static void enrichDocumentWithToolDetails(Document targetDocument, String toolUNID) throws VrhException, NotesException {
		if (toolUNID.isEmpty()) {
			throw new VrhException("error when getting project details: empty ToolUNID");
		}

		Database sourceDb = targetDocument.getParentDatabase();
		try {
			Document sourceDocument = sourceDb.getDocumentByUNID(toolUNID); // tool document
			try {
				targetDocument.replaceItemValue("ToolName", sourceDocument.getItemValueString("name"));
				targetDocument.replaceItemValue("ToolPictureUrl", sourceDocument.getItemValueString("pictureUrl"));
				targetDocument.replaceItemValue("ToolTitle", sourceDocument.getItemValueString("title"));
				targetDocument.replaceItemValue("ToolType", sourceDocument.getItemValueString("type"));
			} finally {
				VrhDominoHelper.recycle(sourceDocument);
			}
		} finally {
			VrhDominoHelper.recycle(sourceDb);
		}
	}

	public static void ppCheckUniqueEmailAddresses(Document ppDoc) throws NotesException, VrhException {
		// ### 1. check dups within emailAddressOther of current pp document
		@SuppressWarnings("unchecked")
		Vector<String> emailAddressOther = ppDoc.getItemValue("emailAddressOther");
		if (emailAddressOther != null && emailAddressOther.size() > 0) {
			for (int i = 0; i < emailAddressOther.size(); i++) {
				String s1 = (emailAddressOther.get(i)).trim();
				for (int j = i + 1; j < emailAddressOther.size(); j++) {
					String s2 = (emailAddressOther.get(j)).trim();
					if (s1.equalsIgnoreCase(s2)) {
						// found a duplicate
						throw new VrhException(VrhException.SC_UNPROCESSABLE_ENTITY, "Die E-Mail-Adresse \"" + s1 + "\" wird mehrtfach in den weiteren E-Mail-Adressen dieser projektbeteiligten Person verwendet!");
					}
				}
			}
		}

		// ### 2. check dups of emailAddress in emailAddressOther of current pp document
		String emailAddress = ppDoc.getItemValueString("emailAddress");
		if (emailAddress != null && !emailAddress.isEmpty() && emailAddressOther != null && emailAddressOther.size() > 0) {
			emailAddress = emailAddress.trim();
			if (VrhHelper.vectorContainsCaseInsensitive(emailAddressOther, emailAddress)) {
				// found a duplicate
				throw new VrhException(VrhException.SC_UNPROCESSABLE_ENTITY, "Die E-Mail-Adresse \"" + emailAddress + "\" wird bereits in den weiteren E-Mail-Adressen dieser projektbeteiligten Person verwendet!");
			}
		}

		// ### 3. check all email addresses of current pp document with all all email addresses of other pp documents in same project
		String projectRID = ppDoc.getItemValueString("ProjectRID");
		String projectUID = ppDoc.getItemValueString("ProjectUNID");

		// get all email addresses
		Vector<String> emailAddressAll = new Vector<String>(emailAddressOther);
		emailAddressAll.add(emailAddress);

		View view = VrhHelper.getViewByDatabase("(vrh)\\constraints\\ppUniqueEmailAddress", ppDoc.getParentDatabase());
		try {
			// iterate over all email addresses of document
			for (String e : emailAddressAll) {
				if (e != null) {
					e = e.trim();
					DocumentCollection documents = view.getAllDocumentsByKey(projectRID + "-" + projectUID + "-" + e, true);
					try {
						// iterate over view
						Document d = documents.getFirstDocument();
						try {
							while (d != null) {
								if (!d.getUniversalID().equals(ppDoc.getUniversalID())) {
									String formattedName = formatName(d.getItemValueString("firstName"), d.getItemValueString("lastName"), d.getItemValueString("emailAddress"), false);
									throw new VrhException(VrhException.SC_UNPROCESSABLE_ENTITY, "Die E-Mail-Adresse \"" + e + "\" wird bereits von der projektbeteiligten Person  \"" + formattedName + "\" verwendet!");
								}

								// get next document (the domino way...)
								Document nextDocument = documents.getNextDocument();
								VrhDominoHelper.recycle(d, ppDoc); // as ppDoc could be part of the view we skip recycling it
								d = nextDocument;
							}
						} finally {
							VrhDominoHelper.recycle(d, ppDoc); // as ppDoc could be part of the view we skip recycling it
						}
					} finally {
						VrhDominoHelper.recycle(documents);
					}
				}
			}
		} finally {
			VrhDominoHelper.recycle(view);
		}
	}

	private static String formatName(String firstName, String lastName, String emailAddress, Boolean naturalOrder) {
		firstName = firstName.trim();
		lastName = lastName.trim();
		emailAddress = emailAddress.trim();

		String result = naturalOrder
			? firstName + (firstName.isEmpty() || lastName.isEmpty()
				? ""
				: " ") + lastName
			: lastName + (firstName.isEmpty() || lastName.isEmpty()
				? ""
				: ", ") + firstName;

		result = emailAddress.isEmpty()
			? result
			: result.isEmpty()
				? emailAddress
				: result + " (" + emailAddress + ")";

		return result;
	}

}
