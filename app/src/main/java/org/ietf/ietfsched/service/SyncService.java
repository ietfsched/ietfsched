/*
 * Copyright 2011 Google Inc.
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

package org.ietf.ietfsched.service;

import org.ietf.ietfsched.io.LocalExecutor;
import org.ietf.ietfsched.io.MeetingDetector;
import org.ietf.ietfsched.io.MeetingMetadata;
import org.ietf.ietfsched.io.RemoteExecutor;
import org.ietf.ietfsched.provider.ScheduleProvider;
import org.ietf.ietfsched.util.MeetingPreferences;
import org.ietf.ietfsched.util.ParserUtils;
import org.ietf.ietfsched.util.UIUtils;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.TimeZone;

/**
 * Background {@link Service} that synchronizes data living in
 * {@link ScheduleProvider}. Reads data from both local {@link Resources} and
 * from remote sources, such as a spreadsheet.
 */
public class SyncService extends IntentService {
    private static final String TAG = "SyncService";
    private static final boolean debug = false;

    public static final String EXTRA_STATUS_RECEIVER = "org.ietf.ietfsched.extra.STATUS_RECEIVER";

    public static final int STATUS_RUNNING = 0x1;
    public static final int STATUS_ERROR = 0x2;
    public static final int STATUS_FINISHED = 0x3;

	/** Root worksheet feed for online data source */
    // Meeting number is now detected dynamically - no longer hardcoded
	private static final String BASE_FILE = "agenda.json";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

    private static final String noteWellURL = "https://www.ietf.org/media/documents/note-well.md";
	private static final int VERSION_NONE = 0;
    private static final int VERSION_CURRENT = 47;

    private LocalExecutor mLocalExecutor;
    private RemoteExecutor mRemoteExecutor;

	public static String noteWellString = "";
    public SyncService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final ContentResolver resolver = getContentResolver();
        mLocalExecutor = new LocalExecutor(getResources(), resolver);

        mRemoteExecutor = new RemoteExecutor();
        if (debug) {
			Log.d(TAG, "SyncService OnCreate" + this.hashCode());
		}
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        assert intent != null;
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_STATUS_RECEIVER);
        if (receiver != null) receiver.send(STATUS_RUNNING, Bundle.EMPTY);
        final Context context = this;
        final SharedPreferences prefs = getSharedPreferences(Prefs.IETFSCHED_SYNC, Context.MODE_PRIVATE);
        final int localVersion = prefs.getInt(Prefs.LOCAL_VERSION, VERSION_NONE);
		final String lastEtag = prefs.getString(Prefs.LAST_ETAG, "");

		Log.d(TAG, "found localVersion=" + localVersion + " and VERSION_CURRENT=" + VERSION_CURRENT);
		
		// Detect current IETF meeting dynamically
		MeetingDetector detector = new MeetingDetector(mRemoteExecutor);
		MeetingMetadata meeting = detector.detectCurrentMeeting();
		
		if (meeting == null) {
			Log.e(TAG, "Failed to detect current meeting");
			final Bundle bundle = new Bundle();
			bundle.putString(Intent.EXTRA_TEXT, "Could not determine current IETF meeting.");
			if (receiver != null) {
				receiver.send(STATUS_ERROR, bundle);
			}
			return;
		}
		
		Log.i(TAG, "Using meeting: IETF " + meeting.number + " (" + meeting.city + ")");
		
		// Save meeting info for UI to use
		MeetingPreferences.saveCurrentMeeting(context, meeting);
		
		// Update UIUtils with meeting timezone and dates
		UIUtils.setConferenceTimeZone(meeting.timezone);
		UIUtils.setConferenceDates(meeting.startMillis, meeting.endMillis);
		
		// Update the ParserUtils timezone
		ParserUtils.updateTimezone();
		
		// Build agenda URL from detected meeting
		String aUrl = meeting.agendaUrl;
		String remoteEtag = "";

		// Run a HEAD on the agenda URL, to get it's status, presumably.
		try {
			if (debug) Log.d(TAG, 	"HEAD " + aUrl);
			remoteEtag = mRemoteExecutor.executeHead(aUrl);
			if (debug) Log.d(TAG, 	"HEAD "  + aUrl + " " + remoteEtag);
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	// Get the NoteWell text. It's convenient to get that here instead of in the WellNoteFragment.
	try {
		String txt = mRemoteExecutor.executeGet(noteWellURL);
		if (txt.length() > 0 ) {
			noteWellString = txt;
			// Persist Note Well content across app restarts
			prefs.edit().putString(Prefs.NOTE_WELL_CONTENT, txt).apply();
			Log.d(TAG, String.format(java.util.Locale.ROOT, "Retrieved and saved the remote notewell (%d chars)", txt.length()));
		}
	} catch (Exception e) {
		Log.d(TAG, String.format(java.util.Locale.ROOT, "Failed to get remote notewell: %s", e));
	}

		try {
			if (debug) Log.d(TAG, aUrl);
			// Attempt to Collect the JSON content from the source.
			JSONObject agenda = mRemoteExecutor.executeJSONGet(aUrl);
			Log.d(TAG, String.format("remote sync started for URL: %s", aUrl));
			// Parse the JSON data retrieved locally.
			mLocalExecutor.execute(agenda, meeting.number);
			prefs.edit().putString(Prefs.LAST_ETAG, remoteEtag).apply();
			prefs.edit().putInt(Prefs.LOCAL_VERSION, VERSION_CURRENT).apply();
			Log.d(TAG, "remote sync finished");
			if (receiver != null) receiver.send(STATUS_FINISHED, Bundle.EMPTY);
		}
		catch (Exception e) {
			Log.e(TAG, "Error HTTP request " + aUrl, e);
			final Bundle bundle = new Bundle();
			bundle.putString(Intent.EXTRA_TEXT, "Connection error. No updates.");
			if (receiver != null) {
				receiver.send(STATUS_ERROR, bundle);
			}
		}
    }


    private interface Prefs {
        String LAST_ETAG = "local_etag";
		String IETFSCHED_SYNC = "ietfsched_sync";
        String LOCAL_VERSION = "local_version";
		String LAST_LENGTH = "last_length";
		String LAST_SYNC_TIME = "last_stime";
		String NOTE_WELL_CONTENT = "note_well_content";
    }
}
