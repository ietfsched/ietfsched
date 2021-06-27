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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.widget.TextView;

import java.util.List;
import java.util.TimeZone;

/**
 * An assortment of UI helpers.
 */
public class UIUtils {
    /**
     * Time zone to use when formatting all session times. To always use the
     * phone local time, use {@link TimeZone#getDefault()}.
     *   https://garygregory.wordpress.com/2013/06/18/what-are-the-java-timezone-ids/
     */
    // BKK +7
    // UTC - ietf109
    // SFO -7 - ietf111
    public static final TimeZone CONFERENCE_TIME_ZONE = TimeZone.getTimeZone("PST");

    public static final Long CONFERENCE_START_MILLIS = ParserUtils.parseTime(
            "2020-07-18T07:07:00.000");
    public static final Long CONFERENCE_END_MILLIS = ParserUtils.parseTime(
            "2020-07-31T07:17:00.000");

    /** Flags used with {@link DateUtils#formatDateRange}. */
    private static final int TIME_FLAGS = DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;

    /** {@link StringBuilder} used for formatting time block. */
    private static StringBuilder sBuilder = new StringBuilder(50);

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    public static String formatSessionSubtitle(long blockStart, long blockEnd,
            String roomName, Context context) {
        TimeZone.setDefault(CONFERENCE_TIME_ZONE);

        // NOTE: There is an efficient version of formatDateRange in Eclair and
        // beyond that allows you to recycle a StringBuilder.
        final CharSequence timeString = DateUtils.formatDateRange(context,
                blockStart, blockEnd, TIME_FLAGS);
			
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

    public static void setSessionTitleColor(long blockStart, long blockEnd, TextView title,
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

    public static boolean isHoneycombTablet(Context context) {
        // Can use static final constants like HONEYCOMB, declared in later versions
        // of the OS since they are inlined at compile time. This is guaranteed behavior.
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public static long getCurrentTime(final Context context) {
        return System.currentTimeMillis();
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
