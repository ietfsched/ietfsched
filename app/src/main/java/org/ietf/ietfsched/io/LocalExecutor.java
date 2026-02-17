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
import java.util.Locale;

public class LocalExecutor {
	private static final String TAG = "LocalExecutor";
	private static final boolean debug = false;
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

	/**
	 * Helper method to check if a string contains any of the given keywords.
	 */
	private static boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	public void execute(JSONObject stream, int meetingNumber) throws Exception {
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
			mResolver.applyBatch(mAuthority, batch);
			ArrayList<ContentProviderOperation> batchClean = purge(versionBuild);
			mResolver.applyBatch(mAuthority, batchClean);
			
			// Explicitly notify observers that blocks have changed (for schedule rebuild)
			mResolver.notifyChange(ScheduleContract.Blocks.CONTENT_URI, null);
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
		sessionType = m.typeSession.toLowerCase(Locale.ROOT);
		String titleLower = m.title.toLowerCase(Locale.ROOT);

		// Based on rough parsing of the agenda elements assign block TYPE.
		// Check specific types FIRST before the generic "session" check
		
		// Check title-based patterns first (these are most specific)
		if (titleLower.contains("break")) {
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
			title = m.title;
		}
		else if (titleLower.contains("plenary")){
			// Plenary actions should get shown, Food at least keeps them showing.
			// Also, there is generally food served at the plenary.
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
			title = m.title;
		}
		else if (titleLower.contains("hackathon")){
			title = m.title;
			// Hackathon Results Presentations go in green column to avoid overlap with main Hackathon
			if (titleLower.contains("results") || titleLower.contains("presentations")) {
				blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
			} else {
				blockType = ParserUtils.BLOCK_TYPE_HACKATHON;
			}
		}
		else if (containsAny(titleLower, "noc", "helpdesk", "help desk")) {
			// NOC Helpdesk Hours must show up in yellow column.
			blockType = ParserUtils.BLOCK_TYPE_NOC_HELPDESK;
			title = m.title;
		}
		else if (titleLower.contains("office hours")) {
			// Only classify as office hours if from staff groups (iesg, ise, ietf-trust) or Liaison/Coordinator
			String groupLower = m.group.toLowerCase(Locale.ROOT);
			boolean isStaffGroup = groupLower.equals("iesg") || groupLower.equals("ise") || 
				groupLower.equals("ietf-trust");
			boolean isStaffOfficeHours = isStaffGroup || containsAny(titleLower, "coordinator", "liaison");
			
			if (isStaffOfficeHours) {
				// Map AD office hours to NOC column (yellow) to reduce overlap with other green blocks
				if (titleLower.contains("ad office hours")) {
					blockType = ParserUtils.BLOCK_TYPE_NOC_HELPDESK;
				} else {
					blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
				}
				title = m.title;
			}
		}
		// Check typeSession patterns
		else if (m.typeSession.contains("Registration") || titleLower.contains("registration")) {
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
			
			// IEPG goes to yellow column to avoid overlap with New Participant Program (check first!)
			if (titleLower.contains("iepg")) {
				blockType = ParserUtils.BLOCK_TYPE_NOC_HELPDESK;
			}
			// Social/food events → Blue
			else if (containsAny(titleLower, "reception", "social", "dinner", "lunch", 
					"happy hour", "game night", "networking")) {
				blockType = ParserUtils.BLOCK_TYPE_FOOD;
			} 
			// Administrative/educational/special programs → Green
			else if (containsAny(titleLower, "education", "outreach", "tutorial", "newcomer", 
					"new participant", "tools", "chairs", "forum", "program", "series", "sprint", 
					"hotrfc", "lightning talk", "office hours")) {
				blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
			} 
			// Other special sessions (evening WG sessions, side meetings) → keep as Red
			else {
				blockType = ParserUtils.BLOCK_TYPE_SESSION;
			}
			}
		}
		else {
			// Default to session if we don't know what it is
			title = m.typeSession.trim().length() == 0 ? m.title : m.typeSession;
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
		}

		// Create one block per unique blockId (which includes start+end+title)
		// Multiple events can start at the same time, so we need to use blockId instead of just startTime
		if (debug) Log.d(TAG, "Block blockId: " + blockId + " for title: " + title);
		if (blockRefs.contains(blockId)) {
			if (debug) Log.d(TAG, "DUPLICATE BLOCK FILTERED: " + blockId + " for title: " + title);
			return null;
		}
		blockRefs.add(blockId);
		if (debug) Log.d(TAG, "BLOCK ADDED: " + blockId + " for title: " + title);

