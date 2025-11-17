package org.ietf.ietfsched.util;

import android.content.Context;
import android.content.SharedPreferences;
import org.ietf.ietfsched.io.MeetingMetadata;
import java.util.TimeZone;

/**
 * Utility for saving and retrieving current meeting metadata.
 */
public class MeetingPreferences {
    private static final String PREFS_NAME = "meeting_prefs";
    private static final String KEY_MEETING_NUMBER = "meeting_number";
    private static final String KEY_MEETING_TIMEZONE = "meeting_timezone";
    private static final String KEY_MEETING_START = "meeting_start";
    private static final String KEY_MEETING_END = "meeting_end";
    private static final String KEY_AGENDA_URL = "agenda_url";
    
    /**
     * Saves the current meeting metadata to preferences.
     */
    public static void saveCurrentMeeting(Context context, MeetingMetadata meeting) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_MEETING_NUMBER, meeting.number)
            .putString(KEY_MEETING_TIMEZONE, meeting.timezone.getID())
            .putLong(KEY_MEETING_START, meeting.startMillis)
            .putLong(KEY_MEETING_END, meeting.endMillis)
            .putString(KEY_AGENDA_URL, meeting.agendaUrl)
            .apply();
    }
    
    /**
     * Retrieves the saved meeting number, or 0 if not set.
     */
    public static int getCurrentMeetingNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_MEETING_NUMBER, 0);
    }
    
    /**
     * Retrieves the saved meeting timezone, or UTC if not set.
     */
    public static TimeZone getCurrentMeetingTimeZone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String tzId = prefs.getString(KEY_MEETING_TIMEZONE, "UTC");
        return TimeZone.getTimeZone(tzId);
    }
}
