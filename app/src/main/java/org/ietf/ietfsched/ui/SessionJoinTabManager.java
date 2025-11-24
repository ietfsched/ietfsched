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
 * Manages the Join tab GeckoView for SessionDetailFragment.
 * Displays Meetecho Lite URL for on-site meeting participation.
 */
public class SessionJoinTabManager extends BaseGeckoViewTabManager {
    private static final String TAG = "SessionJoinTabManager";
    private static final String TAB_JOIN = "join";
    
    public SessionJoinTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls) {
        super(fragment, rootView, tabHost, geckoViewHelpers, geckoViewTabUrls, geckoViewInitialUrls,
                TAB_JOIN, true); // Join tab is deep - navigation stays within GeckoView
    }
    
    /**
     * Initialize GeckoView for Join tab.
     * @param sharedGeckoView Optional shared GeckoView instance (singleton pattern). If null, tries to find from XML.
     */
    public void initializeGeckoView(GeckoView sharedGeckoView) {
        initializeGeckoView(sharedGeckoView, R.id.tab_session_join);
    }
    
    /**
     * Update Join tab with the session title to construct and load Meetecho Lite URL.
     * @param titleString The session title string
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     */
    public void updateJoinTab(String titleString, GeckoView sharedGeckoView) {
        Log.d(TAG, "updateJoinTab: titleString=" + titleString + ", mRootView=" + (mRootView != null));
        if (titleString == null) {
            Log.w(TAG, "updateJoinTab: titleString is null, showing loading message");
            loadLoadingMessage(sharedGeckoView, R.id.tab_session_join,
                    "Join information is being downloaded. Please check back in a moment or use the Refresh button.");
            return;
        }
        
        // Ensure view is created before initializing
        if (mRootView == null) {
            Log.w(TAG, "updateJoinTab: mRootView is null, returning");
            return;
        }
        
        // Initialize GeckoView lazily (after view is attached)
        // Use provided shared GeckoView if available, otherwise try to find it in container
        GeckoView sharedView = sharedGeckoView;
        if (sharedView == null) {
            ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_join);
            if (container != null && container.getChildCount() > 0) {
                View child = container.getChildAt(0);
                if (child instanceof GeckoView) {
                    sharedView = (GeckoView) child;
                }
            }
        }
        initializeGeckoView(sharedView);
        
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "updateJoinTab: GeckoView not initialized");
            return;
        }
        
        // Extract group acronym from session title
        String groupAcronym = extractGroupAcronym(titleString);
        
        Log.d(TAG, "updateJoinTab: Extracted groupAcronym='" + groupAcronym + "' from title='" + titleString + "'");
        
        // Construct Meetecho Lite URL: https://meetings.conf.meetecho.com/onsite{N}/?group={group}
        if (groupAcronym != null && !groupAcronym.isEmpty()) {
            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(mFragment.getActivity());
            final String meetechoUrl = "https://meetings.conf.meetecho.com/onsite" + meetingNumber + "/?group=" + groupAcronym;
            
            // Only load if URL has changed AND Join tab is currently active
            boolean joinTabActive = mTabHost != null && TAB_JOIN.equals(mTabHost.getCurrentTabTag());
            String lastUrl = mGeckoViewTabUrls.get(TAB_JOIN);
            if (!meetechoUrl.equals(lastUrl)) {
                Log.d(TAG, "updateJoinTab: URL changed, joinTabActive=" + joinTabActive);
                // Store URL for later loading when Join tab is opened
                mGeckoViewTabUrls.put(TAB_JOIN, meetechoUrl);
                if (mGeckoViewInitialUrls.get(TAB_JOIN) == null) {
                    mGeckoViewInitialUrls.put(TAB_JOIN, meetechoUrl);
                }
                // Only load if Join tab is currently active - don't load on first load if tab is not active
                if (joinTabActive) {
                    Log.d(TAG, "updateJoinTab: Loading URL: " + meetechoUrl);
                    mGeckoViewHelper.loadUrl(meetechoUrl);
                } else {
                    Log.d(TAG, "updateJoinTab: Skipping load - Join tab not active, will load when tab is opened");
                }
            } else {
                Log.d(TAG, "updateJoinTab: URL unchanged: " + meetechoUrl);
                // If URL is unchanged but tab is active, ensure it's loaded (e.g., after a tab switch)
                if (joinTabActive && mGeckoViewHelper.getGeckoView() != null && 
                    mGeckoViewHelper.getGeckoView().getSession() == mGeckoViewHelper.getGeckoSession()) {
                    Log.d(TAG, "updateJoinTab: URL unchanged but tab active, ensuring load: " + meetechoUrl);
                    mGeckoViewHelper.loadUrl(meetechoUrl);
                }
            }
        } else {
            Log.w(TAG, "updateJoinTab: groupAcronym is null or empty, showing loading message");
            loadLoadingMessage(sharedGeckoView, R.id.tab_session_join,
                    "Join information is being downloaded. Please check back in a moment or use the Refresh button.");
            mGeckoViewTabUrls.remove(TAB_JOIN);
            mGeckoViewInitialUrls.remove(TAB_JOIN);
        }
    }
    
    /**
     * Update Join tab with the session title (backward compatibility - no shared GeckoView).
     */
    public void updateJoinTab(String titleString) {
        updateJoinTab(titleString, null);
    }
    
    private String extractGroupAcronym(String titleString) {
        // Title format: "{area} - {group} - {title}" or " -{group} - {title}" if area is empty
        // We need to extract the group (middle part)
        if (titleString == null || !titleString.contains(" - ")) {
            return null;
        }
        
        String[] parts = titleString.split(" - ", 3);
        Log.d(TAG, "updateJoinTab: Split title into " + parts.length + " parts");
        
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
        return mFragment.getString(R.string.join_error_message);
    }
}

