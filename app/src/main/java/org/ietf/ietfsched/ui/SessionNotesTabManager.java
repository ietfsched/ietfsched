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

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.R;
import org.ietf.ietfsched.util.GeckoViewHelper;
import org.mozilla.geckoview.GeckoView;

/**
 * Manages the Notes tab GeckoView and URL construction for SessionDetailFragment.
 */
public class SessionNotesTabManager extends BaseGeckoViewTabManager {
    private static final String TAG = "SessionNotesTabManager";
    private static final String TAB_NOTES = "notes";
    
    public SessionNotesTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls) {
        super(fragment, rootView, tabHost, geckoViewHelpers, geckoViewTabUrls, geckoViewInitialUrls,
                TAB_NOTES, true); // Notes tab is deep
    }
    
    /**
     * Initialize GeckoView for Notes tab.
     * @param sharedGeckoView Optional shared GeckoView instance (singleton pattern). If null, tries to find from XML.
     */
    public void initializeGeckoView(GeckoView sharedGeckoView) {
        initializeGeckoView(sharedGeckoView, R.id.tab_session_notes);
    }
    
    /**
     * Update Notes tab with the session title to construct and load HedgeDoc URL.
     * @param titleString The session title string
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     */
    public void updateNotesTab(String titleString, GeckoView sharedGeckoView) {
        Log.d(TAG, "updateNotesTab: titleString=" + titleString + ", mRootView=" + (mRootView != null));
        if (titleString == null) {
            Log.w(TAG, "updateNotesTab: titleString is null, returning");
            return;
        }
        
        // Ensure view is created before initializing
        if (mRootView == null) {
            Log.w(TAG, "updateNotesTab: mRootView is null, returning");
            return;
        }
        
        // Initialize GeckoView lazily (after view is attached)
        // Use provided shared GeckoView if available, otherwise try to find it in container
        GeckoView sharedView = sharedGeckoView;
        if (sharedView == null) {
            ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_notes);
            if (container != null && container.getChildCount() > 0) {
                View child = container.getChildAt(0);
                if (child instanceof GeckoView) {
                    sharedView = (GeckoView) child;
                }
            }
        }
        initializeGeckoView(sharedView);
        
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "updateNotesTab: GeckoView not initialized");
            return;
        }
        
        // Extract group acronym from session title
        String groupAcronym = extractGroupAcronym(titleString);
        
        Log.d(TAG, "updateNotesTab: Extracted groupAcronym='" + groupAcronym + "' from title='" + titleString + "'");
        
        // Construct HedgeDoc URL: https://notes.ietf.org/notes-ietf-{meetingNumber}-{groupAcronym}?view
        if (groupAcronym != null && !groupAcronym.isEmpty()) {
            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(mFragment.getActivity());
            final String hedgedocUrl = "https://notes.ietf.org/notes-ietf-" + meetingNumber + "-" + groupAcronym + "?view";
            
            // Only load if URL has changed AND Notes tab is currently active
            boolean notesTabActive = mTabHost != null && TAB_NOTES.equals(mTabHost.getCurrentTabTag());
            String lastUrl = mGeckoViewTabUrls.get(TAB_NOTES);
            boolean isFirstLoad = (lastUrl == null);
            if (!hedgedocUrl.equals(lastUrl)) {
                Log.d(TAG, "updateNotesTab: URL changed, notesTabActive=" + notesTabActive + ", isFirstLoad=" + isFirstLoad);
                // Only load if Notes tab is active or if this is the first load
                if (notesTabActive || isFirstLoad) {
                    Log.d(TAG, "updateNotesTab: Loading URL: " + hedgedocUrl);
                    mGeckoViewHelper.loadUrl(hedgedocUrl);
                    mGeckoViewTabUrls.put(TAB_NOTES, hedgedocUrl);
                    // Track this as the initial URL for this tab
                    mGeckoViewInitialUrls.put(TAB_NOTES, hedgedocUrl);
                } else {
                    Log.d(TAG, "updateNotesTab: Skipping load - Notes tab not active, will load when tab is opened");
                    // Store URL for later loading when Notes tab is opened
                    mGeckoViewTabUrls.put(TAB_NOTES, hedgedocUrl);
                }
            } else {
                Log.d(TAG, "updateNotesTab: URL unchanged, skipping reload: " + hedgedocUrl);
            }
        } else {
            Log.w(TAG, "updateNotesTab: groupAcronym is null or empty, showing loading message");
            loadLoadingMessage(sharedGeckoView, R.id.tab_session_notes,
                    "Notes are being downloaded. Please check back in a moment or use the Refresh button.");
            mGeckoViewTabUrls.remove(TAB_NOTES);
            mGeckoViewInitialUrls.remove(TAB_NOTES);
        }
    }
    
    /**
     * Update Notes tab with the session title (backward compatibility - no shared GeckoView).
     */
    public void updateNotesTab(String titleString) {
        updateNotesTab(titleString, null);
    }
    
    private String extractGroupAcronym(String titleString) {
        // Title format: "{area} - {group} - {title}" or " -{group} - {title}" if area is empty
        // We need to extract the group (middle part)
        if (titleString == null || !titleString.contains(" - ")) {
            return null;
        }
        
        String[] parts = titleString.split(" - ", 3);
        Log.d(TAG, "updateNotesTab: Split title into " + parts.length + " parts");
        
        // Group is typically the middle part (index 1)
        // But if area is empty, format is " -{group} - {title}", so parts[0] might be empty
        if (parts.length >= 2) {
            // Use the middle part (index 1) as the group
            String groupAcronym = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
            // Remove any leading dash if area was empty
            if (groupAcronym.startsWith("-")) {
                groupAcronym = groupAcronym.substring(1).trim();
            }
            return groupAcronym;
        } else if (parts.length == 1 && titleString.startsWith(" -")) {
            // Handle case where area is empty: " -{group} - {title}"
            // Split on " -" (space-dash) instead
            String[] altParts = titleString.split(" -", 3);
            if (altParts.length >= 2) {
                return altParts[1].split(" -", 2)[0].toLowerCase(java.util.Locale.ROOT).trim();
            }
        }
        return null;
    }
    
    @Override
    protected String getErrorMessage() {
        return "Unable to load notes. Please check your internet connection and try again.";
    }
}

