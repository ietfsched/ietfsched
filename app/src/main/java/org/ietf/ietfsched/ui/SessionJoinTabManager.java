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
import android.widget.TextView;
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

    private TextView mReloadButton;
    private String mMeetechoEntryUrl;

    public SessionJoinTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls) {
        super(fragment, rootView, tabHost, geckoViewHelpers, geckoViewTabUrls, geckoViewInitialUrls,
                TAB_JOIN, true); // Join tab is deep - navigation stays within GeckoView
        // Meetecho Datatracker login uses window.open + window.opener token handoff.
        // Do not reload Meetecho after OAuth ? that wipes the in-memory handoff.
        mGeckoViewHelper.setOAuthPopupsEnabled(true);
    }

    /**
     * Join uses dedicated lab-style TextureView siblings in {@code tab_session_join}, not the
     * shared Notes SurfaceView.
     */
    public void initializeGeckoView(GeckoView unusedSharedGeckoView) {
        initializeDedicatedJoinGeckoViews();
    }

    public void initializeDedicatedJoinGeckoViews() {
        if (mRootView == null) {
            Log.w(TAG, "initializeDedicatedJoinGeckoViews: mRootView is null");
            return;
        }
        GeckoView main = mRootView.findViewById(R.id.join_gecko_main);
        GeckoView popup = mRootView.findViewById(R.id.join_gecko_oauth_popup);
        if (main == null || popup == null) {
            Log.e(TAG, "initializeDedicatedJoinGeckoViews: join_gecko_main/popup missing from layout");
            return;
        }
        // Same backends as DebugOAuthActivity ? fixed siblings, never reparented.
        main.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        popup.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        mGeckoViewHelper.setOAuthPopupGeckoView(popup);
        mGeckoViewHelper.initialize(main);
        ensureReloadButton();
        Log.d(TAG, "initializeDedicatedJoinGeckoViews: main+popup TextureView ready");
    }

    private void ensureReloadButton() {
        if (mRootView == null) {
            return;
        }
        if (mReloadButton == null) {
            mReloadButton = mRootView.findViewById(R.id.join_reload_button);
            if (mReloadButton != null) {
                mReloadButton.setOnClickListener(v -> reloadMeetechoEntry());
            }
        }
        updateReloadButtonVisibility();
    }

    private void updateReloadButtonVisibility() {
        if (mReloadButton == null) {
            return;
        }
        boolean joinTabActive = mTabHost != null && TAB_JOIN.equals(mTabHost.getCurrentTabTag());
        boolean show = joinTabActive
                && mMeetechoEntryUrl != null
                && !mMeetechoEntryUrl.isEmpty()
                && !mGeckoViewHelper.isOAuthPopupOpen();
        mReloadButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Reload the Join entry URL (group landing). Prefer this over refreshing an error page
     * after Meetecho disconnects; cookies usually keep the user signed in.
     */
    public void reloadMeetechoEntry() {
        if (mMeetechoEntryUrl == null || mMeetechoEntryUrl.isEmpty()) {
            Log.w(TAG, "reloadMeetechoEntry: no entry URL");
            return;
        }
        if (mGeckoViewHelper.isOAuthPopupOpen()) {
            Log.d(TAG, "reloadMeetechoEntry: OAuth popup open, ignoring");
            return;
        }
        initializeDedicatedJoinGeckoViews();
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "reloadMeetechoEntry: GeckoView not initialized");
            return;
        }
        Log.d(TAG, "reloadMeetechoEntry: Loading " + mMeetechoEntryUrl);
        mGeckoViewHelper.loadUrl(mMeetechoEntryUrl);
    }

    public void updateJoinTab(String titleString, GeckoView sharedGeckoView) {
        Log.d(TAG, "updateJoinTab: titleString=" + titleString + ", mRootView=" + (mRootView != null));
        if (titleString == null) {
            Log.w(TAG, "updateJoinTab: titleString is null, showing loading message");
            mMeetechoEntryUrl = null;
            initializeDedicatedJoinGeckoViews();
            updateReloadButtonVisibility();
            loadLoadingMessage(null, R.id.tab_session_join,
                    "Join information is being downloaded. Please check back in a moment.");
            return;
        }

        if (mRootView == null) {
            Log.w(TAG, "updateJoinTab: mRootView is null, returning");
            return;
        }

        boolean joinTabActive = mTabHost != null && TAB_JOIN.equals(mTabHost.getCurrentTabTag());
        String groupAcronym = extractGroupAcronym(titleString);
        Log.d(TAG, "updateJoinTab: Extracted groupAcronym='" + groupAcronym + "' from title='" + titleString + "'");

        if (groupAcronym != null && !groupAcronym.isEmpty()) {
            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(mFragment.getActivity());
            final String meetechoUrl = "https://meetings.conf.meetecho.com/onsite" + meetingNumber + "/?group=" + groupAcronym;
            mMeetechoEntryUrl = meetechoUrl;

            String lastUrl = mGeckoViewTabUrls.get(TAB_JOIN);
            if (!meetechoUrl.equals(lastUrl)) {
                Log.d(TAG, "updateJoinTab: URL changed, joinTabActive=" + joinTabActive);
                mGeckoViewTabUrls.put(TAB_JOIN, meetechoUrl);
                if (mGeckoViewInitialUrls.get(TAB_JOIN) == null) {
                    mGeckoViewInitialUrls.put(TAB_JOIN, meetechoUrl);
                }
            } else {
                Log.d(TAG, "updateJoinTab: URL unchanged: " + meetechoUrl);
            }

            if (!joinTabActive) {
                Log.d(TAG, "updateJoinTab: Skipping init/load - Join tab not active");
                updateReloadButtonVisibility();
                return;
            }

            initializeDedicatedJoinGeckoViews();

            if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
                Log.w(TAG, "updateJoinTab: GeckoView not initialized");
                return;
            }

            if (mGeckoViewHelper.isOAuthPopupOpen()) {
                Log.d(TAG, "updateJoinTab: OAuth in progress, preserving popup");
                updateReloadButtonVisibility();
                return;
            }

            if (!meetechoUrl.equals(lastUrl)) {
                Log.d(TAG, "updateJoinTab: Loading URL: " + meetechoUrl);
                mGeckoViewHelper.loadUrl(meetechoUrl);
            } else if (!mGeckoViewHelper.hasMainSessionContent()) {
                Log.d(TAG, "updateJoinTab: Loading initial URL: " + meetechoUrl);
                mGeckoViewHelper.loadUrl(meetechoUrl);
            } else {
                Log.d(TAG, "updateJoinTab: Preserving loaded session at " + mGeckoViewHelper.getCurrentUrl());
            }
            updateReloadButtonVisibility();
        } else {
            Log.w(TAG, "updateJoinTab: groupAcronym is null or empty");
            mMeetechoEntryUrl = null;
            mGeckoViewTabUrls.remove(TAB_JOIN);
            mGeckoViewInitialUrls.remove(TAB_JOIN);
            if (joinTabActive) {
                initializeDedicatedJoinGeckoViews();
                loadLoadingMessage(null, R.id.tab_session_join,
                        "Join information is being downloaded. Please check back in a moment.");
            }
            updateReloadButtonVisibility();
        }
    }

    public void updateJoinTab(String titleString) {
        updateJoinTab(titleString, null);
    }

    private String extractGroupAcronym(String titleString) {
        if (titleString == null || !titleString.contains(" - ")) {
            return null;
        }

        String[] parts = titleString.split(" - ", 3);
        Log.d(TAG, "updateJoinTab: Split title into " + parts.length + " parts");

        if (parts.length >= 2) {
            String groupAcronym = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
            if (groupAcronym.startsWith("-")) {
                groupAcronym = groupAcronym.substring(1).trim();
            }
            return groupAcronym;
        } else if (parts.length == 1 && titleString.startsWith(" -")) {
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
