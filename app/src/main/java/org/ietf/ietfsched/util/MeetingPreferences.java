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
    private static final String KEY_MEETING_CITY = "meeting_city";
    private static final String KEY_MEETING_TIMEZONE = "meeting_timezone";
    private static final String KEY_MEETING_START = "meeting_start";
    private static final String KEY_MEETING_END = "meeting_end";
    private static final String KEY_AGENDA_URL = "agenda_url";
    private static final String KEY_NEXT_MEETING_NUMBER = "next_meeting_number";
    private static final String KEY_NEXT_MEETING_CITY = "next_meeting_city";

    /**
     * Saves the current meeting metadata to preferences.
     */
    public static void saveCurrentMeeting(Context context, MeetingMetadata meeting) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_MEETING_NUMBER, meeting.number)
            .putString(KEY_MEETING_CITY, meeting.city != null ? meeting.city : "")
            .putString(KEY_MEETING_TIMEZONE, meeting.timezone.getID())
            .putLong(KEY_MEETING_START, meeting.startMillis)
            .putLong(KEY_MEETING_END, meeting.endMillis)
            .putString(KEY_AGENDA_URL, meeting.agendaUrl)
            .apply();
    }

    /**
     * Saves the nearest upcoming meeting (may be null to clear).
     * Used for the post-meeting "See you at …" bar.
     */
    public static void saveNextMeeting(Context context, MeetingMetadata next) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (next == null) {
            editor.remove(KEY_NEXT_MEETING_NUMBER).remove(KEY_NEXT_MEETING_CITY);
        } else {
            editor.putInt(KEY_NEXT_MEETING_NUMBER, next.number)
                    .putString(KEY_NEXT_MEETING_CITY, next.city != null ? next.city : "");
        }
        editor.apply();
    }

    /**
     * Applies saved meeting timezone/dates into {@link UIUtils} for cold start.
     * @return true if prefs contained a meeting with valid dates
     */
    public static boolean applySavedMeetingToUiUtils(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long start = prefs.getLong(KEY_MEETING_START, 0);
        long end = prefs.getLong(KEY_MEETING_END, 0);
        if (start == 0 || end == 0) {
            return false;
        }
        String tzId = prefs.getString(KEY_MEETING_TIMEZONE, "UTC");
        UIUtils.setConferenceTimeZone(TimeZone.getTimeZone(tzId));
        UIUtils.setConferenceDates(start, end);
        return true;
    }

    /**
     * Retrieves the saved meeting number, or 0 if not set.
     */
    public static int getCurrentMeetingNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_MEETING_NUMBER, 0);
    }

    public static String getCurrentMeetingCity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MEETING_CITY, "");
    }

    public static long getCurrentMeetingStart(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_MEETING_START, 0);
    }

    public static long getCurrentMeetingEnd(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_MEETING_END, 0);
    }

    /**
     * Next upcoming meeting number, or 0 if unknown.
     */
    public static int getNextMeetingNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_NEXT_MEETING_NUMBER, 0);
    }

    public static String getNextMeetingCity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_NEXT_MEETING_CITY, "");
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
