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


import java.net.URL;
import java.util.*;
import java.text.*;
import java.time.*;
import android.util.Log;

import org.ietf.ietfsched.service.SyncService;
import org.ietf.ietfsched.util.ParserUtils;
import org.ietf.ietfsched.util.UIUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


// A simple exception to be used when Meeting() creation expectations are a failure.
class UnScheduledMeetingException extends Exception {
	public UnScheduledMeetingException(String errorMessage) {
		super(errorMessage);
	}
}

class Meeting {
	private static final boolean debug = true;
	private static final String TAG = "Meeting";
	// private final static SimpleDateFormat previousFormat = new SimpleDateFormat("yyyy-MM-dd HHmm"); // 2011-07-23 0900
	//                                                        JSON time - Start - "2023-03-27T00:30:00Z
	//                                                                   "start": "2023-11-06T14:30:00Z",
	private final static SimpleDateFormat jsonDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	// Hack: timezone format (Z) = +0800 where the ietfsched application expects +08:00.
	private final static SimpleDateFormat afterFormat = ParserUtils.df;

	// Meeting number is set dynamically by LocalExecutor before parsing
	private static int sMeetingNumber = 0;

	String startHour; //2010-05-19 10:45:00
	String endHour; // 2010-05-19 11:45:00
	String title;
	String hrefDetail; // agenda link
	String location = "N/A"; // room
	String group = "Unknown"; // APP
	String area = "Unknown"; // apparea
	String typeSession; // Morning Session I
	String key; // unique identifier
	String[] slides; // The list of slides urls.

	/**
	 * Sets the current meeting number. Must be called before creating Meeting objects.
	 */
	static void setMeetingNumber(int meetingNumber) {
		sMeetingNumber = meetingNumber;
	}

	static {
		jsonDate.setTimeZone((UIUtils.AGENDA_TIME_ZONE));
		// Note: afterFormat is ParserUtils.df, which gets its timezone set by
		// ParserUtils static initializer and updated by ParserUtils.updateTimezone()
	}

