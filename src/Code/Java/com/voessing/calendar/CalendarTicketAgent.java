package com.voessing.calendar;

import java.util.List;
import java.util.stream.Collectors;

import org.openntf.domino.Database;
import org.openntf.domino.DocumentCollection;
import org.openntf.domino.Session;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import lotus.domino.NotesCalendar;
import lotus.domino.NotesCalendarEntry;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

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
        DocumentCollection tickets = azeDb.search("Form = \"CalendarTicket\"");
        return tickets.stream().map(CalendarTicket::new).collect(Collectors.toList());
    }

    private void processTicket(CalendarTicket ticket) {
        
    }

    public void createCalendarEntry(MailEntry mailEntry) throws NotesException {
        Database userMailDB = serverAgentSession.getDatabase(mailEntry.getMailServer(), mailEntry.getMailFile());
        
        if(userMailDB == null) {
            throw new IllegalArgumentException("Database not found");
        }

        NotesCalendar userCalendar = serverAgentSession.getCalendar(userMailDB);

        System.out.println(userMailDB);
        System.out.println(userCalendar);
        NotesCalendarEntry entry = userCalendar.createEntry(generateICal());
    }

    private String generateICal(){
        StringBuilder iCal = new StringBuilder();
        iCal.append("BEGIN:VCALENDAR\n");
        iCal.append("BEGIN:VEVENT\n");
        iCal.append("DTSTART:20240507T160000\n");
        iCal.append("DTEND:20240507T180000\n");
        iCal.append("SUMMARY:Urlaub\n");
        iCal.append("END:VEVENT\n");
        iCal.append("END:VCALENDAR\n");
        return iCal.toString();
    }

    private MailEntry getUserMailEntry(String cnName) throws NotesException {
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
