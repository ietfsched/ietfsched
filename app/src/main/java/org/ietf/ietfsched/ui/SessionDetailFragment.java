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
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

    private static final String TAG_CONTENT = "content";
    private static final String TAG_NOTES = "notes";
    private static final String TAG_AGENDA = "agenda";
    private static final String TAG_JOIN = "join";

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    private String mSessionId;
    private Uri mSessionUri;

    private String mTitleString;
    private String mHashtag;
    private String mUrl;
    private String mRoomId;

    private ViewGroup mRootView;
    private TabHost mTabHost;
    private TextView mTitle;
    private TextView mSubtitle;
    private CompoundButton mStarred;
    

    // Single shared GeckoView instance (singleton pattern)
    // Key: tab tag (e.g., TAG_NOTES), Value: GeckoViewHelper instance for that tab
    // Each helper manages its own GeckoSession, but they all share the same GeckoView instance
    private java.util.Map<String, GeckoViewHelper> mGeckoViewHelpers = new java.util.HashMap<String, GeckoViewHelper>();
    private GeckoView mSharedGeckoView; // Single GeckoView instance shared across tabs
    private String mCurrentTabTag; // Retained tab selection
    // Track last loaded URL per GeckoView tab to avoid reloading unnecessarily
    // Key: tab tag (e.g., TAG_NOTES), Value: last URL that should be loaded for that tab
    private java.util.Map<String, String> mGeckoViewTabUrls = new java.util.HashMap<String, String>();
    // Track the initial URL for each GeckoView tab to determine if we're at the "root" of navigation
    // Key: tab tag (e.g., TAG_NOTES), Value: initial URL that was loaded for that tab
    private java.util.Map<String, String> mGeckoViewInitialUrls = new java.util.HashMap<String, String>();

    private NotifyingAsyncQueryHandler mHandler;
    private RemoteExecutor mRemoteExecutor;
    
    // Helper classes for tab management
    private SessionContentTabBuilder mContentTabBuilder;
    private SessionAgendaTabManager mAgendaTabManager;
    private SessionNotesTabManager mNotesTabManager;
    private SessionJoinTabManager mJoinTabManager;
    private SessionDraftFetcher mDraftFetcher;

    private boolean mSessionCursor = false;
    private boolean mSpeakersCursor = false;
    private boolean mHasSummaryContent = false;
    private boolean mPendingStarredUpdate = false; // Track if a starred update is in progress


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
        if (mNotesTabManager != null && mTitleString != null) {
            mNotesTabManager.updateNotesTab(mTitleString, mSharedGeckoView);
        }

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
        
        // Set up update listener to track database updates for testing
        // This allows IdlingResources in tests to wait for updates to complete
        mHandler.setUpdateListener(new NotifyingAsyncQueryHandler.AsyncUpdateListener() {
            @Override
            public void onUpdateComplete(int token, Object cookie, int result) {
                // Clear pending flag when update completes
                // Note: We don't re-query here because the UI is already correct (user clicked it)
                // and re-querying immediately could cause race conditions if the database transaction
                // hasn't fully committed yet. The UI will be refreshed naturally when the next
                // query runs (e.g., from a sync or fragment refresh).
                mPendingStarredUpdate = false;
                Log.d(TAG, "onUpdateComplete: token=" + token + ", result=" + result + ", sessionId=" + mSessionId);
            }
        });
        
        // Initialize draft fetcher now that RemoteExecutor is ready
        initializeDraftFetcher();
        
        mHandler.startQuery(SessionsQuery._TOKEN, mSessionUri, SessionsQuery.PROJECTION);
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
                if (TAG_CONTENT.equals(tabId) && mDraftFetcher != null && mSessionId != null) {
                    mDraftFetcher.fetchDraftsOnDemand();
                }
                // Handle GeckoView tab switching: preserve state if URL unchanged, re-initialize if URL changed
                if (isGeckoViewTab(tabId)) {
                    // Use post to ensure TabHost has finished its visibility management
                    mTabHost.post(new Runnable() {
                        @Override
                        public void run() {
                            handleGeckoViewTabChange(tabId);
                            // After switching, trigger update to ensure URL loads if it was stored but not loaded
                            if (TAG_JOIN.equals(tabId) && mJoinTabManager != null && mTitleString != null) {
                                mJoinTabManager.updateJoinTab(mTitleString, mSharedGeckoView);
                            }
                        }
                    });
                }
                // Handle Agenda tab (WebView) - trigger update if needed
                if (TAG_AGENDA.equals(tabId) && mAgendaTabManager != null && mUrl != null) {
                    mTabHost.post(new Runnable() {
                        @Override
                        public void run() {
                            mAgendaTabManager.updateAgendaTab(mUrl);
                        }
                    });
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

        setupAgendaTab();
        setupJoinTab();
        setupContentTab();
        setupNotesTab();
        
        // Create single shared GeckoView instance (singleton pattern)
        createSharedGeckoView();
        
        // Initialize helper classes after views are created
        initializeHelpers();

        return mRootView;
    }
    
    /**
     * Create a single shared GeckoView instance that will be moved between tab containers.
     * This implements the singleton pattern for GeckoView.
     */
    private void createSharedGeckoView() {
        if (mSharedGeckoView != null) {
            Log.d(TAG, "createSharedGeckoView: Shared GeckoView already exists");
            return;
        }
        
        android.app.Activity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "createSharedGeckoView: Activity is null, cannot create GeckoView");
            return;
        }
        
        try {
            mSharedGeckoView = new GeckoView(activity);
            mSharedGeckoView.setId(android.view.View.generateViewId());
            mSharedGeckoView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            mSharedGeckoView.setFocusable(true);
            mSharedGeckoView.setFocusableInTouchMode(true);
            mSharedGeckoView.setClickable(true);
            Log.d(TAG, "createSharedGeckoView: Created singleton GeckoView instance");
        } catch (Exception e) {
            Log.e(TAG, "createSharedGeckoView: Failed to create GeckoView", e);
            mSharedGeckoView = null;
        }
    }
    
    /**
     * Initialize helper classes for tab management.
     */
    private void initializeHelpers() {
        mContentTabBuilder = new SessionContentTabBuilder(this, mRootView, 
            new Runnable() { public void run() { fireLinkEvent(R.string.session_link_pdf); } });
        mAgendaTabManager = new SessionAgendaTabManager(this, mRootView, mTabHost);
        mNotesTabManager = new SessionNotesTabManager(this, mRootView, mTabHost,
            mGeckoViewHelpers, mGeckoViewTabUrls, mGeckoViewInitialUrls);
        mJoinTabManager = new SessionJoinTabManager(this, mRootView, mTabHost,
            mGeckoViewHelpers, mGeckoViewTabUrls, mGeckoViewInitialUrls);
        
        // mDraftFetcher will be initialized in onActivityCreated() after mRemoteExecutor is ready
    }
    
    /**
     * Initialize draft fetcher after RemoteExecutor is ready.
     */
    private void initializeDraftFetcher() {
        if (mDraftFetcher == null && mRemoteExecutor != null && mSessionUri != null) {
            mDraftFetcher = new SessionDraftFetcher(this, mSessionUri, mRemoteExecutor,
                new Runnable() {
                    public void run() {
                        // Re-query to refresh the Content tab
                        if (mHandler != null && mSessionUri != null) {
                            mHandler.startQuery(SessionsQuery._TOKEN, mSessionUri, SessionsQuery.PROJECTION);
                        }
                    }
                });
        }
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
        if (mContentTabBuilder == null) {
            Log.w(TAG, "updateContentTab: ContentTabBuilder not initialized");
            return;
        }
        mContentTabBuilder.updateContentTab(cursor, SessionsQuery.PDF_URL, SessionsQuery.DRAFTS_URL);
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
        return buildIndicator(textRes, backgroundRes, 0);
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label, a custom background drawable, and optionally a color tint.
     *
     * @param textRes
     * @param backgroundRes
     * @param colorTint Color to tint the drawable (0 for no tint)
     * @return View
     */
    private View buildIndicator(int textRes, int backgroundRes, int colorTint) {
        final TextView indicator = (TextView) getActivity().getLayoutInflater()
                .inflate(R.layout.tab_indicator,
                        (ViewGroup) mRootView.findViewById(android.R.id.tabs), false);
        indicator.setText(textRes);
        indicator.setBackgroundResource(backgroundRes);
        if (colorTint != 0) {
            Drawable background = indicator.getBackground();
            if (background != null) {
                background.setColorFilter(new PorterDuffColorFilter(colorTint, PorterDuff.Mode.SRC_ATOP));
            }
        }
        return indicator;
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
            // However, don't overwrite the checkbox if there's a pending update
            // to prevent race conditions where a user click gets overwritten.
            if (!mPendingStarredUpdate) {
                mStarred.setOnCheckedChangeListener(null);
                mStarred.setChecked(cursor.getInt(SessionsQuery.STARRED) != 0);
                mStarred.setOnCheckedChangeListener(this);
            } else {
                Log.d(TAG, "onSessionQueryComplete: Skipping checkbox update due to pending starred update");
            }

            updateAgendaTab(cursor);
            updateContentTab(cursor);
            // Ensure shared GeckoView exists before updating Notes and Join tabs
            if (mSharedGeckoView == null) {
                createSharedGeckoView();
            }
            if (mNotesTabManager != null && mTitleString != null) {
                mNotesTabManager.updateNotesTab(mTitleString, mSharedGeckoView);
                // Initialize with shared GeckoView if not already initialized
                if (mSharedGeckoView != null) {
                    mNotesTabManager.initializeGeckoView(mSharedGeckoView);
                }
            }
            if (mJoinTabManager != null && mTitleString != null) {
                mJoinTabManager.updateJoinTab(mTitleString, mSharedGeckoView);
                // Initialize with shared GeckoView if not already initialized
                if (mSharedGeckoView != null) {
                    mJoinTabManager.initializeGeckoView(mSharedGeckoView);
                }
            }

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
        Log.d(TAG, "onCheckedChanged: isChecked=" + isChecked + ", sessionId=" + mSessionId);
        // Mark that we have a pending update to prevent race conditions
        mPendingStarredUpdate = true;
        final ContentValues values = new ContentValues();
        values.put(ScheduleContract.Sessions.SESSION_STARRED, isChecked ? 1 : 0);
        mHandler.startUpdate(mSessionUri, values);
        Log.d(TAG, "onCheckedChanged: Started database update");
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
        return TAG_NOTES.equals(tabId) || TAG_JOIN.equals(tabId);
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
        if (TAG_AGENDA.equals(tabId)) {
            return false; // Agenda tab is shallow - links open externally
        }
        if (TAG_JOIN.equals(tabId)) {
            return true; // Join tab is deep - navigation stays within GeckoView
        }
        return true; // Default to deep for safety
    }
    
    /**
     * Generic method to handle GeckoView tab switching using singleton GeckoView.
     * 
     * Strategy: Use a single GeckoView instance and move it between tab containers.
     * This ensures only one GeckoView exists and only one session is attached at a time.
     * 
     * @param activeTabId The tab ID that is becoming active
     */
    private void switchGeckoViewTab(String activeTabId) {
        Log.d(TAG, "switchGeckoViewTab: Switching to tab " + activeTabId + " using singleton GeckoView");
        
        // Ensure shared GeckoView exists
        if (mSharedGeckoView == null) {
            createSharedGeckoView();
        }
        
        if (mSharedGeckoView == null) {
            Log.e(TAG, "switchGeckoViewTab: Failed to create shared GeckoView");
            return;
        }
        
        // Step 1: Get target container for active tab
        ViewGroup targetContainer = getContainerForTab(activeTabId);
        if (targetContainer == null) {
            Log.w(TAG, "switchGeckoViewTab: Could not find container for tab " + activeTabId);
            return;
        }
        
        // Step 2: Add GeckoView to target container first (must be attached before setting session)
        // But first, check if target container has a WebView (from Agenda tab) - remove it
        if (targetContainer != null && targetContainer.getChildCount() > 0) {
            View firstChild = targetContainer.getChildAt(0);
            if (firstChild instanceof android.webkit.WebView) {
                Log.d(TAG, "switchGeckoViewTab: Removing WebView from target container before adding GeckoView");
                targetContainer.removeView(firstChild);
            }
        }
        
        if (mSharedGeckoView.getParent() != targetContainer) {
            // Remove from current parent if exists
            ViewGroup oldParent = (ViewGroup) mSharedGeckoView.getParent();
            if (oldParent != null) {
                Log.d(TAG, "switchGeckoViewTab: Removing GeckoView from current parent");
                oldParent.removeView(mSharedGeckoView);
            }
            Log.d(TAG, "switchGeckoViewTab: Adding GeckoView to " + activeTabId + " container");
            targetContainer.addView(mSharedGeckoView);
        }
        
        // Step 3: Get the active tab's helper and session
        GeckoViewHelper activeHelper = mGeckoViewHelpers.get(activeTabId);
        if (activeHelper == null) {
            Log.w(TAG, "switchGeckoViewTab: No helper found for active tab " + activeTabId);
            return;
        }
        
        // Step 4: Check if we need to switch sessions (only if different)
        GeckoSession currentSession = mSharedGeckoView.getSession();
        GeckoSession activeSession = activeHelper.getGeckoSession();
        
        // Get or create the active session if needed
        if (activeSession == null) {
            Log.d(TAG, "switchGeckoViewTab: Session not initialized for tab " + activeTabId + ", initializing");
            initializeTabManager(activeTabId);
            activeSession = activeHelper.getGeckoSession();
        }
        
        // Only proceed if we have a valid active session
        if (activeSession == null) {
            Log.w(TAG, "switchGeckoViewTab: Could not get or create session for tab " + activeTabId);
            return;
        }
        
        // Only detach/reattach if sessions are different
        if (currentSession != activeSession) {
            // Update helper to use shared GeckoView first
            if (activeHelper.getGeckoView() != mSharedGeckoView) {
                Log.d(TAG, "switchGeckoViewTab: Updating helper to use shared GeckoView");
                activeHelper.initialize(mSharedGeckoView);
                // Re-check session after initialization
                activeSession = activeHelper.getGeckoSession();
                if (activeSession == null) {
                    Log.w(TAG, "switchGeckoViewTab: Session still null after initialization");
                    return;
                }
            }
            
            // Detach current session only if it's different (after helper is initialized)
            if (currentSession != null && mSharedGeckoView != null) {
                try {
                    Log.d(TAG, "switchGeckoViewTab: Detaching current session from shared GeckoView");
                    // Check if GeckoView still has a valid session before detaching
                    if (mSharedGeckoView.getSession() == currentSession) {
                        mSharedGeckoView.setSession(null);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "switchGeckoViewTab: Error detaching session, continuing anyway", e);
                }
            }
            
            // Attach active session to shared GeckoView
            try {
                Log.d(TAG, "switchGeckoViewTab: Attaching " + activeTabId + " session to shared GeckoView");
                mSharedGeckoView.setSession(activeSession);
            } catch (Exception e) {
                Log.e(TAG, "switchGeckoViewTab: Error attaching session", e);
            }
        } else {
            // Sessions are the same - just ensure helper is updated
            if (activeHelper.getGeckoView() != mSharedGeckoView) {
                Log.d(TAG, "switchGeckoViewTab: Updating helper to use shared GeckoView (session already attached)");
                activeHelper.initialize(mSharedGeckoView);
            }
        }
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
     * Build and add "agenda" tab.
     */
    private void setupAgendaTab() {
        // Agenda content comes from GeckoView layout
        mTabHost.addTab(mTabHost.newTabSpec(TAG_AGENDA)
                .setIndicator(buildIndicator(R.string.session_agenda))
                .setContent(R.id.tab_session_links));
    }

    /**
     * Build and add "join" tab.
     */
    private void setupJoinTab() {
        // Join content comes from existing layout
        // Use lighter green tint (#66BB6A) for better text readability
        int greenColor = 0xFF66BB6A;
        mTabHost.addTab(mTabHost.newTabSpec(TAG_JOIN)
                .setIndicator(buildIndicator(R.string.session_join, R.drawable.tab_indicator, greenColor))
                .setContent(R.id.tab_session_join));
    }

    /**
     * Updates the Agenda tab with the agenda URL.
     */
    private void updateAgendaTab(Cursor cursor) {
        if (mAgendaTabManager == null) {
            Log.w(TAG, "updateAgendaTab: AgendaTabManager not initialized");
            return;
        }
        String agendaUrl = cursor.getString(SessionsQuery.SESSION_URL);
        // Always call updateAgendaTab - it will show loading message if URL is null/empty
        mAgendaTabManager.updateAgendaTab(agendaUrl != null ? agendaUrl : "");
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
            if (mNotesTabManager != null && mTitleString != null) {
                mNotesTabManager.updateNotesTab(mTitleString);
            }
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
     * Handle GeckoView tab change: preserve state if URL unchanged, re-initialize if URL changed.
     * @param tabId The tab ID that is becoming active
     */
    private void handleGeckoViewTabChange(String tabId) {
        String expectedUrl = mGeckoViewTabUrls.get(tabId);
        String initialUrl = mGeckoViewInitialUrls.get(tabId);
        
        // If URL has changed (e.g., different session) or initialUrl is null, re-initialize GeckoView
        // Otherwise, preserve navigation state
        if (expectedUrl != null && (initialUrl == null || !expectedUrl.equals(initialUrl))) {
            Log.d(TAG, "handleGeckoViewTabChange: URL changed for GeckoView tab " + tabId + " (expected=" + expectedUrl + ", initial=" + initialUrl + "), re-initializing");
            reinitializeGeckoView(tabId);
            // Load the new URL
            GeckoViewHelper helper = mGeckoViewHelpers.get(tabId);
            if (helper != null) {
                Log.d(TAG, "handleGeckoViewTabChange: Loading new URL for GeckoView tab " + tabId + ": " + expectedUrl);
                helper.loadUrl(expectedUrl);
                mGeckoViewInitialUrls.put(tabId, expectedUrl);
            }
        } else if (expectedUrl == null) {
            // No URL stored yet, trigger update to construct and load URL
            Log.d(TAG, "handleGeckoViewTabChange: No URL stored for GeckoView tab " + tabId + ", triggering update");
            ensureSharedGeckoViewInContainer(tabId);
            updateTabManagerForTab(tabId);
        } else {
            // URL unchanged, preserve navigation state - ensure correct GeckoView is active
            Log.d(TAG, "handleGeckoViewTabChange: URL unchanged for GeckoView tab " + tabId + " (URL=" + expectedUrl + "), preserving navigation state");
            switchGeckoViewTab(tabId);
        }
    }
    
    /**
     * Ensure shared GeckoView exists and is in the correct container for the given tab.
     * @param tabId The tab ID
     */
    private void ensureSharedGeckoViewInContainer(String tabId) {
        if (mSharedGeckoView == null) {
            createSharedGeckoView();
        }
        
        ViewGroup targetContainer = getContainerForTab(tabId);
        if (targetContainer != null && mSharedGeckoView != null && mSharedGeckoView.getParent() != targetContainer) {
            ViewGroup currentParent = (ViewGroup) mSharedGeckoView.getParent();
            if (currentParent != null) {
                currentParent.removeView(mSharedGeckoView);
            }
            targetContainer.addView(mSharedGeckoView);
        }
    }
    
    /**
     * Update the tab manager for the given tab to construct and load URL.
     * @param tabId The tab ID
     */
    private void updateTabManagerForTab(String tabId) {
        if (TAG_NOTES.equals(tabId) && mNotesTabManager != null && mTitleString != null) {
            mNotesTabManager.updateNotesTab(mTitleString, mSharedGeckoView);
            if (mSharedGeckoView != null) {
                mNotesTabManager.initializeGeckoView(mSharedGeckoView);
            }
        } else if (TAG_AGENDA.equals(tabId) && mAgendaTabManager != null && mUrl != null) {
            mAgendaTabManager.updateAgendaTab(mUrl);
        } else if (TAG_JOIN.equals(tabId) && mJoinTabManager != null && mTitleString != null) {
            mJoinTabManager.updateJoinTab(mTitleString, mSharedGeckoView);
            if (mSharedGeckoView != null) {
                mJoinTabManager.initializeGeckoView(mSharedGeckoView);
            }
        }
    }
    
    /**
     * Get the container ViewGroup for a given tab ID.
     * @param tabId The tab ID
     * @return The container ViewGroup, or null if not found
     */
    private ViewGroup getContainerForTab(String tabId) {
        if (TAG_NOTES.equals(tabId)) {
            return (ViewGroup) mRootView.findViewById(R.id.tab_session_notes);
        } else if (TAG_AGENDA.equals(tabId)) {
            return (ViewGroup) mRootView.findViewById(R.id.tab_session_links);
        } else if (TAG_JOIN.equals(tabId)) {
            return (ViewGroup) mRootView.findViewById(R.id.tab_session_join);
        }
        return null;
    }
    
    /**
     * Initialize the tab manager for a given tab ID.
     * @param tabId The tab ID
     */
    private void initializeTabManager(String tabId) {
        if (TAG_NOTES.equals(tabId) && mNotesTabManager != null) {
            mNotesTabManager.initializeGeckoView(mSharedGeckoView);
        } else if (TAG_JOIN.equals(tabId) && mJoinTabManager != null) {
            mJoinTabManager.initializeGeckoView(mSharedGeckoView);
        }
        // Agenda tab uses WebView, not GeckoView - no initialization needed here
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
        initializeTabManager(tabId);
    }
    
    /**
     * Handle back button press - navigate back in GeckoView if a GeckoView tab is active and can go back.
     * Otherwise, allow the activity to finish (exit the session).
     * @return true if GeckoView handled the back press, false to allow activity to finish
     */
    public boolean onBackPressed() {
        String currentTab = mTabHost != null ? mTabHost.getCurrentTabTag() : null;
        
        // Handle Agenda tab (WebView) back button
        if (TAG_AGENDA.equals(currentTab) && mAgendaTabManager != null) {
            if (mAgendaTabManager.onBackPressed()) {
                return true; // WebView handled it
            }
        }
        
        // Handle GeckoView tabs
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
