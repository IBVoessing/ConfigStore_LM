package com.voessing.common;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

public class TDateTimeUtil {

	//SimpleDateFormat is not Thread Safe (neither is Calendar)
	
	private static final String GERMAN_DATE 			= "dd.MM.yyyy";
	
	private static final String BASIC_ISO_DATE			= "yyyyMMdd";
	private static final String ISO_LOCAL_DATE			= "yyyy-MM-dd";
	private static final String ISO_LOCAL_DATE_TIME 	= "yyyy-MM-dd'T'HH:mm:ss";
	
	private static final String ISO_OFFSET_DATE_TIME	= "yyyy-MM-dd'T'HH:mm:ssz";
	private static final String ISO_INSTANT				= ISO_OFFSET_DATE_TIME;	//same format for SimpleDateFormat-processor

	private static final String ISO_OFFSET_DATE_TIME_MS = "yyyy-MM-dd'T'HH:mm:ss.SSSz";
	
	//nur als Z-zeit mit Millisekunden	
	private static final String ECMA_INSTANT			= ISO_OFFSET_DATE_TIME_MS;
	
	//TODO: später (toString()) private static final TimeZone tz = TimeZone.getTimeZone( "UTC" );
	
	public static Date parse(String dateString) throws ParseException {

		String format = null;
		
		int dsLen = dateString.length();
		
		//Erkennung: deutsches Format oder ISO
		if (dsLen==GERMAN_DATE.length() && dateString.indexOf('.')==2) {
			format = GERMAN_DATE;
		} else {

			//ISO
	        //NOTE: SimpleDateFormat uses GMT[-+]hh:mm for the TZ which breaks
	        //things a bit.  Before we go on we have to repair this.

			if (dateString.endsWith("Z")) {
				
				//this is zero time so we need to add that TZ indicator
				//"Z" ersetzen durch "GMT-00:00"
				dateString = dateString.substring(0, dsLen-1) + "GMT-00:00";

				if (dateString.indexOf('.')>-1) {
					//mit Millisekunden
					format = ECMA_INSTANT;
				} else {
					//ohne Millisekunden
					format = ISO_INSTANT;
				}
				
			} else {
				
				if (dsLen==BASIC_ISO_DATE.length()) {
					format = BASIC_ISO_DATE;
				} else if (dsLen==ISO_LOCAL_DATE.length()) {
					format = ISO_LOCAL_DATE;
				} else if (dsLen==ISO_LOCAL_DATE_TIME.length()-2) {  //-2 wg. 'T'
					//Zeitanteil ist enthalten, aber ohne Offset-Info
					format = ISO_LOCAL_DATE_TIME;
				} else {
					
					//Zeitanteil ist enthalten, mit Offset-Info: für SimpleDateFormat anpassen
					int inset = 6;
					
					//check if TZ already contains zone ID: Test for alpabetic character at the correct position
		            int lastCharCode = dateString.charAt(dsLen-inset-1);
		            
		            //add zone ID if not present
		            if (!(lastCharCode>=65 && lastCharCode<=90)) {
		            	String s0 = dateString.substring(0, dsLen - inset);
			            String s1 = dateString.substring(dsLen - inset, dsLen);
		            	dateString = s0 + "GMT" + s1;
		            }
		            
					//Mit oder ohne Millisekunden
					if (dateString.indexOf('.')>-1) {
						//mit Millisekunden
						format = ISO_OFFSET_DATE_TIME_MS;
					} else {
						//ohne Millisekunden
						format = ISO_OFFSET_DATE_TIME;
					}
					
				}
				
			}
			
			
		}

        SimpleDateFormat df = new SimpleDateFormat(format);
        return df.parse(dateString);
		
	}
	
	public static String toISOOffsetDateTimeMs(Date date) {

		SimpleDateFormat df = new SimpleDateFormat(ISO_OFFSET_DATE_TIME_MS);
		
		//auf UTC umrechnen
		TimeZone tz = TimeZone.getTimeZone( "UTC" );
		df.setTimeZone( tz );
		
		//ergibt: yyyy-MM-ddTHH:mm:ss.SSSUTC
		String output = df.format(date);
		
		return output.replaceAll("UTC", "Z");
	}

	public static String toISOOffsetDateTime(Date date) {

		SimpleDateFormat df = new SimpleDateFormat(ISO_OFFSET_DATE_TIME);
		
		//auf UTC umrechnen
		TimeZone tz = TimeZone.getTimeZone( "UTC" );
		df.setTimeZone( tz );
		
		//ergibt: yyyy-MM-ddTHH:mm:ssUTC
		String output = df.format(date);
		
		return output.replaceAll("UTC", "Z");
	}

	public static String toFormattedDate(Date date, String dateFormat) {
		
		SimpleDateFormat df = new SimpleDateFormat(dateFormat);
		String output = df.format(date);
		
		return output;
	}
	
	public static String toGermanDate(Date date) {
		
		SimpleDateFormat df = new SimpleDateFormat(GERMAN_DATE);
		String output = df.format(date);
		
		return output;
	}
	
	public static LocalDateTime convertToLocalDateTime(Date dateToConvert) {
	    return Instant.ofEpochMilli(dateToConvert.getTime())
	      .atZone(ZoneId.systemDefault())
	      .toLocalDateTime();
	}
	
