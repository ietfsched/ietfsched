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

import org.ietf.ietfsched.provider.ScheduleContract.Blocks;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

public class ParserUtils {

    public static final String BLOCK_TITLE_BREAKOUT_SESSIONS = "Breakout sessions";
    public static final String BLOCK_TITLE_REGISTRATION = String.format("%n%nR%nE%nG%nI%nS%nT%nR%nA%nT%nI%nO%nN");

    // Block types are used to map a session to the column in the application Schedule View.
    public static final String BLOCK_TYPE_FOOD = "food";
    public static final String BLOCK_TYPE_SESSION = "session";
    public static final String BLOCK_TYPE_OFFICE_HOURS = "officehours";
    public static final String BLOCK_TYPE_NOC_HELPDESK = "nocHelpdesk";
    public static final String BLOCK_TYPE_HACKATHON = "hackathon";
    public static final String BLOCK_TYPE_UNKNOWN = "unknown";

    /** Used to sanitize a string to be {@link Uri} safe. */
    private static final Pattern sSanitizePattern = Pattern.compile("[^a-z0-9-_]");
    private static final Pattern sParenPattern = Pattern.compile("\\(.*?\\)");

    /** Used to split a comma-separated string. */
    private static final Pattern sCommaPattern = Pattern.compile("\\s*,\\s*");

	private static final SimpleDateFormat df;

	static {
		df = new SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.US);
		df.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
	}

    /**
     * Sanitize the given string to be {@link Uri} safe for building
     * {@link ContentProvider} paths.
     */
    public static String sanitizeId(String input) {
        return sanitizeId(input, false);
    }

    /**
     * Sanitize the given string to be {@link Uri} safe for building
     * {@link ContentProvider} paths.
     */
    private static String sanitizeId(String input, boolean stripParen) {
        if (input == null) return null;
        if (stripParen) {
            // Strip out all parenthetical statements when requested.
            input = sParenPattern.matcher(input).replaceAll("");
        }
        return sSanitizePattern.matcher(input.toLowerCase()).replaceAll("");
    }

    public static Long parseTime(String time) {
		try {
		return df.parse(time).getTime();
		}
		catch (Exception e) {
			e.printStackTrace();
			return 0L;
		}
	}
}
