package com.voessing.vcde.endpoints.vrh.crawler;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openntf.domino.utils.Factory;
import org.openntf.domino.utils.Factory.SessionType;

import com.google.gson.JsonArray;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.voessing.api.adapter.GraphAPINew;
import com.voessing.api.adapter.GraphAPINew.GraphUtil;
import com.voessing.calendar.CalendarTicketAgent;
import com.voessing.common.TNotesUtil;
import com.voessing.common.http.Response;
import com.voessing.xapps.utils.vrh.configs.VrhResourceHandlerConfig;
import com.voessing.xapps.utils.vrh.handler.VrhHttpHandler;

import lotus.domino.Database;
import lotus.domino.NotesCalendar;
import lotus.domino.NotesCalendarEntry;
import lotus.domino.NotesException;
import lotus.domino.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;



public class Test extends VrhHttpHandler {

	@Override
	protected VrhResourceHandlerConfig provideConfig(VrhResourceHandlerConfig initialConfig,
			Map<String, String[]> parameterMap) throws Exception {
		config.setAllowedMethods("GET, POST, HEAD");
		return initialConfig;
	}

	@Override
	protected String doGet(HttpServletRequest request) throws Exception {

		CalendarTicketAgent cta = new CalendarTicketAgent();
		cta.processTickets();
		// testCalendarCreation();

		return "";
	}

	private void testCalendarCreation() {
		try {
			//Session session = Factory.getSession(SessionType.NATIVE);
			Session session = NotesContext.getCurrent().getCurrentSession();
			//Database db = session.getDatabase("CN=mail1-voessing.dom001-ce.cloud-y.com/OU=SCY/O=IBV/C=DE","mail0/2000004851.nsf");
			Database db = session.getDatabase("CN=IBVDNO03/O=IBV/C=DE", "mail/tentwic.nsf");
					
			System.out.println("User => " + session.getEffectiveUserName());
			System.out.println("DB => " + db);
			System.out.println("DB Open? => " + db.isOpen());
			System.out.println("DB.getCurretnAccessLevel: " + db.getCurrentAccessLevel());
			
			
			NotesCalendar nc = session.getCalendar(db);

			//try to create a new entry
			//NotesCalendarEntry nceNew = nc.createEntry(generateICal());
//			NotesCalendarEntry nceNew = nc.getEntryByUNID("FC4FDBB8B81DD84A731B6FDFC809D9FD");
//			nceNew.update(generateICal());
//			System.out.println("Entry => " + nceNew);
//			System.out.println("Entry UID => " + nceNew.getUID());
//			System.out.println("Entry READ => " + nceNew.read());
//			System.out.println("Entry AS DOCUMENT => " + nceNew.getAsDocument());

			// // this uid does not exist ffs
			// NotesCalendarEntry nce = nc.getEntryByUNID("985F6C908E726BDB96650EAD67684CDE");

			// System.out.println("Entry => " + nce);
			// System.out.println(db.getDocumentByUNID("985F6C908E726BDB96650EAD67684CDE"));
			// //nce.remove();
			// //System.out.println("did remove work??");
			// //nce.update(generateICal());
			// //System.out.println("did updating a not existing entry work=?=?");
			// // das schmeißt exception
			// System.out.println(nce.getAsDocument());
			// // das auch 
			// System.out.println(nce.read());
		} catch (Exception e) {
			System.out.println("Error => " + e.getMessage());
			throw new RuntimeException(e);
		}

	}
	
    private String generateICal() {
        StringBuilder iCal = new StringBuilder();
        iCal.append("BEGIN:VCALENDAR\n");
        iCal.append("PRODID:AZE Import\n");
        iCal.append("BEGIN:VEVENT\n");
        iCal.append("UID:testUID123").append("\n");
        iCal.append("DTSTART:20240509").append("\n");
        iCal.append("DTEND:20240528").append("\n");
        iCal.append("SUMMARY:WeeWuu").append("\n");
        iCal.append("DESCRIPTION:WeeWuu").append("\n");
        iCal.append("END:VEVENT\n");
        iCal.append("END:VCALENDAR\n");
        TNotesUtil.logEvent(iCal.toString());
        return iCal.toString();
    }

	@Override
	protected String doPost(HttpServletRequest request) throws Exception {

		return "skfjaskjfklsajfljasdf";
	}

}
