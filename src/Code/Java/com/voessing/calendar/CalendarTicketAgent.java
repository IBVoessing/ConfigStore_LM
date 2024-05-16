package com.voessing.calendar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.openntf.domino.Database;
import org.openntf.domino.Document;
import org.openntf.domino.DocumentCollection;
import org.openntf.domino.NotesCalendar;
import org.openntf.domino.NotesCalendarEntry;
import org.openntf.domino.Session;
import org.openntf.domino.View;
import org.openntf.domino.ViewEntry;
import org.openntf.domino.ViewEntryCollection;
import org.openntf.domino.utils.DominoUtils;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.google.gson.JsonObject;

/**
 * The `CalendarTicketAgent` class represents an agent that processes calendar tickets.
 * It provides methods to load open tickets, process each ticket individually, and manage calendar entries associated with the tickets.
 * This class interacts with a database to retrieve and update ticket information.
 */
public class CalendarTicketAgent {
    
    Session serverAgentSession;
    Database azeDb;

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
    
    public CalendarTicketAgent() {
        // SessionType.CURRENT = Current User Session
        // SessionType.NATIVE = Server Session 
        serverAgentSession = Factory.getSession(SessionType.CURRENT);
        //TODO: change hardcoded server name to session.getServerName()
        azeDb = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "AZEApp.nsf");
        // important as otherwise we don´t get the error when checking if a NotesCalendarEntry is valid or not
        DominoUtils.setBubbleExceptions(true);
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
     */
    public void processTickets() {
        List<CalendarTicket> openTickets = loadOpenTickets();
        consoleLog(openTickets.size() + " tickets loaded");
        
        for(CalendarTicket ticket : openTickets) {
            processTicket(ticket);
        }

        consoleLog("Processing complete");
        consoleLog(getReport());
    }

