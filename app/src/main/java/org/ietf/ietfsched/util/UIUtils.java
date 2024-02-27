/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ietf.ietfsched.util;


import org.ietf.ietfsched.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * An assortment of UI helpers.
 */
public class UIUtils {
    /**
     * NOTE: JSON Agenda stores times as UTC.
     * The CONFERENCE_TIME_ZONE below is local offset from GMT: pdt -> GMT-08:00.
     * The AGENDA_TIME_ZONE is UTC, GMT-00:00.
     * BKK +7
     * PDT -8 - ietf117
     * UTC - ietf109
     * VIE +1 ietf113
     * SFO -7 - ietf111
     * PHL -4 EDT
     * NRT +9 JST GMT+9:00 - Use the GMT offset notation from now on.
     */
    private static final String TAG = "UIUtils";
    // Conference timezone is the local used at the venue.
    public static final TimeZone CONFERENCE_TIME_ZONE = TimeZone.getTimeZone("GMT+11:00");
    // Agenda is published with timezones as UTC.
    public static final TimeZone AGENDA_TIME_ZONE = TimeZone.getTimeZone("GMT+00:00");

    // Date/Time here is format: "yyyy-MM-dd HH:mm:00TZ" - ParserUtils.java:59
    public static final Long CONFERENCE_START_MILLIS = ParserUtils.parseTime(
            String.format("2024-03-22 07:00:00%s", CONFERENCE_TIME_ZONE.getID()));
    public static final Long CONFERENCE_END_MILLIS = ParserUtils.parseTime(
            String.format("2024-03-30 22:00:00%s", CONFERENCE_TIME_ZONE.getID()));

    /** Flags used with {@link DateUtils#formatDateRange}. */
    private static final int TIME_FLAGS = DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;

    /** {@link StringBuilder} used for formatting time block. */
    private static final StringBuilder sBuilder = new StringBuilder(50);

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    public static String formatSessionSubtitle(long blockStart, long blockEnd,
            String roomName, Context context) {
        // Convert the blockStart/blockEnd to localtimezone epochMillis.
        LocalDateTime blockStartDate = Instant.ofEpochMilli(blockStart).atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime blockEndDate = Instant.ofEpochMilli(blockEnd).atZone(ZoneId.systemDefault()).toLocalDateTime();
        ZonedDateTime zdtBlockStart = ZonedDateTime.of(blockStartDate, ZoneId.systemDefault());
        ZonedDateTime zdtBlockEnd = ZonedDateTime.of(blockEndDate, ZoneId.systemDefault());
        final CharSequence timeString = DateUtils.formatDateRange(context,
                zdtBlockStart.toInstant().toEpochMilli(), zdtBlockEnd.toInstant().toEpochMilli(), TIME_FLAGS);
			
		return roomName == null ? context.getString(R.string.session_subtitle_no_room, timeString)
				: context.getString(R.string.session_subtitle_room, timeString, roomName);
			
    }

    /**
     * Populate the given {@link TextView} with the requested text, formatting
     * through {@link Html#fromHtml(String)} when applicable. Also sets
     * {@link TextView#setMovementMethod} so inline links are handled.
     */
    public static void setTextMaybeHtml(TextView view, String text) {
        if (TextUtils.isEmpty(text)) {
            view.setText("");
            return;
        }
        if (text.contains("<") && text.contains(">")) {
            view.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
            view.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            view.setText(text);
        }
    }

    public static void setSessionTitleColor(long blockEnd, TextView title,
            TextView subtitle) {
        long currentTimeMillis = System.currentTimeMillis();
        int colorId = R.color.body_text_1;
        int subColorId = R.color.body_text_2;

        if (currentTimeMillis > blockEnd &&
                currentTimeMillis < CONFERENCE_END_MILLIS) {
            colorId = subColorId = R.color.body_text_disabled;
        }

        final Resources res = title.getResources();
        title.setTextColor(res.getColor(colorId, null));
        subtitle.setTextColor(res.getColor(subColorId, null));
    }

    /**
     * Given a snippet string with matching segments surrounded by curly
     * braces, turn those areas into bold spans, removing the curly braces.
     */
    public static Spannable buildStyledSnippet(String snippet) {
        final SpannableStringBuilder builder = new SpannableStringBuilder(snippet);

        // Walk through string, inserting bold snippet spans
        int startIndex;
        int endIndex = -1;
        int delta = -1;
        while ((startIndex = snippet.indexOf('{', endIndex)) != -1) {
            endIndex = snippet.indexOf('}', startIndex);

            // Remove braces from both sides
            builder.delete(startIndex - delta, startIndex - delta + 1);
            builder.delete(endIndex - delta - 1, endIndex - delta);

            // Insert bold style
            builder.setSpan(sBoldSpan, startIndex - delta, endIndex - delta - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            delta += 2;
        }

        return builder;
    }

    public static String getLastUsedTrackID(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("last_track_id", null);
    }

    public static void setLastUsedTrackID(Context context, String trackID) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putString("last_track_id", trackID).apply();
    }

    private static final int BRIGHTNESS_THRESHOLD = 130;

    public static boolean isColorDark(int color) {
        return ((30 * Color.red(color) +
                59 * Color.green(color) +
                11 * Color.blue(color)) / 100) <= BRIGHTNESS_THRESHOLD;
    }

    public static long getCurrentTime(final Context context) {
        long t = Calendar.getInstance(CONFERENCE_TIME_ZONE).getTimeInMillis();
        Log.d(TAG, String.format("Sending currentTime: %d", t));
        return t;
    }

    public static Drawable getIconForIntent(final Context context, Intent i) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (infos.size() > 0) {
            return infos.get(0).loadIcon(pm);
        }
        return null;
    }

    public static Class getMapActivityClass(Context context) {
		return null;
    }
}
