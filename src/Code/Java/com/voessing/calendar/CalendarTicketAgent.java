package com.voessing.calendar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesCalendar;
import lotus.domino.NotesCalendarEntry;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

import com.google.gson.JsonObject;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.voessing.common.TNotesUtil;

public class CalendarTicketAgent {

    private static final int MAX_RETRIES = 1; // maximum number of retries for a failed ticket
    private static final String SUCCESS_STATUS = "processed";
    private static final String ERROR_STATUS = "error";
    private static final String MAX_RETRY_STATUS = "max_retries";

    private Session serverAgentSession;
    private Database azeDb, namesDB;
    private View userView;
    
    // report variables
    private int processedTickets, failedTickets;
    private List<String> failedTicketsList = new ArrayList<>();


    private static final String logPrefix = "CalendarTicketAgent:";

    public static class MailDatabaseEntry {
        private String mailServer;
        private String mailFile;

        @Override
        public String toString() {
            return "MailEntry [mailServer=" + mailServer + ", mailFile=" + mailFile + "]";
        }

        public MailDatabaseEntry(String mailServer, String mailFile) {
            this.mailServer = mailServer;
            this.mailFile = mailFile;
        }

        public String getMailServer() {
            return mailServer;
        }

        public String getMailFile() {
            return mailFile;
        }
    }
    
    public CalendarTicketAgent() throws NotesException {
        serverAgentSession = NotesContext.getCurrent().getCurrentSession();

        //TEST:  hardcoded server name
        //azeDb = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "AZEApp.nsf");
        azeDb = serverAgentSession.getCurrentDatabase();        
    }
    
    /**
     * Generates a report of the processed and failed tickets.
     * 
     * <p>
     * This method creates a JSON object that contains the number of processed
     * tickets, the number of failed tickets,
     * and a list of failed tickets. The JSON object is then converted into a string
     * and returned.
     * 
     * @return a string representation of a JSON object containing the report
     */
    public String getReport() {
        JsonObject report = new JsonObject();

        report.addProperty("processedTickets", processedTickets);
        report.addProperty("failedTickets", failedTickets);
        report.addProperty("failedTicketsList", failedTicketsList.toString());

        return report.toString();
    }

    /**
     * This is the core method that initiates the entire process of processing
     * calendar tickets.
     * It first loads all open tickets and then processes each ticket individually.
     * 
     * <p>
     * The processing of each ticket is done by calling the
     * {@link #processTicket(CalendarTicket)} method.
     * 
     * <p>
     * This method should be called to start the ticket processing workflow.
     * @throws NotesException 
     */
    public void processTickets() throws NotesException {
        try{
            List<CalendarTicket> openTickets = loadOpenTickets();
            consoleLog(openTickets.size() + " tickets loaded");
    
            for (CalendarTicket ticket : openTickets) {
                processTicket(ticket);
            }
    
            consoleLog("Processing complete");
            consoleLog(getReport());
        }finally{
            TNotesUtil.recycleNotesObject(namesDB, userView);
        }
    }

    /**
     * Loads all open calendar tickets from the database.
     * 
     * <p>
     * This method uses a view that performs a search in the database for documents that have the
     * form 'CalendarTicket', have not been successfully processed. Each document is
     * then converted into a {@link CalendarTicket} object. 
     * 
     * @return a list of open {@link CalendarTicket} objects
     * @throws NotesException 
     */
    private List<CalendarTicket> loadOpenTickets() throws NotesException {
        View sortedTicketsAsc = null;
        ViewEntryCollection vec = null;
        ViewEntry entry = null;
        ViewEntry tmpEntry = null;

        List<CalendarTicket> result = new ArrayList<>();
        
        try {
            // load all tickets from the database that have not been successfully processed
            // we use a view to ensure to process the tickets in creation order (asc)
            sortedTicketsAsc = azeDb.getView("(LookupAZETicketsAsc)");
            vec = sortedTicketsAsc.getAllEntries();


            entry = vec.getFirstEntry();

            while (entry != null) {

                Document entryDoc = entry.getDocument();
                result.add(new CalendarTicket(entryDoc));
                entryDoc.recycle();

                tmpEntry = entry;
                entry = vec.getNextEntry();
                tmpEntry.recycle();
            }
        } finally {
            TNotesUtil.recycleNotesObject(sortedTicketsAsc, vec, entry, tmpEntry);
        }

        return result;
    }