	// Handle parsing each line of the agenda.
	Meeting(JSONObject mJSON) throws UnScheduledMeetingException, Exception {
		try {
			title = mJSON.getString("name");
		} catch (JSONException e) {
		    throw new UnScheduledMeetingException("Missing title for event");
		}
		// Validate that the agenda item has a valid status.
		// For past meetings, accept sessions even without explicit status since the
		// meeting has already happened and data quality may vary.
		String status = "";
		try {
			status = mJSON.getString("status");
		} catch (JSONException e) {
			// No status field - accept it (common for historical/past meetings)
			status = "nostatus";
			if (debug) Log.d(TAG, "Session without status field, accepting: " + mJSON.optString("name"));
		}
		
		// Accept: sched, schedw (scheduled), and sessions without status
		// Reject: canceled, resched, deleted  
		if (status.equals("canceled") || status.equals("resched") || status.equals("deleted")) {
			throw new UnScheduledMeetingException(
					String.format(
							"Unscheduled meeting(%s) status: %s",
							mJSON.getString("name"),
							status));
		}
		// Gather all of the elements for a Meeting().
		try {
			Date jDay = jsonDate.parse(mJSON.getString("start"));

			startHour = afterFormat.format(jDay);
			Log.d(TAG, "jSON Date: " + jDay + " startHour: " + startHour);
			// Build an endDate by using localTime, and Duration (from localtime start 00:00 to duration)
			String[] durSplit = mJSON.getString("duration").split(":");
			Integer[] durSplitInt = new Integer[durSplit.length];
			for (int i = 0; i < durSplit.length; i++) {
				durSplitInt[i] = Integer.parseInt(durSplit[i]);
			}
			// Parse the json duration represented as hour:min:sec to a time, 02:00:00 == 2am, effectively.
			LocalTime lt = LocalTime.parse(String.format("%02d:%02d:%02d", (Object[]) durSplitInt));
			// Get a duration from between '00:00:00 today' and the previous.
			Duration d = Duration.between(LocalTime.MIN, lt);
			// Add the duration millis to the start date.
			Instant tEndHour = jDay.toInstant().plusMillis(d.toMillis());
			endHour = afterFormat.format(Date.from(tEndHour));
			if (debug) {
				Log.d(TAG, String.format("Start/Stop time for %s: %s / %s", title, startHour, endHour));
			}

			// Validate that 'objtype' == 'session', else throw exception.
			typeSession = mJSON.getString("objtype");
			if (!typeSession.equals("session")) {
			  throw new UnScheduledMeetingException(String.format("Not a session: %s", title));
			}
			location = mJSON.getString("location");
			key = String.format("%d", mJSON.getInt("session_id"));
			hrefDetail = "";
			try {
				hrefDetail = mJSON.getString("agenda");
			} catch (JSONException e) {
				Log.d(TAG, "Failed to get an agenda / hrefDetail for " + title);
			}
		} catch (JSONException e) {
			throw new UnScheduledMeetingException(
					String.format("Event(%s) is missing JSON element: %s", title, e.toString()));
		}
		// Parse the group sub element from the agenda, there are instances of meeting
		// where parts of group are unset: IEPG has no parent, for instance.
		JSONObject areaGroup;
		try {
			areaGroup = mJSON.getJSONObject("group");
		} catch (JSONException e) {
			throw new UnScheduledMeetingException(
					String.format("Event(%s) is missing JSON element: %s", title, e.toString()));
		}
		// Do not throw an exception for missing parent/acronym.
		try {
			area = areaGroup.getString("parent");
			group = areaGroup.getString("acronym");
		} catch (JSONException e) {
			if (debug) {
				Log.d(TAG, String.format("Meeting %s is missing area or group.", title));
			}
		}
		// Handle an unknown group/area a bit more gracefully.
		if (group == "Unknown") {
			group = title;
		}

		// Extract the presentation urls and titles, if there are any.
		try {
			JSONArray pArray =  (JSONArray) mJSON.get("presentations");
			if (pArray == null) throw new UnScheduledMeetingException("No presentations");
			slides = new String[pArray.length()];
			
			// DEBUG: Log the entire presentation object to understand structure
			if (pArray.length() > 0) {
				Log.d(TAG, "First presentation object: " + pArray.getJSONObject(0).toString());
			}
			
			// The presentations array contains objects with "url", "name", and "text" (title) fields
			// Store as: "title|||url" so we can display the actual presentation title
			for (int i = 0; i < pArray.length(); i++ ){
				JSONObject presentation = pArray.getJSONObject(i);
				
				// Get the presentation title (try "text" first, fallback to "name")
				String presentationTitle = presentation.optString("text", "");
				if (presentationTitle.isEmpty()) {
					presentationTitle = presentation.optString("name", "Presentation " + (i+1));
				}
				
				// Get the URL (preferred from API, fallback to construct)
				String url = presentation.optString("url", null);
				if (url == null || url.isEmpty()) {
					// Fallback: construct URL from name
					String name = presentation.getString("name");
					String baseUrl = "https://datatracker.ietf.org/meeting/" + sMeetingNumber + "/";
					url = baseUrl + "materials/" + name;
					Log.w(TAG, "No URL in presentation, constructed: " + url);
				}
				
				// Store as "title|||url" (using ||| as separator since :: separates multiple presentations)
				slides[i] = presentationTitle + "|||" + url;
				if (debug) Log.d(TAG, "Presentation: " + presentationTitle + " -> " + url);
			}
		} catch (JSONException e) {
			if (debug) Log.d(TAG, String.format("NoPresentations for %s: %s", title, e.toString()));
		}
		if (debug) Log.d(TAG, "Agenda URL: " + hrefDetail);
		if (debug && slides != null) {
			Log.d(TAG, "Slides URLs count: " + slides.length);
			for (int i = 0; i < slides.length; i++) {
				Log.d(TAG, "Slides URL[" + i + "]: " + slides[i]);
			}
		}
	}
}
