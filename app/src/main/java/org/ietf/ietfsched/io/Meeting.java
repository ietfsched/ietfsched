/*
 * Copyright 2011 Isabelle Dalmasso.
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

package org.ietf.ietfsched.io;


import java.util.*;
import java.text.*;
import android.util.Log;

import org.ietf.ietfsched.util.ParserUtils;
import org.ietf.ietfsched.util.UIUtils;


class Meeting {
	private static final boolean debug = false;
	private static final String TAG = "Meeting";
	private final static SimpleDateFormat previousFormat = new SimpleDateFormat("yyyy-MM-dd HHmm"); // 2011-07-23 0900
	// Hack: timezone format (Z) = +0800 where the ietfsched application expects +08:00.
	private final static SimpleDateFormat afterFormat = ParserUtils.df;

	private String day; // Saturday, March, 12
	String startHour; //2010-05-19 10:45:00
	String endHour; // 2010-05-19 11:45:00
	String title;
	String hrefDetail;
	String location = "N/A"; // room
	String group; // APP
	String area; // apparea
	String typeSession; // Morning Session I
	String key; // unique identifier
	String[] slides; // The list of slides urls.

	static {
		previousFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
		afterFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
	}

	// Handle parsing each line of the agenda.
	Meeting(String lineCsv) throws Exception {
		String[] elements = lineCsv.split(",");
		day = elements[0].replaceAll("\"", "");
		startHour = convert(day, elements[1].replaceAll("\"", ""));
		endHour = convert(day, elements[2].replaceAll("\"", ""));
		typeSession = elements[3].replaceAll("\"", "");
		String tLocation = elements[4].trim().replaceAll("\"", "");
		if (tLocation.length() > 0) {
			location = tLocation;
		}
		area = elements[5].replaceAll("\"", "");
		group = elements[6].replaceAll("\"", "");
		title = elements[8].replaceAll("\"", "");
		key = elements[9].replaceAll("\"", "");
		hrefDetail = elements[10].replaceAll("\"", "");
		if (elements[11].length() > 0) {
		  slides = elements[11].replaceAll("\"",
				  "").split("\\|");
		}
		if (debug) Log.d(TAG, "Agenda URL: " + hrefDetail);
		if (debug) Log.d(TAG, "Slides URLs count: " + slides.length);
		if (debug) {
			for (int i = 0; i < slides.length; i++) {
				Log.d(TAG, "Slides URL[" + i + "]: " + slides[i]);
			}
		}
	}

	private static String convert(String date, String hour) throws Exception {
		Date d;
		if (debug) Log.d(TAG, String.format("Date: %s Hour: %s", date, hour));
		d = previousFormat.parse(String.format("%s %s", date, hour));
		if (debug) Log.d(TAG, String.format("Converted: %s", afterFormat.format(d)));
		return afterFormat.format(d);
	}
}