    /**
     * Processes a single calendar ticket.
     * 
     * <p>
     * This method retrieves the mail database entry for the requester of the
     * ticket, manages the calendar entry associated with the ticket,
     * and updates the status of the ticket to indicate that it has been processed.
     * 
     * <p>
     * If an exception occurs during the processing of the ticket, the exception is
     * caught and handled by the
     * {@link #handleTicketError(Exception, CalendarTicket)} method.
     * 
     * @param ticket the calendar ticket to be processed
     * @throws NotesException 
     */
    private void processTicket(CalendarTicket ticket) throws NotesException {

        if (ticket.getRetries() > MAX_RETRIES) {
            logFailedTicket(ticket, "Max retries reached");
            updateTicketStatus(ticket, MAX_RETRY_STATUS, "Maximum number of retries reached");
            return;
        }

        try {
            MailDatabaseEntry mailEntry = getUserMailEntry(ticket.getRequester());
            manageCalendarEntry(mailEntry, ticket);

            updateTicketStatus(ticket, SUCCESS_STATUS, null);
            logProcessedTicket(ticket);
        } catch (Exception e) {
            handleTicketError(e, ticket);
        }
    }

    /**
     * Logs the successful processing of a calendar ticket.
     * 
     * <p>
     * This method increments the count of processed tickets, and logs a success
     * message to the console.
     * 
     * @param ticket the calendar ticket that was successfully processed
     */
    private void logProcessedTicket(CalendarTicket ticket) {
        processedTickets++;
        consoleLog("Ticket " + ticket.getTicketDocumentUnid() + " processed successfully");
    }

    /**
     * Logs the failure of processing a calendar ticket.
     * 
     * <p>
     * This method increments the count of failed tickets, logs an error message to
     * the console, and adds the error message to a list of failed tickets.
     * 
     * @param ticket   the calendar ticket that failed to be processed
     * @param errorMsg the error message associated with the failure
     */
    private void logFailedTicket(CalendarTicket ticket, String errorMsg) {
        failedTickets++;
        String msg = "Ticket " + ticket.getTicketDocumentUnid() + " failed: " + errorMsg;
        consoleLog(msg);
        failedTicketsList.add(msg);
    }

    /**
     * Updates the status of a calendar ticket in the database.
     * 
     * <p>
     * This method retrieves the document associated with the ticket, updates the
     * 'AgentStatus' field to 'processed' if the ticket was processed successfully
     * or 'error' otherwise,
     * and updates the 'AgentError' field with any error message. The document is
     * then saved to reflect these changes.
     * 
     * @param ticket   the calendar ticket whose status is to be updated
     * @param success  a boolean indicating whether the ticket was processed
     *                 successfully
     * @param errorMsg a string containing any error message associated with the
     *                 ticket processing
     * @throws NotesException 
     */
    private void updateTicketStatus(CalendarTicket ticket, String status, String errorMsg) throws NotesException {

        Document ticketDoc = null;

        try {
            ticketDoc = azeDb.getDocumentByUNID(ticket.getTicketDocumentUnid());
            ticketDoc.replaceItemValue("AgentStatus", status);
            ticketDoc.replaceItemValue("AgentError", errorMsg);

            if (status.equals(ERROR_STATUS)) {
                ticketDoc.replaceItemValue("AgentRetryCount", ticket.getRetries() + 1);
            }

            ticketDoc.save();
        } finally {
            TNotesUtil.recycleNotesObject(ticketDoc);
        }
    }

    /**
     * Manages a calendar entry based on the provided ticket.
     * 
     * <p>
     * This method retrieves the user's mail database and calendar, and the calendar
     * entry associated with the ticket.
     * If the ticket is marked to be deleted, the calendar entry is deleted.
     * Otherwise, the calendar entry is updated or created based on whether it
     * already exists.
     * 
     * @param mailEntry the mail database entry of the user
     * @param ticket    the calendar ticket that dictates how the calendar entry
     *                  should be managed
     * @throws NotesException
     */
    private void manageCalendarEntry(MailDatabaseEntry mailEntry, CalendarTicket ticket) throws NotesException {

        Database userMailDB = null;
        NotesCalendar userCalendar = null;
        NotesCalendarEntry entry = null;

        try {
            userMailDB = getUserMailDatabase(mailEntry);
            userCalendar = getUserCalendar(userMailDB);

            entry = getCalendarEntry(userCalendar, ticket);

            if (ticket.toBeDeleted()) {
                deleteCalendarEntry(entry);
            } else {
                updateOrCreateCalendarEntry(userCalendar, entry, ticket);
            }

        } finally {
            TNotesUtil.recycleNotesObject(userMailDB, userCalendar, entry);
        }
    }

    /**
     * Retrieves the user's mail database.
     * 
     * @param mailEntry the mail database entry of the user
     * @return the user's mail database
     * @throws NotesException 
     * @throws IllegalArgumentException if the database could not be found
     */
    private Database getUserMailDatabase(MailDatabaseEntry mailEntry) throws NotesException {
        Database userMailDB = serverAgentSession.getDatabase(mailEntry.getMailServer(), mailEntry.getMailFile());
        if (userMailDB == null) {
            throw new IllegalArgumentException("Database not found");
        }
        return userMailDB;
    }

