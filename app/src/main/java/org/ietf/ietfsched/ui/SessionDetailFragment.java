/*
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

package org.ietf.ietfsched.ui;

import org.ietf.ietfsched.R;
import org.ietf.ietfsched.io.RemoteExecutor;
import org.ietf.ietfsched.provider.ScheduleContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ietf.ietfsched.util.ActivityHelper;
import org.ietf.ietfsched.util.FractionalTouchDelegate;
import org.ietf.ietfsched.util.GeckoRuntimeHelper;
import org.ietf.ietfsched.util.GeckoViewHelper;
import org.ietf.ietfsched.util.NotifyingAsyncQueryHandler;
import org.ietf.ietfsched.util.UIUtils;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;

/**
 * A fragment that shows detail information for a session, including session title, abstract,
 * time information, speaker photos and bios, etc.
 */
public class SessionDetailFragment extends Fragment implements
        NotifyingAsyncQueryHandler.AsyncQueryListener,
        CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "SessionDetailFragment";

    /**
     * Since sessions can belong tracks, the parent activity can send this extra specifying a
     * track URI that should be used for coloring the title-bar.
     */
    public static final String EXTRA_TRACK = "org.ietf.ietfsched.extra.TRACK";

    private static final String TAG_CONTENT = "content";
    private static final String TAG_NOTES = "notes";
    private static final String TAG_LINKS = "links";
    private static final String TAG_JOIN = "join";

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    private String mSessionId;
    private Uri mSessionUri;
    private Uri mTrackUri;

    private String mTitleString;
    private String mHashtag;
    private String mUrl;
    private String mRoomId;

    private ViewGroup mRootView;
    private TabHost mTabHost;
    private TextView mTitle;
    private TextView mSubtitle;
    private CompoundButton mStarred;
    

    // Generic GeckoView wrapper: support multiple GeckoView tabs
    // Key: tab tag (e.g., TAG_NOTES), Value: GeckoViewHelper instance for that tab
    private java.util.Map<String, GeckoViewHelper> mGeckoViewHelpers = new java.util.HashMap<String, GeckoViewHelper>();
    private String mCurrentTabTag; // Retained tab selection
    // Track last loaded URL per GeckoView tab to avoid reloading unnecessarily
    // Key: tab tag (e.g., TAG_NOTES), Value: last URL that should be loaded for that tab
    private java.util.Map<String, String> mGeckoViewTabUrls = new java.util.HashMap<String, String>();
    // Track the initial URL for each GeckoView tab to determine if we're at the "root" of navigation
    // Key: tab tag (e.g., TAG_NOTES), Value: initial URL that was loaded for that tab
    private java.util.Map<String, String> mGeckoViewInitialUrls = new java.util.HashMap<String, String>();

    private NotifyingAsyncQueryHandler mHandler;
    private RemoteExecutor mRemoteExecutor;
    private boolean mDraftsFetched = false; // Track if we've fetched drafts for this session

    private boolean mSessionCursor = false;
    private boolean mSpeakersCursor = false;
    private boolean mHasSummaryContent = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = BaseActivity.fragmentArgumentsToIntent(getArguments());
        mSessionUri = intent.getData();

        if (mSessionUri == null) {
            return;
        }
        
        mSessionId = ScheduleContract.Sessions.getSessionId(mSessionUri);
        
        // Retain fragment instance across configuration changes to preserve GeckoView state and tab selection
        setRetainInstance(true);

        setHasOptionsMenu(true);
    }
    
    @Override
    public void onResume() {	
        super.onResume();
        updateNotesTab();

        // Start listening for time updates to adjust "now" bar. TIME_TICK is
        // triggered once per minute, which is how we move the bar over time.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        getActivity().registerReceiver(mPackageChangesReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mPackageChangesReceiver);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Save current tab selection
        if (mTabHost != null) {
            outState.putString("current_tab", mTabHost.getCurrentTabTag());
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mSessionUri == null) {
            return;
        }

        // Start background queries to load session and track details
//        final Uri speakersUri = ScheduleContract.Sessions.buildSpeakersDirUri(mSessionId);

        mHandler = new NotifyingAsyncQueryHandler(getActivity().getContentResolver(), this);
        mRemoteExecutor = new RemoteExecutor();
        mHandler.startQuery(SessionsQuery._TOKEN, mSessionUri, SessionsQuery.PROJECTION);
//        mHandler.startQuery(TracksQuery._TOKEN, mTrackUri, TracksQuery.PROJECTION);
 //       mHandler.startQuery(SpeakersQuery._TOKEN, speakersUri, SpeakersQuery.PROJECTION);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_session_detail, container, false);
        mTabHost = (TabHost) mRootView.findViewById(android.R.id.tabhost);
        mTabHost.setup();
        
        // Restore tab selection from retained instance or savedInstanceState
        final String savedTab;
        if (mCurrentTabTag != null) {
            savedTab = mCurrentTabTag;
        } else if (savedInstanceState != null) {
            savedTab = savedInstanceState.getString("current_tab");
        } else {
            savedTab = null;
        }
        
        if (savedTab != null) {
            // Set tab after setup is complete - use post to ensure tabs are ready
            mTabHost.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mTabHost.setCurrentTabByTag(savedTab);
                        // mCurrentTabTag is already set from savedTab, no need to update again
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to restore tab selection", e);
                    }
                }
            });
        }
        
        // Set up tab change listener to track current tab and fetch drafts when Content tab opens
        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                mCurrentTabTag = tabId;
                // Fetch drafts on-demand when Content tab is opened
                if (TAG_CONTENT.equals(tabId) && !mDraftsFetched && mSessionId != null) {
                    fetchDraftsOnDemand();
                }
                // Handle GeckoView tab switching: preserve state if URL unchanged, re-initialize if URL changed
                if (isGeckoViewTab(tabId)) {
                    String expectedUrl = mGeckoViewTabUrls.get(tabId);
                    String initialUrl = mGeckoViewInitialUrls.get(tabId);
                    
                    // If URL has changed (e.g., different session) or initialUrl is null, re-initialize GeckoView
                    // Otherwise, preserve navigation state
                    if (expectedUrl != null && (initialUrl == null || !expectedUrl.equals(initialUrl))) {
                        Log.d(TAG, "onTabChanged: URL changed for GeckoView tab " + tabId + " (expected=" + expectedUrl + ", initial=" + initialUrl + "), re-initializing");
                        reinitializeGeckoView(tabId);
                        // Load the new URL
                        GeckoViewHelper helper = mGeckoViewHelpers.get(tabId);
                        if (helper != null) {
                            Log.d(TAG, "onTabChanged: Loading new URL for GeckoView tab " + tabId + ": " + expectedUrl);
                            helper.loadUrl(expectedUrl);
                            mGeckoViewInitialUrls.put(tabId, expectedUrl);
                        }
                    } else if (expectedUrl == null) {
                        // No URL stored yet, trigger update to construct and load URL
                        Log.d(TAG, "onTabChanged: No URL stored for GeckoView tab " + tabId + ", triggering update");
                        updateGeckoViewTab(tabId);
                    } else {
                        // URL unchanged, preserve navigation state - just ensure GeckoView is initialized
                        Log.d(TAG, "onTabChanged: URL unchanged for GeckoView tab " + tabId + " (URL=" + expectedUrl + "), preserving navigation state");
                        ensureWebViewInitialized(tabId);
                    }
                }
                // Log tab change with GeckoView state if applicable
                if (isGeckoViewTab(tabId)) {
                    GeckoViewHelper helper = mGeckoViewHelpers.get(tabId);
                    boolean canGoBack = helper != null && helper.canGoBack();
                    Log.d(TAG, "onTabChanged: tabId=" + tabId + ", canGoBack=" + canGoBack);
                } else {
                    Log.d(TAG, "onTabChanged: tabId=" + tabId);
                }
            }
        });

        mTitle = (TextView) mRootView.findViewById(R.id.session_title);
        mSubtitle = (TextView) mRootView.findViewById(R.id.session_subtitle);
        mStarred = (CompoundButton) mRootView.findViewById(R.id.star_button);

        mStarred.setFocusable(true);
        mStarred.setClickable(true);

        // Larger target triggers star toggle
        final View starParent = mRootView.findViewById(R.id.header_session);
        FractionalTouchDelegate.setupDelegate(starParent, mStarred, new RectF(0.6f, 0f, 1f, 0.8f));

        setupLinksTab();
        setupJoinTab();
        setupContentTab();
        setupNotesTab();

        return mRootView;
    }

    /**
     * Build and add "content" tab.
     */
    private void setupContentTab() {
        // Content tab includes slides and drafts
        mTabHost.addTab(mTabHost.newTabSpec(TAG_CONTENT)
                .setIndicator(buildIndicator(R.string.session_content))
                .setContent(R.id.tab_session_summary));
    }

    /**
     * Updates the Content tab with Internet drafts and presentation slides.
     */
    private void updateContentTab(Cursor cursor) {
        // Find the included view first, then find the container inside it
        View includedView = mRootView.findViewById(R.id.tab_session_summary);
        ViewGroup container = null;
        if (includedView != null) {
            container = (ViewGroup) includedView.findViewById(R.id.summary_container);
        }
        if (container == null) {
            // Fallback: try direct find
            container = (ViewGroup) mRootView.findViewById(R.id.summary_container);
        }
        if (container == null) {
            Log.e(TAG, "updateContentTab: summary_container not found!");
            return;
        }
        // Remove all views from container (we'll add content below)
        container.removeAllViews();
        
        // Ensure container is visible
        container.setVisibility(View.VISIBLE);

        LayoutInflater inflater = getLayoutInflater(null);
        boolean hasContent = false;

        // Note: cursor should already be at first position from onSessionQueryComplete
        // But ensure it's valid
        if (cursor == null) {
            Log.e(TAG, "updateContentTab: cursor is null!");
            return;
        }

        // First, add Presentation Slides section
        final String pdfUrl = cursor.getString(SessionsQuery.PDF_URL);
        if (!TextUtils.isEmpty(pdfUrl)) {
            String[] slideEntries = pdfUrl.split("::");
            boolean hasValidSlides = false;
            for (String entry : slideEntries) {
                if (!entry.trim().isEmpty()) {
                    hasValidSlides = true;
                    break;
                }
            }
            
            if (hasValidSlides) {
                TextView slidesHeader = createSectionHeader(R.string.session_link_pdf);
                // Add padding above the header
                android.widget.LinearLayout.LayoutParams headerParams = 
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                headerParams.topMargin = getResources().getDimensionPixelSize(R.dimen.body_padding_medium);
                slidesHeader.setLayoutParams(headerParams);
                container.addView(slidesHeader);
                hasContent = true;
                
                // Add slide links
                for (int j = 0; j < slideEntries.length; j++) {
                    final String slideEntry = slideEntries[j].trim();
                    if (slideEntry.isEmpty()) continue;
                    
                    // Parse "title|||url" format
                    final String slideTitle;
                    final String slideUrl;
                    if (slideEntry.contains("|||")) {
                        String[] parts = slideEntry.split("\\|\\|\\|", 2);
                        slideTitle = parts[0].trim();
                        slideUrl = parts[1].trim();
                    } else {
                        // Fallback for old format (just URL without title)
                        slideTitle = slideEntries.length == 1 
                            ? getString(R.string.session_link_pdf)
                            : getString(R.string.session_link_pdf) + " " + (j + 1);
                        slideUrl = slideEntry;
                    }
                    
                    ViewGroup linkContainer = (ViewGroup)
                            inflater.inflate(R.layout.list_item_session_link, container, false);
                    
                    ((TextView) linkContainer.findViewById(R.id.link_text)).setText(slideTitle);
                    
                    linkContainer.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            fireLinkEvent(R.string.session_link_pdf);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(slideUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                            startActivity(intent);
                        }
                    });
                    
                    container.addView(linkContainer);
                    container.addView(createThinSeparator());
                }
            }
        }

        // Then, add Internet Drafts section
        final String draftsUrl = cursor.getString(SessionsQuery.DRAFTS_URL);
        if (!TextUtils.isEmpty(draftsUrl)) {
            String[] draftEntries = draftsUrl.split("::");
            boolean hasValidDrafts = false;
            for (String entry : draftEntries) {
                if (!entry.trim().isEmpty()) {
                    hasValidDrafts = true;
                    break;
                }
            }
            
            if (hasValidDrafts) {
                TextView draftsHeader = createSectionHeader(R.string.session_drafts);
                // Add padding above the header
                android.widget.LinearLayout.LayoutParams headerParams = 
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                headerParams.topMargin = getResources().getDimensionPixelSize(R.dimen.body_padding_medium);
                draftsHeader.setLayoutParams(headerParams);
                container.addView(draftsHeader);
                hasContent = true;
                
                // Add draft links
                for (int j = 0; j < draftEntries.length; j++) {
                    final String draftEntry = draftEntries[j].trim();
                    if (draftEntry.isEmpty()) continue;
                    
                    // Parse "title|||url" format (same as slides)
                    final String draftTitle;
                    final String draftUrl;
                    if (draftEntry.contains("|||")) {
                        String[] parts = draftEntry.split("\\|\\|\\|", 2);
                        draftTitle = parts[0].trim();
                        draftUrl = parts[1].trim();
                    } else {
                        // Fallback: treat as URL and extract filename
                        draftUrl = draftEntry;
                        String draftFileName = draftUrl.substring(draftUrl.lastIndexOf('/') + 1);
                        if (draftFileName.endsWith("/")) {
                            draftFileName = draftFileName.substring(0, draftFileName.length() - 1);
                        }
                        draftTitle = draftFileName;
                    }
                    
                    ViewGroup linkContainer = (ViewGroup)
                            inflater.inflate(R.layout.list_item_session_link, container, false);
                    
                    ((TextView) linkContainer.findViewById(R.id.link_text)).setText(draftTitle);
                    
                    linkContainer.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            fireLinkEvent(R.string.session_drafts);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(draftUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                            startActivity(intent);
                        }
                    });
                    
                    container.addView(linkContainer);
                    container.addView(createThinSeparator());
                }
            }
        }

        // Show empty message if no content
        if (!hasContent) {
            TextView emptyView = new TextView(getActivity());
            emptyView.setId(android.R.id.empty);
            emptyView.setText(getString(R.string.empty_session_detail));
            emptyView.setGravity(android.view.Gravity.CENTER);
            emptyView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
            emptyView.setLayoutParams(params);
            container.addView(emptyView);
        }
    }

    /**
     * Fetches drafts on-demand when Content tab is opened.
     * This runs in the background and updates the database if successful.
     * Slides will still display even if this fails (e.g., offline).
     */
    private void fetchDraftsOnDemand() {
        if (mDraftsFetched || mSessionId == null || mRemoteExecutor == null || getActivity() == null) {
            return;
        }

        // Check if drafts are already in database, and get session_res_uri
        Cursor cursor = null;
        String sessionResUri = null;
        try {
            cursor = getActivity().getContentResolver().query(
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
                                getActivity().getContentResolver().update(mSessionUri, values, null, null);
                                
                                // Refresh Content tab on UI thread
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Re-query to refresh the Content tab
                                        mHandler.startQuery(SessionsQuery._TOKEN, mSessionUri, SessionsQuery.PROJECTION);
                                    }
                                });
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
    private java.util.List<String> parseDraftsFromMaterials(JSONArray materialsArray) {
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
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     *
     * @param textRes
     * @return View
     */
    private View buildIndicator(int textRes) {
        return buildIndicator(textRes, R.drawable.tab_indicator);
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label and a custom background drawable.
     *
     * @param textRes
     * @param backgroundRes
     * @return View
     */
    private View buildIndicator(int textRes, int backgroundRes) {
        final TextView indicator = (TextView) getActivity().getLayoutInflater()
                .inflate(R.layout.tab_indicator,
                        (ViewGroup) mRootView.findViewById(android.R.id.tabs), false);
        indicator.setText(textRes);
        indicator.setBackgroundResource(backgroundRes);
        return indicator;
    }

    /**
     * Derive {@link org.ietf.ietfsched.provider.ScheduleContract.Tracks#CONTENT_ITEM_TYPE}
     * {@link Uri} based on incoming {@link Intent}, using
     * {@link #EXTRA_TRACK} when set.
     * @param intent
     * @return Uri
     */
    private Uri resolveTrackUri(Intent intent) {
        final Uri trackUri = intent.getParcelableExtra(EXTRA_TRACK);
        if (trackUri != null) {
            return trackUri;
        } else {
            return ScheduleContract.Sessions.buildTracksDirUri(mSessionId);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (getActivity() == null) {
            return;
        }

        if (token == SessionsQuery._TOKEN) {
            onSessionQueryComplete(cursor);
        } else {
            cursor.close();
        }
    }

    /**
     * Handle {@link SessionsQuery} {@link Cursor}.
     */
    private void onSessionQueryComplete(Cursor cursor) {
        try {
            mSessionCursor = true;
            if (!cursor.moveToFirst()) {
                return;
            }

            // Format time block this session occupies
            final long blockStart = cursor.getLong(SessionsQuery.BLOCK_START);
            final long blockEnd = cursor.getLong(SessionsQuery.BLOCK_END);
            final String roomName = cursor.getString(SessionsQuery.ROOM_NAME);
            final String subtitle = UIUtils.formatSessionSubtitle(blockStart,
                    blockEnd, roomName, getActivity());

            mTitleString = cursor.getString(SessionsQuery.TITLE);
            mTitle.setText(mTitleString);
            mSubtitle.setText(subtitle);

            mUrl = cursor.getString(SessionsQuery.SESSION_URL);
            if (TextUtils.isEmpty(mUrl)) {
                mUrl = "";
            }

            mHashtag = cursor.getString(SessionsQuery.HASHTAG);

            mRoomId = cursor.getString(SessionsQuery.ROOM_ID);

            // Unregister around setting checked state to avoid triggering
            // listener since change isn't user generated.
            mStarred.setOnCheckedChangeListener(null);
            mStarred.setChecked(cursor.getInt(SessionsQuery.STARRED) != 0);
            mStarred.setOnCheckedChangeListener(this);

            updateLinksTab(cursor);
            updateContentTab(cursor);
            updateNotesTab();

        } finally {
            cursor.close();
        }
    }

    /**
     * Handle {@link TracksQuery} {@link Cursor}.
     */
    private void onTrackQueryComplete(Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) {
                return;
            }

            // Use found track to build title-bar
            ActivityHelper activityHelper = ((BaseActivity) getActivity()).getActivityHelper();
            activityHelper.setActionBarTitle(cursor.getString(TracksQuery.TRACK_NAME));
            activityHelper.setActionBarColor(cursor.getInt(TracksQuery.TRACK_COLOR));
        } finally {
            cursor.close();
        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.session_detail_menu_items, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final String shareString;
        final Intent intent;

        switch (item.getItemId()) {
            case R.id.menu_map:
/*                intent = new Intent(getActivity().getApplicationContext(),
                        UIUtils.getMapActivityClass(getActivity()));
                intent.putExtra(MapFragment.EXTRA_ROOM, mRoomId);
                startActivity(intent);
*/				Log.d(TAG, "option item map selected");				
                return true;

            case R.id.menu_share:
                // TODO: consider bringing in shortlink to session
                shareString = getString(R.string.share_template, mTitleString, getHashtagsString(),
                        mUrl);
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, shareString);
                startActivity(Intent.createChooser(intent, getText(R.string.title_share)));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle toggling of starred checkbox.
     */
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final ContentValues values = new ContentValues();
        values.put(ScheduleContract.Sessions.SESSION_STARRED, isChecked ? 1 : 0);
        mHandler.startUpdate(mSessionUri, values);
    }

    /**
     * Build and add "notes" tab.
     */
    private void setupNotesTab() {
        // Setup tab
        mTabHost.addTab(mTabHost.newTabSpec(TAG_NOTES)
                .setIndicator(buildIndicator(R.string.session_notes))
                .setContent(R.id.tab_session_notes));
        
        // WebView will be configured lazily in updateNotesTab() when we have session data
    }
    
    /**
     * Check if a tab uses GeckoView.
     * @param tabId The tab tag identifier
     * @return true if the tab uses GeckoView, false otherwise
     */
    private boolean isGeckoViewTab(String tabId) {
        // Currently only Notes tab uses GeckoView, but more tabs may be added
        return TAG_NOTES.equals(tabId);
    }
    
    /**
     * Check if a GeckoView tab is "deep" (navigation stays within GeckoView) or "shallow" (links open externally).
     * @param tabId The tab tag identifier
     * @return true if the tab is deep (navigation stays within), false if shallow (links open externally)
     */
    private boolean isDeepGeckoViewTab(String tabId) {
        if (TAG_NOTES.equals(tabId)) {
            return true; // Notes tab is deep - navigation stays within GeckoView
        }
        // Add more GeckoView tabs here as they are added
        // Return false for shallow tabs (links open externally)
        return true; // Default to deep for safety
    }
    
    /**
     * Get the GeckoView resource ID for a given tab.
     * @param tabId The tab tag identifier
     * @return The resource ID of the GeckoView for this tab, or 0 if not found
     */
    private int getGeckoViewResourceId(String tabId) {
        if (TAG_NOTES.equals(tabId)) {
            return R.id.notes_webview;
        }
        // Add more GeckoView tabs here as they are added
        return 0;
    }
    
    /**
     * Get the tab content resource ID for a given GeckoView tab.
     * @param tabId The tab tag identifier
     * @return The resource ID of the tab content container, or 0 if not found
     */
    private int getGeckoViewTabContentId(String tabId) {
        if (TAG_NOTES.equals(tabId)) {
            return R.id.tab_session_notes;
        }
        // Add more GeckoView tabs here as they are added
        return 0;
    }
    
    /**
     * Update a GeckoView tab with its URL. This is a generic method that can be called
     * for any GeckoView tab. Currently only handles Notes tab, but can be extended.
     * @param tabId The tab tag identifier
     */
    private void updateGeckoViewTab(String tabId) {
        if (TAG_NOTES.equals(tabId)) {
            updateNotesTab();
        }
        // Add more GeckoView tabs here as they are added
    }
    
    /**
     * Initialize and configure the GeckoView for a specific tab.
     * Generic wrapper that works with any GeckoView tab.
     * Called lazily when we have session data to avoid NullPointerException.
     * @param tabId The tab tag identifier
     */
    private void ensureWebViewInitialized(String tabId) {
        if (!isGeckoViewTab(tabId)) {
            Log.w(TAG, "ensureWebViewInitialized: Tab " + tabId + " is not a GeckoView tab");
            return;
        }
        
        GeckoViewHelper helper = mGeckoViewHelpers.get(tabId);
        if (helper != null && helper.getGeckoView() != null && helper.getGeckoSession() != null) {
            // Already initialized
            return;
        }

        Log.d(TAG, "ensureWebViewInitialized: Initializing GeckoView for tab " + tabId + ", mRootView=" + (mRootView != null));
        
        // Create helper if it doesn't exist
        if (helper == null) {
            boolean isDeep = isDeepGeckoViewTab(tabId);
            helper = new GeckoViewHelper(this, isDeep);
            mGeckoViewHelpers.put(tabId, helper);
        }
        
        // Try multiple ways to find the GeckoView
        GeckoView geckoView = null;
        int geckoViewResId = getGeckoViewResourceId(tabId);
        if (geckoViewResId != 0) {
            geckoView = (GeckoView) mRootView.findViewById(geckoViewResId);
            Log.d(TAG, "ensureWebViewInitialized: Found via findViewById: " + (geckoView != null));
        }

        if (geckoView == null) {
            // Fallback: the included layout's root might be the GeckoView itself
            int tabContentId = getGeckoViewTabContentId(tabId);
            if (tabContentId != 0) {
                View tabContent = mRootView.findViewById(tabContentId);
                Log.d(TAG, "ensureWebViewInitialized: tab content view: " + (tabContent != null) + ", type: " + (tabContent != null ? tabContent.getClass().getName() : "null"));
                if (tabContent instanceof GeckoView) {
                    geckoView = (GeckoView) tabContent;
                    Log.d(TAG, "ensureWebViewInitialized: Found GeckoView as tab content root");
                }
            }
        }

        if (geckoView == null) {
            Log.e(TAG, "Failed to find GeckoView for tab " + tabId);
            return;
        }

        // Initialize helper with the GeckoView
        helper.initialize(geckoView);
    }
    
    /*
     * Event structure:
     * Category -> "Session Details"
     * Action -> "Create Note", "View Note", etc
     * Label -> Session's Title
     * Value -> 0.
     */
    public void fireNotesEvent(int actionId) {
    }

    /*
     * Event structure:
     * Category -> "Session Details"
     * Action -> Link Text
     * Label -> Session's Title
     * Value -> 0.
     */
    public void fireLinkEvent(int actionId) {
    }
    
    /**
     * Helper method to open agenda in AgendaActivity
     */
    private void openAgendaInWebView(String url) {
        Intent intent = new Intent(getActivity(), AgendaActivity.class);
        intent.putExtra(AgendaActivity.EXTRA_AGENDA_URL, url);
        intent.putExtra(Intent.EXTRA_TITLE, mTitleString);
        startActivity(intent);
    }
    
    /**
     * Helper method to create a gradient drawable
     */
    private android.graphics.drawable.GradientDrawable createGradient(int startColor, int endColor, float cornerRadius) {
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {startColor, endColor});
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }
    
    /**
     * Helper method to create a section header with gradient background
     */
    private TextView createSectionHeader(int textResId) {
        TextView header = new TextView(getActivity());
        header.setText(textResId);
        header.setTextSize(14);
        header.setTextColor(0xFFFFFFFF);  // White text
        header.setBackground(createGradient(0xFF888888, 0xFFC0C0C0, 0));  // Gray gradient
        header.setPadding(20, 14, 16, 14);
        header.setTypeface(null, android.graphics.Typeface.ITALIC);
        return header;
    }
    
    /**
     * Helper method to create a separator view
     */
    private View createSeparator() {
        View separator = new ImageView(getActivity());
        separator.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        separator.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
        return separator;
    }
    
    /**
     * Helper method to create a thin separator line
     */
    private View createThinSeparator() {
        View separator = new ImageView(getActivity());
        separator.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        separator.setBackgroundColor(0xFFCCCCCC);
        return separator;
    }
    
    /**
     * Helper method to create Meetecho button
     */
    private TextView createMeetechoButton(final String meetechoUrl) {
        TextView button = new TextView(getActivity());
        button.setText(R.string.session_link_meetecho);
        button.setTextSize(14);
        button.setTextColor(0xFFFFFFFF);  // White text
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(16, 12, 16, 12);
        
        // Green gradient background with rounded corners
        button.setBackground(createGradient(0xFF388E3C, 0xFF66BB6A, 4));
        
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                fireLinkEvent(R.string.session_link_meetecho);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(meetechoUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                startActivity(intent);
            }
        });
        
        android.widget.LinearLayout.LayoutParams buttonParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.leftMargin = 8;   // Small gap between agenda and button
        buttonParams.rightMargin = 16; // Right padding to match left padding of agenda
        button.setLayoutParams(buttonParams);
        
        return button;
    }

    /**
     * Escape HTML special characters to prevent XSS.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private void updateNotesTab() {
        Log.d(TAG, "updateNotesTab: mTitleString=" + mTitleString + ", mRootView=" + (mRootView != null));
        if (mTitleString == null) {
            Log.w(TAG, "updateNotesTab: mTitleString is null, returning");
            return;
        }
        
        // Ensure view is created before initializing
        if (mRootView == null) {
            Log.w(TAG, "updateNotesTab: mRootView is null, returning");
            return;
        }
        
        // Initialize WebView lazily (after view is attached)
        ensureWebViewInitialized(TAG_NOTES);
        
        GeckoViewHelper helper = mGeckoViewHelpers.get(TAG_NOTES);
        if (helper == null || helper.getGeckoView() == null || helper.getGeckoSession() == null) {
            Log.w(TAG, "updateNotesTab: helper=" + (helper != null) + ", geckoView=" + (helper != null && helper.getGeckoView() != null) + ", geckoSession=" + (helper != null && helper.getGeckoSession() != null));
            return;
        }
        
        // Extract group acronym from session title
        // Title format: "{area} - {group} - {title}" or " -{group} - {title}" if area is empty
        // We need to extract the group (middle part)
        String groupAcronym = null;
        if (mTitleString != null && mTitleString.contains(" - ")) {
            // Split on " - " to get parts
            String[] parts = mTitleString.split(" - ", 3);
            Log.d(TAG, "updateNotesTab: Split title into " + parts.length + " parts");
            for (int i = 0; i < parts.length; i++) {
                Log.d(TAG, "updateNotesTab: parts[" + i + "] = '" + parts[i] + "'");
            }
            
            // Group is typically the middle part (index 1)
            // But if area is empty, format is " -{group} - {title}", so parts[0] might be empty
            if (parts.length >= 2) {
                // Use the middle part (index 1) as the group
                groupAcronym = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
                // Remove any leading dash if area was empty
                if (groupAcronym.startsWith("-")) {
                    groupAcronym = groupAcronym.substring(1).trim();
                }
            } else if (parts.length == 1 && mTitleString.startsWith(" -")) {
                // Handle case where area is empty: " -{group} - {title}"
                // Split on " -" (space-dash) instead
                String[] altParts = mTitleString.split(" -", 3);
                if (altParts.length >= 2) {
                    groupAcronym = altParts[1].split(" -", 2)[0].toLowerCase(java.util.Locale.ROOT).trim();
                }
            }
        }
        
        Log.d(TAG, "updateNotesTab: Extracted groupAcronym='" + groupAcronym + "' from title='" + mTitleString + "'");
        
        // Construct HedgeDoc URL: https://notes.ietf.org/notes-ietf-{meetingNumber}-{groupAcronym}?view
        // Note: HedgeDoc may redirect to home page if the specific notes page doesn't exist
        if (groupAcronym != null && !groupAcronym.isEmpty()) {
            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(getActivity());
            final String hedgedocUrl = "https://notes.ietf.org/notes-ietf-" + meetingNumber + "-" + groupAcronym + "?view";
            
            // Only load if URL has changed AND Notes tab is currently active
            // This prevents adding unnecessary entries to GeckoView history when star is toggled
            boolean notesTabActive = mTabHost != null && TAG_NOTES.equals(mTabHost.getCurrentTabTag());
            String lastUrl = mGeckoViewTabUrls.get(TAG_NOTES);
            boolean isFirstLoad = (lastUrl == null);
            if (!hedgedocUrl.equals(lastUrl)) {
                Log.d(TAG, "updateNotesTab: URL changed, notesTabActive=" + notesTabActive + ", isFirstLoad=" + isFirstLoad);
                // Only load if Notes tab is active or if this is the first load
                // This prevents reloading when star is toggled and Notes tab isn't visible
                if (notesTabActive || isFirstLoad) {
                    Log.d(TAG, "updateNotesTab: Loading URL: " + hedgedocUrl);
                    // helper is already defined earlier in the method
                    if (helper != null) {
                        helper.loadUrl(hedgedocUrl);
                        mGeckoViewTabUrls.put(TAG_NOTES, hedgedocUrl);
                        // Track this as the initial URL for this tab (will be used to determine if we need to re-initialize)
                        mGeckoViewInitialUrls.put(TAG_NOTES, hedgedocUrl);
                    }
                } else {
                    Log.d(TAG, "updateNotesTab: Skipping load - Notes tab not active, will load when tab is opened");
                    // Store URL for later loading when Notes tab is opened
                    mGeckoViewTabUrls.put(TAG_NOTES, hedgedocUrl);
                }
            } else {
                Log.d(TAG, "updateNotesTab: URL unchanged, skipping reload: " + hedgedocUrl);
            }
        } else {
            Log.w(TAG, "updateNotesTab: groupAcronym is null or empty, not loading URL");
            mGeckoViewTabUrls.remove(TAG_NOTES);
            mGeckoViewInitialUrls.remove(TAG_NOTES);
        }
    }

    /**
     * Build and add "links" tab.
     */
    private void setupLinksTab() {
        // Links content comes from existing layout
        mTabHost.addTab(mTabHost.newTabSpec(TAG_LINKS)
                .setIndicator(buildIndicator(R.string.session_links))
                .setContent(R.id.tab_session_links));
    }

    /**
     * Build and add "join" tab.
     */
    private void setupJoinTab() {
        // Join content comes from existing layout
        // Use green tab indicator to match "Join Meeting" button color
        mTabHost.addTab(mTabHost.newTabSpec(TAG_JOIN)
                .setIndicator(buildIndicator(R.string.session_join, R.drawable.tab_indicator_green))
                .setContent(R.id.tab_session_join));
    }

    /**
     * Updates the Links tab with session links, agendas, and presentation slides.
     * 
     * Special handling for different link types:
     * - PDF_URL: Can contain multiple slides (separated by "::"), shown with section header
     * - SESSION_URL (Agenda): Paired with Meetecho button on the same row
     * - Other links: Displayed as standard clickable items with separators
     */
    private void updateLinksTab(Cursor cursor) {
        ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.links_container);

        // Remove all views but the 'empty' view
        int childCount = container.getChildCount();
        if (childCount > 1) {
            container.removeViews(1, childCount - 1);
        }

        LayoutInflater inflater = getLayoutInflater(null);

        boolean hasLinks = false;
        
        // Process each link type defined in SessionsQuery
        // Skip PDF_URL (presentation slides) - those are now in Summary tab
        for (int i = 0; i < SessionsQuery.LINKS_INDICES.length; i++) {
            // Skip PDF_URL - moved to Summary tab
            if (SessionsQuery.LINKS_INDICES[i] == SessionsQuery.PDF_URL) {
                continue;
            }
            
            final String url = cursor.getString(SessionsQuery.LINKS_INDICES[i]);
            if (!TextUtils.isEmpty(url)) {
                hasLinks = true;
                
                // Special handling for Agenda link (SESSION_URL) - place Meetecho button on same row
                if (SessionsQuery.LINKS_INDICES[i] == SessionsQuery.SESSION_URL) {
                    // Special handling for Agenda link (SESSION_URL) - place Meetecho button on same row
                    if (SessionsQuery.LINKS_INDICES[i] == SessionsQuery.SESSION_URL) {
                        // Extract group acronym from session title (format: "area - group - title")
                        String groupAcronym = null;
                        if (mTitleString != null && mTitleString.contains(" - ")) {
                            String[] parts = mTitleString.split(" - ", 3);
                            if (parts.length >= 2) {
                                groupAcronym = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
                            }
                        }
                        
                        if (groupAcronym != null && !groupAcronym.isEmpty()) {
                            // Create horizontal container for Agenda link + Meetecho button
                            android.widget.LinearLayout horizontalContainer = new android.widget.LinearLayout(getActivity());
                            horizontalContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                            horizontalContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);  // Vertically center all children
                            horizontalContainer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                            
                            // Add Agenda link on the left
                            ViewGroup linkContainer = (ViewGroup)
                                    inflater.inflate(R.layout.list_item_session_link, container, false);
                            ((TextView) linkContainer.findViewById(R.id.link_text)).setText(
                                    SessionsQuery.LINKS_TITLES[i]);
                            final int linkTitleIndex = i;
                            linkContainer.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View view) {
                                    fireLinkEvent(SessionsQuery.LINKS_TITLES[linkTitleIndex]);
                                    openAgendaInWebView(url);
                                }
                            });
                            
                            android.widget.LinearLayout.LayoutParams agendaParams = new android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                            linkContainer.setLayoutParams(agendaParams);
                            horizontalContainer.addView(linkContainer);
                            
                            // Add Meetecho button on the right
                            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(getActivity());
                            final String meetechoUrl = "https://meetings.conf.meetecho.com/onsite" + meetingNumber + "/?group=" + groupAcronym;
                            horizontalContainer.addView(createMeetechoButton(meetechoUrl));
                            
                            container.addView(horizontalContainer);
                            container.addView(createSeparator());
                        } else {
                            // No group acronym, just add agenda link normally
                            ViewGroup linkContainer = (ViewGroup)
                                    inflater.inflate(R.layout.list_item_session_link, container, false);
                            ((TextView) linkContainer.findViewById(R.id.link_text)).setText(
                                    SessionsQuery.LINKS_TITLES[i]);
                            final int linkTitleIndex = i;
                            linkContainer.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View view) {
                                    fireLinkEvent(SessionsQuery.LINKS_TITLES[linkTitleIndex]);
                                    openAgendaInWebView(url);
                                }
                            });

                            container.addView(linkContainer);
                            container.addView(createSeparator());
                        }
                    } else {
                        // Normal handling for other link types (single URL)
                        ViewGroup linkContainer = (ViewGroup)
                                inflater.inflate(R.layout.list_item_session_link, container, false);
                        ((TextView) linkContainer.findViewById(R.id.link_text)).setText(
                                SessionsQuery.LINKS_TITLES[i]);
                        final int linkTitleIndex = i;
                        linkContainer.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View view) {
                                fireLinkEvent(SessionsQuery.LINKS_TITLES[linkTitleIndex]);
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                                startActivity(intent);
                            }
                        });

                        container.addView(linkContainer);
                        container.addView(createSeparator());
                    }
                }
            }
        }

        container.findViewById(R.id.empty_links).setVisibility(hasLinks ? View.GONE : View.VISIBLE);
    }

    private String getHashtagsString() {
/*        if (!TextUtils.isEmpty(mHashtag)) {
            return TagStreamFragment.CONFERENCE_HASHTAG + " #" + mHashtag;
        } else {
            return TagStreamFragment.CONFERENCE_HASHTAG;
       }
*/	
		return ""; 
   }

    private BroadcastReceiver mPackageChangesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNotesTab();
        }
    };
    /**
     * {@link org.ietf.ietfsched.provider.ScheduleContract.Sessions} query parameters.
     */
    private interface SessionsQuery {
        int _TOKEN = 0x1;

        String[] PROJECTION = {
                ScheduleContract.Blocks.BLOCK_START,
                ScheduleContract.Blocks.BLOCK_END,
                ScheduleContract.Sessions.SESSION_LEVEL,
                ScheduleContract.Sessions.SESSION_TITLE,
                ScheduleContract.Sessions.SESSION_ABSTRACT,
                ScheduleContract.Sessions.SESSION_REQUIREMENTS,
                ScheduleContract.Sessions.SESSION_STARRED,
                ScheduleContract.Sessions.SESSION_HASHTAG,
                ScheduleContract.Sessions.SESSION_SLUG,
                ScheduleContract.Sessions.SESSION_URL,
                ScheduleContract.Sessions.SESSION_MODERATOR_URL,
                ScheduleContract.Sessions.SESSION_YOUTUBE_URL,
                ScheduleContract.Sessions.SESSION_PDF_URL,
                ScheduleContract.Sessions.SESSION_DRAFTS_URL,
                ScheduleContract.Sessions.SESSION_RES_URI,
                ScheduleContract.Sessions.SESSION_FEEDBACK_URL,
                ScheduleContract.Sessions.SESSION_NOTES_URL,
                ScheduleContract.Sessions.ROOM_ID,
                ScheduleContract.Rooms.ROOM_NAME,
        };

        int BLOCK_START = 0;
        int BLOCK_END = 1;
        // int LEVEL = 2;
        int TITLE = 3;
        int ABSTRACT = 4;
        // int REQUIREMENTS = 5;
        int STARRED = 6;
        int HASHTAG = 7;
        // int SLUG = 8;
        int SESSION_URL = 9;
        int MODERATOR_URL = 10;
        int YOUTUBE_URL = 11;
        int PDF_URL = 12;
        int DRAFTS_URL = 13;
        int RES_URI = 14;
        int FEEDBACK_URL = 15;
        int NOTES_URL = 16;
        int ROOM_ID = 17;
        int ROOM_NAME = 18;

        int[] LINKS_INDICES = {
                SESSION_URL,
                YOUTUBE_URL,
                PDF_URL,
                FEEDBACK_URL,
                NOTES_URL,
        };

        int[] LINKS_TITLES = {
                R.string.session_link_main,
                R.string.session_link_youtube,
                R.string.session_link_pdf,
                R.string.session_link_feedback,
                R.string.session_link_notes,
        };
    }

    /**
     * {@link org.ietf.ietfsched.provider.ScheduleContract.Tracks} query parameters.
     */
    private interface TracksQuery {
        int _TOKEN = 0x2;

        String[] PROJECTION = {
                ScheduleContract.Tracks.TRACK_NAME,
                ScheduleContract.Tracks.TRACK_COLOR,
        };

        int TRACK_NAME = 0;
        int TRACK_COLOR = 1;
    }

    private interface SpeakersQuery {
        int _TOKEN = 0x3;

        String[] PROJECTION = {
                ScheduleContract.Speakers.SPEAKER_NAME,
                ScheduleContract.Speakers.SPEAKER_IMAGE_URL,
                ScheduleContract.Speakers.SPEAKER_COMPANY,
                ScheduleContract.Speakers.SPEAKER_ABSTRACT,
                ScheduleContract.Speakers.SPEAKER_URL,
        };

        int SPEAKER_NAME = 0;
        int SPEAKER_IMAGE_URL = 1;
        int SPEAKER_COMPANY = 2;
        int SPEAKER_ABSTRACT = 3;
        int SPEAKER_URL = 4;
    }
    
    /**
     * Re-initialize GeckoView for a specific tab by closing the old session and creating a new one.
     * This clears the navigation history, ensuring a fresh start when the URL changes.
     * @param tabId The tab tag identifier
     */
    private void reinitializeGeckoView(String tabId) {
        if (!isGeckoViewTab(tabId)) {
            Log.w(TAG, "reinitializeGeckoView: Tab " + tabId + " is not a GeckoView tab");
            return;
        }
        
        GeckoViewHelper helper = mGeckoViewHelpers.get(tabId);
        if (helper != null) {
            helper.reinitialize();
        }
        
        // Clear initial URL for this tab since we're starting fresh
        mGeckoViewInitialUrls.remove(tabId);
        
        // Re-initialize GeckoView (will create new session)
        ensureWebViewInitialized(tabId);
    }
    
    /**
     * Handle back button press - navigate back in GeckoView if a GeckoView tab is active and can go back.
     * Otherwise, allow the activity to finish (exit the session).
     * @return true if GeckoView handled the back press, false to allow activity to finish
     */
    public boolean onBackPressed() {
        String currentTab = mTabHost != null ? mTabHost.getCurrentTabTag() : null;
        boolean isGeckoTab = isGeckoViewTab(currentTab);
        
        GeckoViewHelper helper = currentTab != null ? mGeckoViewHelpers.get(currentTab) : null;
        boolean canGoBack = helper != null && helper.canGoBack();
        
        Log.d(TAG, "onBackPressed: currentTab=" + currentTab + ", isGeckoViewTab=" + isGeckoTab + ", helper=" + (helper != null) + ", canGoBack=" + canGoBack);
        
        // Only handle back navigation within GeckoView if:
        // 1. A GeckoView tab is currently selected
        // 2. GeckoViewHelper exists for that tab
        // 3. GeckoView can actually go back
        if (mTabHost != null && isGeckoTab && helper != null && canGoBack) {
            return helper.onBackPressed();
        }
        
        // Otherwise, allow activity to finish (exit session)
        Log.d(TAG, "onBackPressed: Returning false - will exit session (tab=" + currentTab + ", isGeckoViewTab=" + isGeckoTab + ", helper=" + (helper != null) + ", canGoBack=" + canGoBack + ")");
        return false;
    }
}
