package org.ietf.ietfsched.io;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Represents metadata for an IETF meeting.
 */
public class MeetingMetadata {
    private static final String TAG = "MeetingMetadata";
    private static final SimpleDateFormat ISO_DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    
    public final int number;
    public final String name;
    public final String city;
    public final String country;
    public final TimeZone timezone;
    public final long startMillis;
    public final long endMillis;
    public final String agendaUrl;
    public final boolean agendaAvailable;
    
    /**
     * Parses meeting metadata from JSON object returned by Datatracker API.
     * 
     * Example JSON:
     * {
     *   "number": "125",
     *   "type": "/api/v1/name/meetingtypename/ietf/",
     *   "date": "2024-11-02",
     *   "end_date": "2024-11-03",
     *   "city": "Vancouver",
     *   "country": "CA",
     *   "time_zone": "America/Vancouver",
     *   ...
     * }
     */
    public static MeetingMetadata fromJSON(JSONObject json) throws JSONException {
        int number = Integer.parseInt(json.getString("number"));
        String date = json.getString("date");
        String city = json.optString("city", "Unknown");
        String country = json.optString("country", "Unknown");
        String timezoneId = json.optString("time_zone", "UTC");
        
        TimeZone tz;
        try {
            tz = TimeZone.getTimeZone(timezoneId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse timezone " + timezoneId + ", using UTC", e);
            tz = TimeZone.getTimeZone("UTC");
        }
        
        long startMillis = 0;
        long endMillis = 0;
        try {
            Date startDate = ISO_DATE_FORMAT.parse(date);
            if (startDate != null) {
                startMillis = startDate.getTime();
                // IMPORTANT: The API's end_date is unreliable (sometimes before start date!).
                // IETF meetings are typically 7 days long, so calculate end date from start.
                // Add 6 full days plus end-of-day (23:59:59) to the start date.
                long WEEK_IN_MILLIS = 6 * 24 * 60 * 60 * 1000L;
                endMillis = startMillis + WEEK_IN_MILLIS + (24 * 60 * 60 * 1000 - 1);
                Log.d(TAG, "Calculated end date: start=" + date + ", end is 6 days later");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse meeting dates", e);
        }
        
        String name = "IETF " + number;
        String agendaUrl = "https://datatracker.ietf.org/meeting/" + number + "/agenda.json";
        
        return new MeetingMetadata(number, name, city, country, tz, 
                                   startMillis, endMillis, agendaUrl, false);
    }
    
    private MeetingMetadata(int number, String name, String city, String country,
                           TimeZone timezone, long startMillis, long endMillis,
                           String agendaUrl, boolean agendaAvailable) {
        this.number = number;
        this.name = name;
        this.city = city;
        this.country = country;
        this.timezone = timezone;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.agendaUrl = agendaUrl;
        this.agendaAvailable = agendaAvailable;
    }
    
    /**
     * Creates a copy of this metadata with agendaAvailable flag set.
     */
    public MeetingMetadata withAgendaAvailability(boolean available) {
        return new MeetingMetadata(number, name, city, country, timezone,
                                  startMillis, endMillis, agendaUrl, available);
    }
}