    /**
     * Retrieves the user's calendar from their mail database.
     * 
     * @param userMailDB the user's mail database
     * @return the user's calendar
     * @throws NotesException 
     */
    private NotesCalendar getUserCalendar(Database userMailDB) throws NotesException {
        return serverAgentSession.getCalendar(userMailDB);
    }

    /**
     * Retrieves a calendar entry from the user's calendar using the ticket UID.
     * 
     * @param userCalendar the user's calendar
     * @param ticket       the calendar ticket
     * @return the calendar entry associated with the ticket
     * @throws NotesException 
     */
    private NotesCalendarEntry getCalendarEntry(NotesCalendar userCalendar, CalendarTicket ticket) throws NotesException {
        return userCalendar.getEntry(ticket.getTicketUid());
    }

    /**
     * Deletes a calendar entry.
     * 
     * @param entry the calendar entry to be deleted
     * @throws NotesException 
     * @throws IllegalArgumentException if the entry to be deleted could not be
     *                                  found
     */
    private void deleteCalendarEntry(NotesCalendarEntry entry) throws NotesException {
        // as can check if entry is valid just delete it
        // if entry is not valid, an exception is thrown and written back to the ticket doc
        entry.remove();
    }

    /**
     * Updates an existing calendar entry or creates a new one based on the provided
     * ticket.
     * 
     * @param userCalendar the user's calendar
     * @param entry        the calendar entry to be updated, or null if a new entry
     *                     should be created
     * @param ticket       the calendar ticket that dictates how the calendar entry
     *                     should be updated or created
     * @throws NotesException 
     */
    private void updateOrCreateCalendarEntry(NotesCalendar userCalendar, NotesCalendarEntry entry, CalendarTicket ticket) throws NotesException {
        // we can´t check if an entry is valid or not, so we just try to update it
        try {
            entry.update(generateICal(ticket), "Anfrag wurde aktualisiert",
                    NotesCalendar.CS_WRITE_DISABLE_IMPLICIT_SCHEDULING +
                            NotesCalendar.CS_WRITE_MODIFY_LITERAL);
        } catch (Exception e) {
            // if the entry does not exist, create a new one
            NotesCalendarEntry newEntry = userCalendar.createEntry(generateICal(ticket), NotesCalendar.CS_WRITE_DISABLE_IMPLICIT_SCHEDULING);
            newEntry.recycle();
        }

    }

