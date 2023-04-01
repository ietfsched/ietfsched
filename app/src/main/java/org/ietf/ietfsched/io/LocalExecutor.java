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
import java.util.HashSet;

public class LocalExecutor {
	private static final String TAG = "LocalExecutor";
	private static final boolean debug = false;
    private Resources mRes;
    private ContentResolver mResolver;
	private final String mAuthority = ScheduleContract.CONTENT_AUTHORITY;
	private final HashSet<String> blockRefs = new HashSet<>();


    public LocalExecutor(Resources res, ContentResolver resolver) {
        mRes = res;
        mResolver = resolver;
    }

	public void execute(JSONObject stream) throws Exception {
		Log.d(TAG, "Parsing input page data");
		if (stream != null) {
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
	
		String key = String.format("%s-%s", m.startHour, m.typeSession);
		if (blockRefs.contains(key)) {
			return null;
		}
		else {
			blockRefs.add(key);
		}
	
		String title;
		Long startTime;
		Long endTime;
		String blockType;
		String sessionType;


		startTime = ParserUtils.parseTime(m.startHour);
		endTime = ParserUtils.parseTime(m.endHour);
		String blockId = Blocks.generateBlockId(startTime, endTime);
		title = m.title;
		blockType = ParserUtils.BLOCK_TYPE_UNKNOWN;
		sessionType = m.typeSession.toLowerCase();

		// Based on rough parsing of the agenda elements assign block TYPE.
		if (m.typeSession.contains("Registration")) {
			title = ParserUtils.BLOCK_TITLE_REGISTRATION;
			blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
		}
		else if (sessionType.contains(("session"))) {
			title = m.typeSession.trim().length() == 0 ? m.area : m.typeSession;
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
		}
		else if (m.title.contains("Break")) {
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
		}
		// NOC Helpdesk Hours must show up like office-hours.
		// Otherwise these blocks overwrite the session blocks.
		else if (m.title.contains("NOC")) {
			blockType = ParserUtils.BLOCK_TYPE_NOC_HELPDESK;
		}
		else if (m.title.contains("Office Hours")) {
			blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
		}
		else if (m.title.contains("Plenary")){
			// Plenary actions should get shown, Food at least keeps them showing.
            // Also, there is generally food served at the plenary.
		    blockType = ParserUtils.BLOCK_TYPE_FOOD;
		}
		else if (m.title.contains("Hackathon")){
		    title = ParserUtils.BLOCK_TYPE_HACKATHON;
		    blockType = ParserUtils.BLOCK_TYPE_HACKATHON;
		}
		else if (m.typeSession.contains("None")) {
			title = "...";
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
		}
		else {
		    Log.d(TAG, String.format("Unknown Agenda slot(%s - %s), type: %s, title: %s",
					startTime, endTime, m.typeSession, m.title));
		}

		builder.withValue(Blocks.BLOCK_ID, blockId);
		builder.withValue(Blocks.BLOCK_TITLE, title);
		builder.withValue(Blocks.BLOCK_START, startTime);
		builder.withValue(Blocks.BLOCK_END, endTime);
		builder.withValue(Blocks.BLOCK_TYPE, blockType);
		return builder.build();	
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
	private ArrayList<Meeting> decode(final JSONObject is) throws IOException {
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