    /**
     * Loads all open calendar tickets from the database.
     * 
     * <p>
     * This method performs a search in the database for documents that have the
     * form 'CalendarTicket', have not been processed. Each document is
     * then converted into a {@link CalendarTicket} object.
     * 
     * @return a list of open {@link CalendarTicket} objects
     */
    private List<CalendarTicket> loadOpenTickets() {
        // load all tickets from the database
        DocumentCollection tickets = azeDb.search(
                "Form = \"CalendarTicket\" & AgentStatus != \"processed\"");
        return tickets.stream().map(CalendarTicket::new).collect(Collectors.toList());
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
     */
    private void processTicket(CalendarTicket ticket) {
        try {
            MailDatabaseEntry mailEntry = getUserMailEntry(ticket.getRequester());
            manageCalendarEntry(mailEntry, ticket);

            updateTicketStatus(ticket, true, null);
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
     */
    private void updateTicketStatus(CalendarTicket ticket, boolean success, String errorMsg) {
        Document ticketDoc = azeDb.getDocumentByUNID(ticket.getTicketDocumentUnid());
        ticketDoc.replaceItemValue("AgentStatus", success ? "processed" : "error");
        ticketDoc.replaceItemValue("AgentError", errorMsg);
        ticketDoc.save();
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
     */
    private void manageCalendarEntry(MailDatabaseEntry mailEntry, CalendarTicket ticket) {
        Database userMailDB = getUserMailDatabase(mailEntry);
        NotesCalendar userCalendar = getUserCalendar(userMailDB);
        NotesCalendarEntry entry = getCalendarEntry(userCalendar, ticket);

        if (ticket.toBeDeleted()) {
            deleteCalendarEntry(entry);
        } else {
            updateOrCreateCalendarEntry(userCalendar, entry, ticket);
        }
    }

    /**
     * Retrieves the user's mail database.
     * 
     * @param mailEntry the mail database entry of the user
     * @return the user's mail database
     * @throws IllegalArgumentException if the database could not be found
     */
    private Database getUserMailDatabase(MailDatabaseEntry mailEntry) {
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
     */
    private NotesCalendar getUserCalendar(Database userMailDB) {
        return serverAgentSession.getCalendar(userMailDB);
    }

    /**
     * Retrieves a calendar entry from the user's calendar using the ticket UID.
     * 
     * @param userCalendar the user's calendar
     * @param ticket       the calendar ticket
     * @return the calendar entry associated with the ticket
     */
    private NotesCalendarEntry getCalendarEntry(NotesCalendar userCalendar, CalendarTicket ticket) {
        return userCalendar.getEntry(ticket.getTicketUid());
    }

    /**
     * Deletes a calendar entry.
     * 
     * @param entry the calendar entry to be deleted
     * @throws IllegalArgumentException if the entry to be deleted could not be
     *                                  found
     */
    private void deleteCalendarEntry(NotesCalendarEntry entry) {
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
     */
    private void updateOrCreateCalendarEntry(NotesCalendar userCalendar, NotesCalendarEntry entry, CalendarTicket ticket) {
        // we can´t check if an entry is valid or not, so we just try to update it
        try {
            entry.update(generateICal(ticket), "Anfrag wurde aktualisiert",
                    NotesCalendar.CS_WRITE_DISABLE_IMPLICIT_SCHEDULING +
                            NotesCalendar.CS_WRITE_MODIFY_LITERAL);
        } catch (Exception e) {
            // if the entry does not exist, create a new one
            userCalendar.createEntry(generateICal(ticket), NotesCalendar.CS_WRITE_DISABLE_IMPLICIT_SCHEDULING);
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
        iCal.append("END:VEVENT\n");
        iCal.append("END:VCALENDAR\n");
        return iCal.toString();
    }

    /**
     * Retrieves the user's mail database entry.
     * 
     * @param cnName the common name of the user
     * @return the mail database entry of the user
     * @throws RuntimeException         if the names.nsf database or the ($Users)
     *                                  view could not be found
     * @throws IllegalArgumentException if the user could not be found in the
     *                                  names.nsf database
     */
    private MailDatabaseEntry getUserMailEntry(String cnName) {
        Database namesDB = getNamesDatabase();
        View userView = getUserView(namesDB);
        ViewEntry user = getUserEntry(cnName, userView);

        String mailServer = user.getColumnValues().get(5).toString();
        String mailFile = user.getColumnValues().get(6).toString();

        return new MailDatabaseEntry(mailServer, mailFile);
    }

    /**
     * Retrieves the names.nsf database.
     * 
     * @return the names.nsf database
     * @throws RuntimeException if the names.nsf database could not be found or
     *                          opened
     */
    private Database getNamesDatabase() {
        //TODO: change hardcoded server name to session.getServerName()
        Database namesDB = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "names.nsf");
        if (namesDB == null) {
            throw new RuntimeException("Could not find or open the names.nsf database");
        }
        return namesDB;
    }

    /**
     * Retrieves the ($Users) view from the names.nsf database.
     * 
     * @param namesDB the names.nsf database
     * @return the ($Users) view
     * @throws RuntimeException if the ($Users) view could not be found in the
     *                          names.nsf database
     */
    private View getUserView(Database namesDB) {
        View userView = namesDB.getView("($Users)");
        if (userView == null) {
            throw new RuntimeException("Could not find the view ($Users) in the names.nsf database");
        }
        return userView;
    }

    /**
     * Retrieves the user entry from the ($Users) view.
     * 
     * @param cnName   the common name of the user
     * @param userView the ($Users) view
     * @return the user entry
     * @throws IllegalArgumentException if the user could not be found in the
     *                                  names.nsf database
     */
    private ViewEntry getUserEntry(String cnName, View userView) {
        ViewEntryCollection users = userView.getAllEntriesByKey(cnName);
        if (users.getCount() != 1) {
            throw new IllegalArgumentException("Could not find the user" + cnName + " in the names.nsf database");
        }
        return users.getFirstEntry();
    }

    /**
     * Handles any exceptions that occur during the processing of a calendar ticket.
     * 
     * @param e      the exception that occurred
     * @param ticket the calendar ticket that was being processed when the exception
     *               occurred
     * @throws RuntimeException if the ticket document could not be found in the
     *                          database
     */
    private void handleTicketError(Exception e, CalendarTicket ticket) {

        String logErrMsg = "Exception Class: " + e.getClass().getName() + " Cause: " + e.getCause() + " Message: " + e.getMessage();

        logFailedTicket(ticket, logErrMsg);

        consoleLog("Error processing ticket " + ticket.getTicketDocumentUnid() + ": " + logErrMsg);

        Document ticketDoc = azeDb.getDocumentByUNID(ticket.getTicketDocumentUnid());

        if (ticketDoc != null) {
            // try to update the ticket document with the error message
            updateTicketStatus(ticket, false, logErrMsg);
        } else {
            // if the ticket document is not available, escalate the error
            throw new RuntimeException(e);
        }
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
