/*
 * Copyright 2001 Isabelle Dalmasso.
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

package org.ietf.ietfsched.io;

import org.ietf.ietfsched.util.ParserUtils;
import org.ietf.ietfsched.util.UIUtils;
import org.ietf.ietfsched.provider.ScheduleContract;
import org.ietf.ietfsched.provider.ScheduleContract.Blocks;
import org.ietf.ietfsched.provider.ScheduleContract.Rooms;
import org.ietf.ietfsched.provider.ScheduleContract.Sessions;
import org.ietf.ietfsched.provider.ScheduleContract.Tracks;
import org.ietf.ietfsched.provider.ScheduleDatabase.SessionsTracks;
import org.ietf.ietfsched.util.Lists;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.content.res.Resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class LocalExecutor {
	private static final String TAG = "LocalExecutor";
	private static final boolean debug = true;
    private Resources mRes;
    private ContentResolver mResolver;
	private final String mAuthority = ScheduleContract.CONTENT_AUTHORITY;
	private final HashSet<String> blockRefs = new HashSet<>();
	
	// Map of (day -> sorted list of session start times) for assigning session numbers (I, II, III)
	private final HashMap<String, ArrayList<Long>> mDaySessionTimes = new HashMap<>();


    public LocalExecutor(Resources res, ContentResolver resolver) {
        mRes = res;
        mResolver = resolver;
    }

	public void execute(JSONObject stream, int meetingNumber) throws Exception {
		Log.d(TAG, "Parsing input page data for meeting " + meetingNumber);
		if (stream != null) {
			// Set the meeting number for Meeting objects to use when building URLs
			Meeting.setMeetingNumber(meetingNumber);
			
			ArrayList<Meeting> meetings = decode(stream);
			if (meetings.size() == 0) {
				throw new IOException("Cannot decode inputStream. Not an agenda ? ");
			}
			executeBuild(meetings);
		}
		else {
			throw new IOException("Invalid inputStream."); 
		}
	}

	private void executeBuild(ArrayList<Meeting> meetings) {
		final long versionBuild = System.currentTimeMillis();
		try {
			ArrayList<ContentProviderOperation> batch = transform(meetings, versionBuild);
			Log.d(TAG, "Build database ...");
			mResolver.applyBatch(mAuthority, batch);
			Log.d(TAG, "Build database done");
			ArrayList<ContentProviderOperation> batchClean = purge(versionBuild);
			Log.d(TAG, "Clean database ");
			ContentProviderResult[] results = mResolver.applyBatch(mAuthority, batchClean);
			if (debug) {
				for (ContentProviderResult r : results) {
					Log.d(TAG, "Result clean : " + r);
				}	
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		blockRefs.clear();
	}

	private ArrayList<ContentProviderOperation> transform(ArrayList<Meeting> meetings, long versionBuild) throws Exception {
		// First pass: build map of session start times per day for numbering (I, II, III)
		buildSessionTimesMap(meetings);
		
		final ArrayList<ContentProviderOperation> batch = Lists.newArrayList();
		for (int i = 0; i < meetings.size(); i++) {
			Meeting m = meetings.get(i);
			ContentProviderOperation cp = createBlock(m, versionBuild); 
			if (cp != null) {
				batch.add(cp);
			}
			cp = createTrack(m, versionBuild);
			if (cp != null) {
				batch.add(cp);
			}
			if (! (m.location.length() == 0)) {
				cp = createRoom(m);
				if (cp != null) {
					batch.add(cp);
				}
			}		
			cp = createSession(m, versionBuild);
			if (cp != null) {
				batch.add(cp);
			}
			cp = createSessionTrack(m);
			if (cp != null) {
				batch.add(cp);
			}
		}
		return batch;
	}
	
	private ContentProviderOperation createBlock(Meeting m, long versionBuild) throws Exception {
		final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Blocks.CONTENT_URI);
        builder.withValue(Blocks.UPDATED, versionBuild);
	
		String title;
		Long startTime;
		Long endTime;
		String blockType;
		String sessionType;

		long actualStartTime = ParserUtils.parseTime(m.startHour);
		long actualEndTime = ParserUtils.parseTime(m.endHour);
		
		// For now, use actual times for block_id calculation
		// We'll adjust this for SESSION blocks below
		startTime = actualStartTime;
		endTime = actualEndTime;
		
		String blockId = Blocks.generateBlockId(startTime, endTime);
		title = m.title;
		blockType = ParserUtils.BLOCK_TYPE_UNKNOWN;
		sessionType = m.typeSession.toLowerCase();

		if (debug) Log.d(TAG, "Creating block: title='" + m.title + "' typeSession='" + m.typeSession + "' group='" + m.group + "'");

		// Based on rough parsing of the agenda elements assign block TYPE.
		// Check specific types FIRST before the generic "session" check
		
		// Check title-based patterns first (these are most specific)
		if (m.title.contains("Break") || m.title.contains("break")) {
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
			title = m.title;
		}
		else if (m.title.contains("Plenary")){
			// Plenary actions should get shown, Food at least keeps them showing.
			// Also, there is generally food served at the plenary.
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
			title = m.title;
		}
		else if (m.title.contains("Hackathon")){
			title = ParserUtils.BLOCK_TYPE_HACKATHON;
			blockType = ParserUtils.BLOCK_TYPE_HACKATHON;
		}
		else if (m.title.toLowerCase().contains("noc") || 
				 m.title.toLowerCase().contains("helpdesk") || 
				 m.title.toLowerCase().contains("help desk")) {
			// NOC Helpdesk Hours must show up in yellow column.
			blockType = ParserUtils.BLOCK_TYPE_NOC_HELPDESK;
			title = m.title;
		}
		else if (m.title.contains("Office Hours") && isActualOfficeHours(m)) {
			// Only classify as office hours if it's IETF/IAB/IESG/ISE office hours,
			// not a WG session that happens to mention office hours
			blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
			title = m.title;
		}
		// Check typeSession patterns
		else if (m.typeSession.contains("Registration") || m.title.contains("Registration")) {
			title = ParserUtils.BLOCK_TITLE_REGISTRATION;
			blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
		}
		else if (m.typeSession.contains("None")) {
			title = "...";
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
		}
		// Default: if typeSession contains "session", it's a regular session block
		else if (sessionType.contains("session")) {
			// Only use numbered session title (I, II, III) if this time is in the session times map
			// Otherwise use the actual meeting title (for special events, evening sessions, etc.)
			if (isInSessionTimesMap(startTime)) {
				// Regular numbered session → Red
				title = generateBlockTitle(startTime);
				blockType = ParserUtils.BLOCK_TYPE_SESSION;
			} else {
				// Special event → assign appropriate color based on type
				title = m.title;
				String titleLower = m.title.toLowerCase();
				
				if (titleLower.contains("reception") || titleLower.contains("social")) {
					// Receptions/social events → Blue (FOOD type)
					blockType = ParserUtils.BLOCK_TYPE_FOOD;
				} else if (titleLower.contains("education") || 
						   titleLower.contains("outreach") ||
						   titleLower.contains("tutorial") ||
						   titleLower.contains("newcomer")) {
					// Administrative/educational events → Green (OFFICE_HOURS type)
					blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
				} else {
					// Other special sessions (evening WG sessions, etc.) → keep as Red
					blockType = ParserUtils.BLOCK_TYPE_SESSION;
				}
			}
		}
		else {
			Log.d(TAG, String.format("Unknown Agenda slot(%s - %s), type: %s, title: %s",
					startTime, endTime, m.typeSession, m.title));
			// Default to session if we don't know what it is
			title = m.typeSession.trim().length() == 0 ? m.title : m.typeSession;
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
		}

		// Create one block per unique start time + type
		// Don't consolidate - session times vary by meeting
		String key = String.format("%s-%s", m.startHour, m.typeSession);
		if (blockRefs.contains(key)) {
			return null;
		}
		blockRefs.add(key);

		builder.withValue(Blocks.BLOCK_ID, blockId);
		builder.withValue(Blocks.BLOCK_TITLE, title);
		builder.withValue(Blocks.BLOCK_START, startTime);
		builder.withValue(Blocks.BLOCK_END, endTime);
		builder.withValue(Blocks.BLOCK_TYPE, blockType);
		return builder.build();	
	}
	
	/**
	 * Check if this is an actual staff office hours entry vs a WG session.
	 * 
	 * Called only when title already contains "Office Hours". This function
	 * verifies it's from staff groups (iesg, ise, ietf-trust) or is a 
	 * Liaison/Coordinator office hours.
	 * 
	 * Returns FALSE for regular IAB/IESG meetings like "IAB Open Meeting"
	 * which should be in the session block, not office hours block.
	 */
	private boolean isActualOfficeHours(Meeting m) {
		String group = m.group.toLowerCase();
		
		// Staff group office hours (but NOT regular IAB meetings)
		// IAB meetings like "IAB Open Meeting" should be sessions, not office hours
		if (group.equals("iesg") || group.equals("ise") || group.equals("ietf-trust")) {
			return true;
		}
		
		// Liaison/Coordinator office hours (usually from IAB)
		if (m.title.contains("Coordinator") || m.title.contains("Liaison")) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * Build a map of session start times per day.
	 * This is used to assign session numbers (I, II, III) chronologically.
	 */
	private void buildSessionTimesMap(ArrayList<Meeting> meetings) {
		mDaySessionTimes.clear();
		
		for (Meeting m : meetings) {
			// Only process session-type meetings (regular WG sessions)
			// Exclude special events, breaks, registration, etc.
			String sessionType = m.typeSession.toLowerCase();
			String titleLower = m.title.toLowerCase();
			
			if (!sessionType.contains("session")) {
				continue;
			}
			
			// Exclude non-session blocks
			if (titleLower.contains("break") || 
				titleLower.contains("breakfast") ||
				titleLower.contains("registration") ||
				titleLower.contains("office hours") ||
				titleLower.contains("plenary") ||
				titleLower.contains("hackathon") ||
				titleLower.contains("reception") ||
				titleLower.contains("social") ||
				titleLower.contains("education") ||
				titleLower.contains("outreach") ||
				titleLower.contains("tutorial") ||
				titleLower.contains("newcomer") ||
				titleLower.contains("noc") ||
				titleLower.contains("helpdesk") ||
				titleLower.contains("help desk")) {
				continue;
			}
			
			long startTime = ParserUtils.parseTime(m.startHour);
			java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
			cal.setTimeInMillis(startTime);
			
			// Get day key (YYYY-DDD format)
			String dayKey = String.format("%04d-%03d", 
				cal.get(java.util.Calendar.YEAR), 
				cal.get(java.util.Calendar.DAY_OF_YEAR));
			
			// Add this start time to the day's list
			if (!mDaySessionTimes.containsKey(dayKey)) {
				mDaySessionTimes.put(dayKey, new ArrayList<Long>());
			}
			ArrayList<Long> times = mDaySessionTimes.get(dayKey);
			if (!times.contains(startTime)) {
				times.add(startTime);
			}
		}
		
		// Sort times for each day
		for (String day : mDaySessionTimes.keySet()) {
			ArrayList<Long> times = mDaySessionTimes.get(day);
			Collections.sort(times);
			
			// Debug: log session times for this day
			if (debug) {
				StringBuilder sb = new StringBuilder();
				sb.append("Day ").append(day).append(" has ").append(times.size()).append(" session times: ");
				for (Long time : times) {
					java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH);
					fmt.setTimeZone(UIUtils.getConferenceTimeZone());
					sb.append(fmt.format(new java.util.Date(time))).append(" ");
				}
				Log.d(TAG, sb.toString());
			}
		}
	}
	
	/**
	 * Check if a given start time is in the session times map.
	 * Returns true if this time should be numbered as a session (I, II, III).
	 */
	private boolean isInSessionTimesMap(long startTimeMillis) {
		java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
		cal.setTimeInMillis(startTimeMillis);
		
		String dayKey = String.format("%04d-%03d", 
			cal.get(java.util.Calendar.YEAR), 
			cal.get(java.util.Calendar.DAY_OF_YEAR));
		
		ArrayList<Long> times = mDaySessionTimes.get(dayKey);
		if (times == null) {
			return false;
		}
		
		return times.contains(startTimeMillis);
	}
	
	/**
	 * Get the session number (I, II, III, etc.) for a given start time.
	 * Returns the Roman numeral based on chronological order within the day.
	 */
	private String getSessionNumber(long startTimeMillis) {
		java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
		cal.setTimeInMillis(startTimeMillis);
		
		String dayKey = String.format("%04d-%03d", 
			cal.get(java.util.Calendar.YEAR), 
			cal.get(java.util.Calendar.DAY_OF_YEAR));
		
		ArrayList<Long> times = mDaySessionTimes.get(dayKey);
		if (times == null) {
			return "I"; // Default fallback
		}
		
		int index = times.indexOf(startTimeMillis);
		if (index == -1) {
			return "I"; // Default fallback
		}
		
		// Convert index to Roman numeral
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		if (index < numerals.length) {
			return numerals[index];
		}
		return String.valueOf(index + 1); // Fallback to Arabic if > X
	}
	
	/**
	 * Generate a descriptive block title matching IETF web agenda format.
	 * Examples: "Monday Session I", "Tuesday Session II", "Wednesday Session III"
	 * 
	 * Session numbers are assigned chronologically within each day.
	 */
	private String generateBlockTitle(long startTimeMillis) {
		java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
		cal.setTimeInMillis(startTimeMillis);
		
		// Get day of week name (Monday, Tuesday, etc.)
		String dayName = new java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH).format(cal.getTime());
		
		// Get session number based on chronological order
		String sessionNumber = getSessionNumber(startTimeMillis);
		
		return dayName + " Session " + sessionNumber;
	}

	private ContentProviderOperation createSession(Meeting m, long versionBuild) throws Exception {
		final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Sessions.CONTENT_URI);
        builder.withValue(Sessions.UPDATED, versionBuild);

        Long startTime;
        Long endTime;
        String title;
        String sessionId;
        String trackId;
		String roomId;
		String pdfs;

		try {
			// Use the times for start/end as presented from the JSON, in UTC.
			startTime = ParserUtils.parseTime(m.startHour);
			endTime = ParserUtils.parseTime(m.endHour);
			title = String.format("%s -%s%s - %s%s",
					m.area,
					(m.area.length() == 0 ? "" : " "),
					m.group,
					(m.group.length() == 0 ? "" : " "),
					m.title);
			roomId = Rooms.generateRoomId(m.location);
			
			sessionId = Sessions.generateSessionId(m.key);
			
			// Use actual times for block_id - no consolidation
			String blockId = Blocks.generateBlockId(startTime, endTime);
		
			builder.withValue(Sessions.SESSION_ID, sessionId);
			builder.withValue(Sessions.SESSION_TITLE, title);
			builder.withValue(Sessions.SESSION_ABSTRACT, null);
			builder.withValue(Sessions.SESSION_URL, m.hrefDetail);
			builder.withValue(Sessions.SESSION_REQUIREMENTS, null);
			builder.withValue(Sessions.SESSION_KEYWORDS, null);
			builder.withValue(Sessions.BLOCK_ID, blockId);
			builder.withValue(Sessions.ROOM_ID, roomId);
			if (m.slides != null ) {
				// TODO(morrowc): Set the session urls and make more than 1 button appear
				//                if there's more than 1 slide url in the set.
				builder.withValue(Sessions.SESSION_PDF_URL, TextUtils.join("::", m.slides));
			} else {
				builder.withValue(Sessions.SESSION_PDF_URL, "::");
			}
			
			final Uri sessionUri = Sessions.buildSessionUri(sessionId);
			final int starred = querySessionStarred(sessionUri, mResolver);
			if (starred != -1) {
				builder.withValue(Sessions.SESSION_STARRED, starred);
			}
			
			return builder.build();
		}
		catch (Exception e) {
			Log.w(TAG, "Error parsing a session involves:[[" + m + "]]");
			e.printStackTrace();
			return null;
		}
	}
	
	private ContentProviderOperation createRoom(Meeting m) throws Exception {
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Rooms.CONTENT_URI);
	
		builder.withValue(Rooms.ROOM_ID, Rooms.generateRoomId(m.location));
		builder.withValue(Rooms.ROOM_NAME, m.location);
		builder.withValue(Rooms.ROOM_FLOOR, " ");
		
		return builder.build();
	}
	
	private ContentProviderOperation createTrack(Meeting m, long versionBuild) throws Exception {
		if (m.group.length() == 0 || m.area.length() == 0) {
			return null;
		}

		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Tracks.CONTENT_URI);
        builder.withValue(Tracks.UPDATED, versionBuild);
		builder.withValue(Tracks.TRACK_ID, Tracks.generateTrackId(m.area + m.group));
		builder.withValue(Tracks.TRACK_NAME, m.area + "-" + m.group);
		builder.withValue(Tracks.TRACK_COLOR, 1);
		builder.withValue(Tracks.TRACK_ABSTRACT, m.area + "-" + m.group);
	
		return builder.build();
	}

	private ContentProviderOperation createSessionTrack(Meeting m) throws Exception {
		if (m.group.length() == 0 || m.area.length() == 0) {
			return null;
		}

	final String sessionId = Sessions.generateSessionId(m.key);
		final Uri sessionsTracksUri = Sessions.buildTracksDirUri(sessionId);
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(sessionsTracksUri);
		builder.withValue(SessionsTracks.SESSION_ID, sessionId);
		builder.withValue(SessionsTracks.TRACK_ID, Tracks.generateTrackId(m.area + m.group));
		
		return builder.build();
	}
	
	/**
	 * Purge the sessions and blocks removed from the agenda. 
	 */
	private ArrayList<ContentProviderOperation> purge(long versionBuild) throws Exception {
		ArrayList<ContentProviderOperation> batchClean = Lists.newArrayList();
		batchClean.add(buildPurge(Sessions.CONTENT_URI, versionBuild));
		batchClean.add(buildPurge(Blocks.CONTENT_URI, versionBuild));
		return batchClean;
	}
	
	private ContentProviderOperation buildPurge(Uri contentURI, long versionBuild) throws Exception {
		ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(contentURI);
		String where = Sessions.UPDATED + " <> ?";
		String args[] = new String[] {"" + versionBuild };
		builder.withSelection(where, args);
		return builder.build();
	}


	// decode, decodes the JSON content from the origin (datatracker).
	private ArrayList<Meeting> decode(final JSONObject jsAgenda) throws IOException {
		final ArrayList<Meeting> meetings = new ArrayList<>();
		if (jsAgenda != null) {
			try {
			JSONArray jsAgendaArray = jsAgenda.optJSONArray(jsAgenda.keys().next());
				for (int i = 0; i < jsAgendaArray.length(); i++) {
					JSONObject mJSON = jsAgendaArray.getJSONObject(i);
					try {
						Meeting m = new Meeting(mJSON);
						meetings.add(m);
					} catch (UnScheduledMeetingException e) {
						Log.d(TAG, String.format("Unscheduled meeting: %s", e.getMessage()));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (final JSONException e) {
				Log.d(TAG, String.format("Failed to parse JSONObject: %s", jsAgenda));
				return null;
			}
		}
		return meetings;
	}
            
	private static int querySessionStarred(Uri uri, ContentResolver resolver) {
        final String[] projection = { Sessions.SESSION_STARRED };
		try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
			assert cursor != null;
			if (cursor.moveToFirst()) {
				return cursor.getInt(0);
			} else {
				return -1;
			}
		}
	}
}