	/*TODO sp�ter
    public static String toString( Date date ) {
        
        SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz" );
        
        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        
        df.setTimeZone( tz );

        String output = df.format( date );

        int inset0 = 9;
        int inset1 = 6;
        
        String s0 = output.substring( 0, output.length() - inset0 );
        String s1 = output.substring( output.length() - inset1, output.length() );

        String result = s0 + s1;

        result = result.replaceAll( "UTC", "+00:00" );
        
        return result;
        
    }
*/
	

	/*OK
	
	if (dateString.endsWith("Z")) {
		
		if (dateString.indexOf('.')>-1) {
			
			SimpleDateFormat df = new SimpleDateFormat(ECMA_INSTANT);
			
			//this is zero time so we need to add that TZ indicator
			//"Z" ersetzen durch "GMT-00:00"
			dateString = dateString.substring(0, dsLen-1) + "GMT-00:00";

			return df.parse(dateString);
		
		} else {
			
			//ISO_INSTANT annehmen
			SimpleDateFormat df = new SimpleDateFormat(ISO_INSTANT);

			//this is zero time so we need to add that TZ indicator
			//"Z" ersetzen durch "GMT-00:00"
			dateString = dateString.substring(0, dsLen-1) + "GMT-00:00";

			return df.parse(dateString);
		}
		
	} else {
		
		//eins der anderen ISO-Formate
		if (dsLen==BASIC_ISO_DATE.length()) {
			SimpleDateFormat df = new SimpleDateFormat(BASIC_ISO_DATE);
			return df.parse(dateString);
		} else if (dsLen==ISO_LOCAL_DATE.length()) {
			SimpleDateFormat df = new SimpleDateFormat(ISO_LOCAL_DATE);
			return df.parse(dateString);
		} else if (dsLen==ISO_LOCAL_DATE_TIME.length()-2) {  //-2 wg. 'T'
			//Zeitanteil ist enthalten, aber ohne Offset-Info
			SimpleDateFormat df = new SimpleDateFormat(ISO_LOCAL_DATE_TIME);
			return df.parse(dateString);
		} else {
			//Zeitanteil ist enthalten, mit Offset-Info
			int inset = 6;
	        
            String s0 = dateString.substring(0, dsLen - inset);
            String s1 = dateString.substring(dsLen - inset, dsLen);

			SimpleDateFormat df = new SimpleDateFormat(ISO_OFFSET_DATE_TIME);
            return df.parse(s0 + "GMT" + s1);
		}
		
	}
	*/
	
    // http://www.cl.cam.ac.uk/~mgk25/iso-time.html
    //    
    // http://www.intertwingly.net/wiki/pie/DateTime
    //
    // http://www.w3.org/TR/NOTE-datetime
    //
    // Different standards may need different levels of granularity in the date and
    // time, so this profile defines six levels. Standards that reference this
    // profile should specify one or more of these granularities. If a given
    // standard allows more than one granularity, it should specify the meaning of
    // the dates and times with reduced precision, for example, the result of
    // comparing two dates with different precisions.

    // The formats are as follows. Exactly the components shown here must be
    // present, with exactly this punctuation. Note that the "T" appears literally
    // in the string, to indicate the beginning of the time element, as specified in
    // ISO 8601.

    //    Year:
    //       YYYY (eg 1997)
    //    Year and month:
    //       YYYY-MM (eg 1997-07)
    //    Complete date:
    //       YYYY-MM-DD (eg 1997-07-16)
    //    Complete date plus hours and minutes:
    //       YYYY-MM-DDThh:mmTZD (eg 1997-07-16T19:20+01:00)
    //    Complete date plus hours, minutes and seconds:
    //       YYYY-MM-DDThh:mm:ssTZD (eg 1997-07-16T19:20:30+01:00)
    //    Complete date plus hours, minutes, seconds and a decimal fraction of a
    // second
    //       YYYY-MM-DDThh:mm:ss.sTZD (eg 1997-07-16T19:20:30.45+01:00)

    // where:

    //      YYYY = four-digit year
    //      MM   = two-digit month (01=January, etc.)
    //      DD   = two-digit day of month (01 through 31)
    //      hh   = two digits of hour (00 through 23) (am/pm NOT allowed)
    //      mm   = two digits of minute (00 through 59)
    //      ss   = two digits of second (00 through 59)
    //      s    = one or more digits representing a decimal fraction of a second
    //      TZD  = time zone designator (Z or +hh:mm or -hh:mm)
	
	/*
	 * org
    public static Date parse1( String input ) throws java.text.ParseException {

        //NOTE: SimpleDateFormat uses GMT[-+]hh:mm for the TZ which breaks
        //things a bit.  Before we go on we have to repair this.
        SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssz" );
        
        //this is zero time so we need to add that TZ indicator for 
        if ( input.endsWith( "Z" ) ) {
            input = input.substring( 0, input.length() - 1) + "GMT-00:00";
        } else {
            int inset = 6;
        
            String s0 = input.substring( 0, input.length() - inset );
            String s1 = input.substring( input.length() - inset, input.length() );

            input = s0 + "GMT" + s1;
        }
        
        return df.parse( input );
        
    }
    */

	
}
