package com.voessing.calendar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.openntf.domino.Database;
import org.openntf.domino.Document;
import org.openntf.domino.DocumentCollection;
import org.openntf.domino.Session;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import org.openntf.domino.NotesCalendar;
import org.openntf.domino.NotesCalendarEntry;
import org.openntf.domino.View;
import org.openntf.domino.ViewEntry;
import org.openntf.domino.ViewEntryCollection;

public class CalendarTicketAgent {
    
    Session serverAgentSession;
    Database azeDb;

    public static class MailEntry {
        private String mailServer;
        private String mailFile;

        @Override
        public String toString() {
            return "MailEntry [mailServer=" + mailServer + ", mailFile=" + mailFile + "]";
        }

        public MailEntry(String mailServer, String mailFile) {
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
        serverAgentSession = Factory.getSession_unchecked(SessionType.CURRENT);
        //TODO: change hardcoded server name to session.getServerName()
        azeDb = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "AZEApp.nsf");
    } 

    public void processTickets() {
        List<CalendarTicket> openTickets = loadOpenTickets();
        System.out.println(openTickets.size() + " tickets loaded");
        // for(CalendarTicket ticket : openTickets) {
        //     processTicket(ticket);
        //     System.out.println(ticket);
        // }
    }

    private List<CalendarTicket> loadOpenTickets() {
        // load all tickets from the database
        DocumentCollection tickets = azeDb.search("Form = \"CalendarTicket\" & AgentStatus != \"processed\"");
        return tickets.stream().map(CalendarTicket::new).collect(Collectors.toList());
    }

    private void processTicket(CalendarTicket ticket) {
        Document ticketDoc = null;
        try {
            MailEntry mailEntry = getUserMailEntry(ticket.getRequester());
            createCalendarEntry(mailEntry, ticket);

            ticketDoc = azeDb.getDocumentByUNID(ticket.getTicketDocumentUnid());
            ticketDoc.replaceItemValue("AgentStatus", "processed");
        } catch (Exception e) {
            handleTicketError(e, ticketDoc);
        }
    }

    public void createCalendarEntry(MailEntry mailEntry, CalendarTicket ticket) {
        Database userMailDB = serverAgentSession.getDatabase(mailEntry.getMailServer(), mailEntry.getMailFile());

        if (userMailDB == null) {
            throw new IllegalArgumentException("Database not found");
        }

        NotesCalendar userCalendar = serverAgentSession.getCalendar(userMailDB);

        System.out.println(userMailDB);
        System.out.println(userCalendar);
        NotesCalendarEntry entry = userCalendar.createEntry(generateICal(ticket));
    }

    private void handleTicketError(Exception e, Document ticketDoc) {
        if (ticketDoc != null) {
            // try to update the ticket document with the error message
            ticketDoc.replaceItemValue("AgentStatus", "error");
            ticketDoc.replaceItemValue("AgentError", e.getMessage());
        } else {
            // if the ticket document is not available, escalate the error
            throw new RuntimeException(e);
        }
    }

    private String generateICal(CalendarTicket ticket) {
        StringBuilder iCal = new StringBuilder();
        iCal.append("BEGIN:VCALENDAR\n");
        iCal.append("BEGIN:VEVENT\n");
        iCal.append(String.format("DTSTART:%s\n", formatDate(ticket.getStartDate())));
        iCal.append(String.format("DTEND:%s\n", formatDate(ticket.getEndDate())));
        iCal.append(String.format("SUMMARY:%s\n", ticket.getTitle()));
        iCal.append("END:VEVENT\n");
        iCal.append("END:VCALENDAR\n");
        return iCal.toString();
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        return formatter.format(date);
    }

    private MailEntry getUserMailEntry(String cnName) {
        //TODO: change hardcoded server name to session.getServerName()
        Database namesDB = serverAgentSession.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "names.nsf");
        View userView = namesDB.getView("($Users)");

        ViewEntryCollection users = userView.getAllEntriesByKey(cnName);

        if (users.getCount() != 1) {
            return null;
        }

        ViewEntry user = users.getFirstEntry();

        String mailServer = user.getColumnValues().get(5).toString();
        String mailFile = user.getColumnValues().get(6).toString();

        return new MailEntry(mailServer, mailFile);
    }

}
