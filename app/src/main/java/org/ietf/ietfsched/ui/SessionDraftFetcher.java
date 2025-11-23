/*
 * Copyright 2025
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

package org.ietf.ietfsched.ui;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.io.RemoteExecutor;
import org.ietf.ietfsched.provider.ScheduleContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles fetching and parsing Internet drafts for sessions.
 */
public class SessionDraftFetcher {
    private static final String TAG = "SessionDraftFetcher";
    
    private final Fragment mFragment;
    private final Uri mSessionUri;
    private final RemoteExecutor mRemoteExecutor;
    private final Runnable mOnDraftsFetchedCallback;
    private boolean mDraftsFetched = false;
    
    public SessionDraftFetcher(Fragment fragment, Uri sessionUri, RemoteExecutor remoteExecutor, 
            Runnable onDraftsFetchedCallback) {
        mFragment = fragment;
        mSessionUri = sessionUri;
        mRemoteExecutor = remoteExecutor;
        mOnDraftsFetchedCallback = onDraftsFetchedCallback;
    }
    
    /**
     * Fetches drafts on-demand when Content tab is opened.
     * This runs in the background and updates the database if successful.
     * Slides will still display even if this fails (e.g., offline).
     */
    public void fetchDraftsOnDemand() {
        if (mDraftsFetched || mFragment.getActivity() == null) {
            return;
        }

        // Check if drafts are already in database, and get session_res_uri
        Cursor cursor = null;
        String sessionResUri = null;
        try {
            cursor = mFragment.getActivity().getContentResolver().query(
                mSessionUri,
                new String[]{ScheduleContract.Sessions.SESSION_DRAFTS_URL, ScheduleContract.Sessions.SESSION_RES_URI},
                null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String draftsUrl = cursor.getString(0);
                sessionResUri = cursor.getString(1);
                if (!TextUtils.isEmpty(draftsUrl)) {
                    // Drafts already exist in database, no need to fetch
                    mDraftsFetched = true;
                    return;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchDraftsOnDemand: Error checking database", e);
            // Continue - slides should still display
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Mark as fetched to prevent duplicate requests
        mDraftsFetched = true;

        // If no session_res_uri, can't fetch drafts
        if (TextUtils.isEmpty(sessionResUri)) {
            return;
        }

        // Fetch drafts in background thread
        final String finalSessionResUri = sessionResUri;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String detailUrl = "https://datatracker.ietf.org" + finalSessionResUri + "?format=json";
                    JSONObject detailJson = mRemoteExecutor.executeJSONGet(detailUrl);
                    if (detailJson != null) {
                        JSONArray materialsArray = detailJson.optJSONArray("materials");
                        if (materialsArray != null && materialsArray.length() > 0) {
                            // Parse drafts from materials array
                            java.util.List<String> draftList = parseDraftsFromMaterials(materialsArray);
                            if (draftList != null && draftList.size() > 0) {
                                // Update database with drafts
                                ContentValues values = new ContentValues();
                                values.put(ScheduleContract.Sessions.SESSION_DRAFTS_URL, TextUtils.join("::", draftList));
                                mFragment.getActivity().getContentResolver().update(mSessionUri, values, null, null);
                                
                                // Notify callback on UI thread
                                if (mOnDraftsFetchedCallback != null) {
                                    new Handler(Looper.getMainLooper()).post(mOnDraftsFetchedCallback);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "fetchDraftsOnDemand: Failed to fetch drafts", e);
                    // Don't throw - slides should still display
                }
            }
        }).start();
    }
    
    /**
     * Parse Internet drafts from a materials JSONArray.
     * Returns a list of draft entries in "draft-name|||url" format, where draft-name is the raw draft identifier (e.g., "draft-ietf-6man-enhanced-vpn-vtn-id").
     */
    public static java.util.List<String> parseDraftsFromMaterials(JSONArray materialsArray) {
        java.util.List<String> draftList = new java.util.ArrayList<>();
        if (materialsArray == null) return draftList;
        
        try {
            for (int i = 0; i < materialsArray.length(); i++) {
                String materialUri = materialsArray.getString(i);
                // Materials are API endpoints like "/api/v1/doc/document/draft-richardson-emu-eap-onboarding/"
                if (materialUri != null && materialUri.contains("/api/") && materialUri.contains("draft-")) {
                    // Extract draft name from URI: /api/v1/doc/document/draft-name/ -> draft-name
                    String[] parts = materialUri.split("/");
                    for (String part : parts) {
                        if (part.startsWith("draft-")) {
                            // Construct URL to the draft document
                            String draftUrl = "https://datatracker.ietf.org/doc/" + part + "/";
                            // Use draft name as-is (e.g., "draft-ietf-6man-enhanced-vpn-vtn-id")
                            draftList.add(part + "|||" + draftUrl);
                            break;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Error parsing materials for drafts", e);
        }
        return draftList;
    }
    
    /**
     * Reset the fetched flag (useful for testing or when session changes).
     */
    public void reset() {
        mDraftsFetched = false;
    }
}