    /**<p>
     * (CURRENTLY NOT WORKING! WHY MISTA VISTALLI????)
     * <p>
     * 
     * Checks if a calendar entry is valid.
     * 
     * <p>
     * This method attempts to read the provided calendar entry. If the read
     * operation throws an exception,
     * the method returns false, indicating that the entry is not valid. If the read
     * operation is successful,
     * the method returns true, indicating that the entry is valid.
     * <p>
     * 
     * <p>
     * (YES this is the way to check if an entry is valid or not! ASK HCL!!!)
     * <p>
     * Note: BubbleExceptions must be turned on! Otherwise, you only get a warning
     * in the console.
     * 
     * @param entry the calendar entry to be checked
     * @return true if the entry can be successfully read, false otherwise
     */
    @SuppressWarnings("unused")
	@Deprecated
    private boolean isEntryValid(NotesCalendarEntry entry) {
        try {
            // use the entry to determine if it is valid
            // BubbleExceptions must be turned on! otherwise you only get an warning in the
            // console
            entry.read();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Generates an iCalendar (iCal) representation of a calendar ticket.
     * 
     * @param ticket the calendar ticket to be converted into an iCal format
     * @return a string representing the iCal format of the ticket
     */
    private String generateICal(CalendarTicket ticket) {
        StringBuilder iCal = new StringBuilder();
        iCal.append("BEGIN:VCALENDAR\n");
        iCal.append("PRODID:AZE Import\n");
        iCal.append("BEGIN:VEVENT\n");

        if (ticket.isBlocking()) {
            iCal.append("TRANSP:OPAQUE\n");
        } else {
            iCal.append("TRANSP:TRANSPARENT\n");
        }

        iCal.append("UID:").append(ticket.getTicketUid()).append("\n");
        iCal.append("DTSTART:").append(formatDate(ticket.getStartDate())).append("\n");
        iCal.append("DTEND:").append(formatDate(ticket.getEndDate())).append("\n");
        iCal.append("SUMMARY:").append(ticket.getTitle()).append("\n");
        iCal.append("DESCRIPTION:").append(ticket.getTitle()).append("\n");
        iCal.append("CATEGORIES:").append(getICalCategory(ticket)).append("\n");
        iCal.append("END:VEVENT\n");
        iCal.append("END:VCALENDAR\n");
        return iCal.toString();
    }
    
    /**
     * Returns the iCalendar category based on the type of the given CalendarTicket.
     *
     * @param ticket The CalendarTicket for which the category is to be determined.
     * @return The category as a String. Possible values are "Mobile Arbeit", "Gleittag", and "Urlaub".
     * @throws RuntimeException if the CalendarTicket type is not one of MOBILE_WORK, FLEX_DAY, VACATION, or SPECIAL_VACATION.
     */
	private String getICalCategory(CalendarTicket ticket) {
		switch (ticket.getType()) {
		case MOBILE_WORK:
			return "Mobile Arbeit";
		case FLEX_DAY:
			return "Gleittag";
		case VACATION:
		case SEPECIAL_VACATION:
			return "Urlaub";
		default:
			throw new RuntimeException("Invalid Type");
		}
	}

    /**
     * Retrieves the user's mail database entry.
     * 
     * @param cnName the common name of the user
     * @return the mail database entry of the user
     * @throws NotesException 
     * @throws RuntimeException         if the names.nsf database or the ($Users)
     *                                  view could not be found
     * @throws IllegalArgumentException if the user could not be found in the
     *                                  names.nsf database
     */
    private MailDatabaseEntry getUserMailEntry(String cnName) throws NotesException {

        initNamesDatabase();

        List<String> user = getUserEntry(cnName, userView);

        String mailServer = user.get(0);
        String mailFile = user.get(1);
        
        return new MailDatabaseEntry(mailServer, mailFile);
    }

    /**
     * Retrieves the names.nsf database.
     * 
     * @return the names.nsf database
     * @throws NotesException 
     * @throws RuntimeException if the names.nsf database could not be found or
     *                          opened
     */
    private void initNamesDatabase() throws NotesException {

        // only open once
        if (namesDB == null) {

            // TEST: hardcoded server name
            // Database namesDB = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE",
            // "names.nsf");
            namesDB = serverAgentSession.getDatabase(null, "names.nsf", false);

            if (namesDB == null) {
                throw new RuntimeException("Could not find or open the names.nsf database");
            } else {

                userView = namesDB.getView("($Users)");

                if (userView == null) {
                    throw new RuntimeException("Could not find the view ($Users) in the names.nsf database");
                }
            }
        }
    }

    /**
     * Retrieves the user entry from the ($Users) view.
     * 
     * @param cnName   the common name of the user
     * @param userView the ($Users) view
     * @return the user entry
     * @throws NotesException 
     * @throws IllegalArgumentException if the user could not be found in the
     *                                  names.nsf database
     */
    private List<String> getUserEntry(String cnName, View userView) throws NotesException {

        ViewEntryCollection users = null;
        ViewEntry user = null;

        List<String> result = new ArrayList<>();

        try {

            users = userView.getAllEntriesByKey(cnName);

            if (users.getCount() != 1) {
                throw new IllegalArgumentException("Could not find the user" + cnName + " in the names.nsf database");
            }

            user = users.getFirstEntry();

            String mailServer = user.getColumnValues().get(5).toString();
            String mailFile = user.getColumnValues().get(6).toString();

            result.add(mailServer);
            result.add(mailFile);

        } finally {
            TNotesUtil.recycleNotesObject(users, user);
        }

        return result;
    }

    /**
     * Handles any exceptions that occur during the processing of a calendar ticket.
     * 
     * @param e      the exception that occurred
     * @param ticket the calendar ticket that was being processed when the exception
     *               occurred
     * @throws NotesException 
     * @throws RuntimeException if the ticket document could not be found in the
     *                          database
     */
    private void handleTicketError(Exception e, CalendarTicket ticket) throws NotesException {

        // dko: Log to openLog
        TNotesUtil.stdErrorHandler(e);

        /*
         * später: z.B. bei Storno nicht auffindbare Einträge müssen nicht als Fehler
         * behandelt werden
         * boolean handleAsSuccess = false;
         * 
         * if (e instanceof NotesException) {
         * NotesException ne = (NotesException) e;
         * handleAsSuccess = ne.id == NotesError.NOTES_ERR_INVALIDID;
         * }
         */

        String logErrMsg = "Exception Class: " + e.getClass().getName() + " Cause: " + e.getCause() + " Message: " + e.getMessage();

        logFailedTicket(ticket, logErrMsg);

        consoleLog("Error processing ticket " + ticket.getTicketDocumentUnid() + ": " + logErrMsg);

        // try to update the ticket document with the error message
        updateTicketStatus(ticket, ERROR_STATUS, logErrMsg);
    }

    /**
     * Formats a date into the 'yyyyMMdd' format.
     * 
     * @param date the date to be formatted
     * @return a string representing the formatted date
     */
    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        return formatter.format(date);
    }

    /**
     * Logs a message to the console.
     * 
     * @param message the message to be logged
     */
    private void consoleLog(String message) {
        System.out.println(logPrefix + message);
    }
}