		builder.withValue(Blocks.BLOCK_ID, blockId);
		builder.withValue(Blocks.BLOCK_TITLE, title);
		builder.withValue(Blocks.BLOCK_START, startTime);
		builder.withValue(Blocks.BLOCK_END, endTime);
		builder.withValue(Blocks.BLOCK_TYPE, blockType);
		return builder.build();	
	}
	
	/**
	 * Build a map of session start times per day.
	 * This is used to assign session numbers (I, II, III) chronologically.
	 * 
	 * Uses a heuristic: time slots with 2+ parallel session meetings are numbered
	 * (e.g. "Fri Session I"). Regular session blocks have many parallel WG meetings;
	 * special events typically have 1. Using 2+ avoids showing one arbitrary session
	 * name (e.g. "SRv6 Operations") when a slot has 2-4 sessions.
	 */
	private void buildSessionTimesMap(ArrayList<Meeting> meetings) {
		mDaySessionTimes.clear();
		
		// Count parallel meetings per start time, excluding special events
		HashMap<Long, Integer> parallelCounts = new HashMap<>();
		final int MIN_PARALLEL_MEETINGS = 2;
		
		for (Meeting m : meetings) {
			if (!m.typeSession.toLowerCase(Locale.ROOT).contains("session")) {
				continue;
			}
			
			String titleLower = m.title.toLowerCase(Locale.ROOT);
			// Skip special events - they shouldn't be numbered as sessions
			if (containsAny(titleLower, "break", "breakfast", "registration", "office hours", 
					"plenary", "hackathon", "reception", "social", "education", "outreach", 
					"tutorial", "newcomer", "noc", "helpdesk", "help desk",
					"host speaker", "systers")) {
				continue;
			}
			
			long startTime = ParserUtils.parseTime(m.startHour);
			int count = parallelCounts.getOrDefault(startTime, 0) + 1;
			parallelCounts.put(startTime, count);
			
			// Once we know this time slot has enough meetings, add it to the map
			if (count == MIN_PARALLEL_MEETINGS) {
				String dayKey = getDayKey(startTime);
				if (!mDaySessionTimes.containsKey(dayKey)) {
					mDaySessionTimes.put(dayKey, new ArrayList<Long>());
				}
				mDaySessionTimes.get(dayKey).add(startTime);
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
	 * Get the day key (YYYY-DDD format) for a given timestamp.
	 */
	private String getDayKey(long timeMillis) {
		java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
		cal.setTimeInMillis(timeMillis);
		return String.format(Locale.ROOT, "%04d-%03d", 
			cal.get(java.util.Calendar.YEAR), 
			cal.get(java.util.Calendar.DAY_OF_YEAR));
	}
	
	/**
	 * Check if a given start time is in the session times map.
	 * Returns true if this time should be numbered as a session (I, II, III).
	 */
	private boolean isInSessionTimesMap(long startTimeMillis) {
		ArrayList<Long> times = mDaySessionTimes.get(getDayKey(startTimeMillis));
		return times != null && times.contains(startTimeMillis);
	}
	
	/**
	 * Generate a descriptive block title matching IETF web agenda format.
	 * Examples: "Monday Session I", "Tuesday Session II", "Wednesday Session III"
	 * 
	 * Session numbers are assigned chronologically within each day.
	 */
	private String generateBlockTitle(long startTimeMillis) {
		// Use Calendar with conference timezone to get the correct day name
		java.util.Calendar cal = java.util.Calendar.getInstance(UIUtils.getConferenceTimeZone());
		cal.setTimeInMillis(startTimeMillis);
		
		// Get day name directly from Calendar (already in correct timezone)
		String[] dayNames = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
		int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
		String dayName = dayNames[dayOfWeek];
		
		// Get session number based on chronological order within the day
		ArrayList<Long> times = mDaySessionTimes.get(getDayKey(startTimeMillis));
		if (times == null) {
			return dayName + " Session I";
		}
		
		int index = times.indexOf(startTimeMillis);
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		String number = (index >= 0 && index < numerals.length) ? numerals[index] : String.valueOf(index + 1);
		
		return dayName + " Session " + number;
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
			title = String.format(Locale.ROOT, "%s -%s%s - %s%s",
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
			// Store multiple slide URLs separated by "::"
			builder.withValue(Sessions.SESSION_PDF_URL, TextUtils.join("::", m.slides));
		} else {
			builder.withValue(Sessions.SESSION_PDF_URL, "::");
		}
		if (m.drafts != null && m.drafts.length > 0) {
			// Store multiple draft entries separated by "::"
			builder.withValue(Sessions.SESSION_DRAFTS_URL, TextUtils.join("::", m.drafts));
		} else {
			builder.withValue(Sessions.SESSION_DRAFTS_URL, null);
		}
		if (m.sessionResUri != null && !m.sessionResUri.isEmpty()) {
			builder.withValue(Sessions.SESSION_RES_URI, m.sessionResUri);
		} else {
			builder.withValue(Sessions.SESSION_RES_URI, null);
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
						// Skip unscheduled meetings
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (final JSONException e) {
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
