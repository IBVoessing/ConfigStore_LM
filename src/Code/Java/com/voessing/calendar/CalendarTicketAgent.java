package com.voessing.calendar;

import java.util.List;
import java.util.stream.Collectors;

import org.openntf.domino.Database;
import org.openntf.domino.DocumentCollection;
import org.openntf.domino.Session;
import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

public class CalendarTicketAgent {
    
    Session serverAgentSession;
    Database azeDb;
    
    public CalendarTicketAgent() {
        // SessionType.CURRENT = Current User Session
        // SessionType.NATIVE = Server Session 
        serverAgentSession = Factory.getSession_unchecked(SessionType.CURRENT);
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

}
