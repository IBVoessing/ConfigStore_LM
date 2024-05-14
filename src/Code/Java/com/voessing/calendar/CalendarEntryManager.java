package com.voessing.calendar;



// import org.openntf.domino.Database;
// import org.openntf.domino.NotesCalendar;
// import org.openntf.domino.NotesCalendarEntry;
// import org.openntf.domino.Session;
// import org.openntf.domino.View;
// import org.openntf.domino.ViewEntry;
// import org.openntf.domino.ViewEntryCollection;
// import org.openntf.domino.utils.Factory;
// import org.openntf.domino.utils.Factory.SessionType;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.domino.xsp.module.nsf.NotesContext;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.NotesCalendar;
import lotus.domino.NotesCalendarEntry;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

public class CalendarEntryManager {

    Session session;
    
    public CalendarEntryManager(Session session) {
        this.session = session;
    }

    public CalendarEntryManager() {
        session = NotesContext.getCurrent().getCurrentSession();
    }

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

    public void createCalendarEntry(MailEntry mailEntry) throws NotesException {
        Database userMailDB = session.getDatabase(mailEntry.getMailServer(), mailEntry.getMailFile());
        
        if(userMailDB == null) {
            throw new IllegalArgumentException("Database not found");
        }

        NotesCalendar userCalendar = session.getCalendar(userMailDB);

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


    public MailEntry getUserMailEntry(String cnName) throws NotesException {
        //TODO: change hardcoded server name to session.getServerName()
        Database namesDB = session.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "names.nsf");
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
