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
	private final static SimpleDateFormat jsonDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	// Hack: timezone format (Z) = +0800 where the ietfsched application expects +08:00.
	private final static SimpleDateFormat afterFormat = ParserUtils.df;

	String startHour; //2010-05-19 10:45:00
	String endHour; // 2010-05-19 11:45:00
	String title;
	String hrefDetail; // agenda link
	String location = "N/A"; // room
	String group; // APP
	String area; // apparea
	String typeSession; // Morning Session I
	String key; // unique identifier
	String[] slides; // The list of slides urls.

	static {
		// previousFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
		jsonDate.setTimeZone((UIUtils.AGENDA_TIME_ZONE));
		afterFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
	}

	// Handle parsing each line of the agenda.
	Meeting(JSONObject mJSON) throws UnScheduledMeetingException, Exception {
		try {
			title = mJSON.getString("name");
		} catch (JSONException e) {
		    throw new UnScheduledMeetingException("Missing title for event");
		}
		// Validate that the agenda item is a 'status' == 'sched'.
		String status = "";
		try {
			status = mJSON.getString("status");
		} catch (JSONException e) {
		  throw new UnScheduledMeetingException("Non Status event");
		}
		// To permit use before the final agenda is published, permit 'schedw' status.
		if (!status.equals("sched") && !status.equals("schedw")) {
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
			JSONObject areaGroup = mJSON.getJSONObject("group");
			area = areaGroup.getString("parent");
			group = areaGroup.getString("acronym");
			key = String.format("%d", mJSON.getInt("session_id"));
			hrefDetail = mJSON.getString("agenda");
		} catch (JSONException e) {
		  throw new UnScheduledMeetingException(
				  String.format("Event(%s) is missing JSON element: %s",title, e.toString()));
		}
		// Extract the presentation urls, if there are any.
		try {
			JSONArray pArray =  (JSONArray) mJSON.get("presentations");
			if (pArray == null) throw new UnScheduledMeetingException("No presentations");
			slides = new String[pArray.length()];
			// Meeting BASE_URL - SyncService.BASE_URL - is:
			//   https://datatracker.ietf.org/meeting/116
			// Meeting materials are urls like:
			//   https://datatracker.ietf.org/meeting/116/materials/slides-116-mpls-clarify-bootstrapping-bfd-over-mpls-lsp
			// Agenda url:
			//   https://datatracker.ietf.org/meeting/116/materials/agenda-116-mpls-00
			// Presentation name == file-name in URL: slides-116-mpls-clarify-bootstrapping-bfd-over-mpls-lsp
			if (debug) Log.d(TAG, String.format("PRESENTATION LEN: %d", pArray.length()));
			for (int i = 0; i < pArray.length(); i++ ){
                String tURL = SyncService.BASE_URL + "materials/" +
						pArray.getJSONObject(i).getString("name");
				slides[i] = tURL;
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
