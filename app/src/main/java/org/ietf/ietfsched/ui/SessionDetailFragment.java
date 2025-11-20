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
import org.ietf.ietfsched.util.ActivityHelper;
import org.ietf.ietfsched.util.FractionalTouchDelegate;
import org.ietf.ietfsched.util.GeckoRuntimeHelper;
import org.ietf.ietfsched.util.NotifyingAsyncQueryHandler;
import org.ietf.ietfsched.util.UIUtils;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
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

    private static final String TAG_SUMMARY = "summary";
    private static final String TAG_NOTES = "notes";
    private static final String TAG_LINKS = "links";

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    private String mSessionId;
    private Uri mSessionUri;
    private Uri mTrackUri;

    private String mTitleString;
    private String mHashtag;
    private String mUrl;
    private TextView mTagDisplay;
    private String mRoomId;

    private ViewGroup mRootView;
    private TabHost mTabHost;
    private TextView mTitle;
    private TextView mSubtitle;
    private CompoundButton mStarred;
    
    private TextView mAbstract;
    private TextView mRequirements;

    private GeckoView mNotesWebView;
    private GeckoSession mGeckoSession;
    private String mCurrentTabTag; // Retained tab selection

    private NotifyingAsyncQueryHandler mHandler;

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
        Log.d(TAG, "mSessionUri: " + mSessionUri);
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
        
        // Set up tab change listener to track current tab
        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                mCurrentTabTag = tabId;
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

        mAbstract = (TextView) mRootView.findViewById(R.id.session_abstract);
//        mRequirements = (TextView) mRootView.findViewById(R.id.session_requirements);

        setupLinksTab();
        setupNotesTab();			
        setupSummaryTab();

        return mRootView;
    }

    /**
     * Build and add "summary" tab.
     */
    private void setupSummaryTab() {
        // Summary content comes from existing layout
        mTabHost.addTab(mTabHost.newTabSpec(TAG_SUMMARY)
                .setIndicator(buildIndicator(R.string.session_summary))
                .setContent(R.id.tab_session_summary));
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     *
     * @param textRes
     * @return View
     */
    private View buildIndicator(int textRes) {
        final TextView indicator = (TextView) getActivity().getLayoutInflater()
                .inflate(R.layout.tab_indicator,
                        (ViewGroup) mRootView.findViewById(android.R.id.tabs), false);
        indicator.setText(textRes);
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
            mTagDisplay = (TextView) mRootView.findViewById(R.id.session_tags_button);
            if (!TextUtils.isEmpty(mHashtag)) {
                // Create the button text
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(getString(R.string.tag_stream) + " ");
                int boldStart = sb.length();
                sb.append(getHashtagsString());
                sb.setSpan(sBoldSpan, boldStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                mTagDisplay.setText(sb);

                mTagDisplay.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
/*                        Intent intent = new Intent(getActivity(), TagStreamActivity.class);
                        intent.putExtra(TagStreamFragment.EXTRA_QUERY, getHashtagsString());
                        startActivity(intent);
*/					Log.d(TAG, "on click mTagDisplay");
                    }
                });
            } else {
                mTagDisplay.setVisibility(View.GONE);
            }

            mRoomId = cursor.getString(SessionsQuery.ROOM_ID);

            // Unregister around setting checked state to avoid triggering
            // listener since change isn't user generated.
            mStarred.setOnCheckedChangeListener(null);
            mStarred.setChecked(cursor.getInt(SessionsQuery.STARRED) != 0);
            mStarred.setOnCheckedChangeListener(this);

            final String sessionAbstract = cursor.getString(SessionsQuery.ABSTRACT);
            if (!TextUtils.isEmpty(sessionAbstract)) {
                UIUtils.setTextMaybeHtml(mAbstract, sessionAbstract);
                mAbstract.setVisibility(View.VISIBLE);
                mHasSummaryContent = true;
            } else {
                mAbstract.setVisibility(View.GONE);
            }

            // Show empty message when all data is loaded, and nothing to show
			if (!mHasSummaryContent) {
					mRootView.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
            }
			else {
				mTabHost.setCurrentTabByTag(TAG_SUMMARY);
			}

            updateLinksTab(cursor);
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
     * Initialize and configure the GeckoView for HedgeDoc.
     * Called lazily when we have session data to avoid NullPointerException.
     */
    private void ensureWebViewInitialized() {
        if (mNotesWebView != null && mGeckoSession != null) {
            // Ensure session is still attached to view (important after rotation)
            if (mNotesWebView.getSession() != mGeckoSession) {
                mNotesWebView.setSession(mGeckoSession);
            }
            return; // Already initialized
        }

        // Try multiple ways to find the GeckoView
        mNotesWebView = (GeckoView) mRootView.findViewById(R.id.notes_webview);

        if (mNotesWebView == null) {
            // Fallback: the included layout's root might be the GeckoView itself
            View tabContent = mRootView.findViewById(R.id.tab_session_notes);
            if (tabContent instanceof GeckoView) {
                mNotesWebView = (GeckoView) tabContent;
            }
        }

        if (mNotesWebView == null) {
            Log.e(TAG, "Failed to find GeckoView in notes tab layout");
            return;
        }

        // Get shared GeckoRuntime instance
        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(getActivity());
        if (runtime == null) {
            Log.e(TAG, "Failed to get GeckoRuntime");
            return;
        }

        // Create GeckoSession
        mGeckoSession = new GeckoSession();

        // Configure GeckoSession for navigation handling
        mGeckoSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, GeckoSession.NavigationDelegate.LoadRequest request) {
                Log.d(TAG, "Navigation request: " + request.uri);
                // Allow all navigation within GeckoView
                return GeckoResult.allow();
            }
            
            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                Log.d(TAG, "New session requested for: " + uri);
                // Load the URL in the current session instead of creating a new window
                session.loadUri(uri);
                // Return null to deny new session creation
                return GeckoResult.fromValue((GeckoSession) null);
            }
        });
        
        // Ensure GeckoView can receive touch events
        mNotesWebView.setFocusable(true);
        mNotesWebView.setFocusableInTouchMode(true);

        // Open session and attach to view
        mGeckoSession.open(runtime);
        mNotesWebView.setSession(mGeckoSession);
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
        if (mTitleString == null) {
            return;
        }
        
        // Ensure view is created before initializing
        if (mRootView == null) {
            return;
        }
        
        // Initialize WebView lazily (after view is attached)
        ensureWebViewInitialized();
        
        if (mNotesWebView == null || mGeckoSession == null) {
            return;
        }
        
        // Extract group acronym from session title (format: "area - group - title")
        String groupAcronym = null;
        if (mTitleString.contains(" - ")) {
            String[] parts = mTitleString.split(" - ", 3);
            if (parts.length >= 2) {
                groupAcronym = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
            }
        }
        
        // Construct HedgeDoc URL: https://notes.ietf.org/notes-ietf-{meetingNumber}-{groupAcronym}?view
        if (groupAcronym != null && !groupAcronym.isEmpty()) {
            int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(getActivity());
            final String hedgedocUrl = "https://notes.ietf.org/notes-ietf-" + meetingNumber + "-" + groupAcronym + "?view";
            
            // Simply load the URL - GeckoView will handle everything naturally
            mGeckoSession.loadUri(hedgedocUrl);
        }
    }

    /**
     * Build and add "summary" tab.
     */
    private void setupLinksTab() {
        // Summary content comes from existing layout
        mTabHost.addTab(mTabHost.newTabSpec(TAG_LINKS)
                .setIndicator(buildIndicator(R.string.session_links))
                .setContent(R.id.tab_session_links));
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
        boolean hasPresentationSlides = false;
        
        // Process each link type defined in SessionsQuery
        for (int i = 0; i < SessionsQuery.LINKS_INDICES.length; i++) {
            final String url = cursor.getString(SessionsQuery.LINKS_INDICES[i]);
            if (!TextUtils.isEmpty(url)) {
                hasLinks = true;
                
                // Special handling for PDF_URL (presentation slides) - can contain multiple entries separated by "::"
                // Each entry is formatted as "title|||url"
                // Note: compare against the COLUMN INDEX (LINKS_INDICES[i]), not the loop index i
                if (SessionsQuery.LINKS_INDICES[i] == SessionsQuery.PDF_URL) {
                    // Split by "::" separator to get individual slide entries
                    String[] slideEntries = url.split("::");
                    
                    // First pass: check if there are any valid slides
                    boolean hasValidSlides = false;
                    for (String entry : slideEntries) {
                        if (!entry.trim().isEmpty()) {
                            hasValidSlides = true;
                            break;
                        }
                    }
                    
                    // Only show section header if there are actual slides
                    if (hasValidSlides && !hasPresentationSlides) {
                        container.addView(createSectionHeader(R.string.session_link_pdf));
                        hasPresentationSlides = true;
                    }
                    
                    // Second pass: add the actual slide links
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
                } else {
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
            } else {
                Log.d(TAG, "NOT URL - Links Indices loop, Url[" + i + "]: " + SessionsQuery.LINKS_INDICES[i] + " Title: " + SessionsQuery.LINKS_TITLES[i]);
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

		Log.d(TAG, "Get hashtag/string method");
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
        int FEEDBACK_URL = 13;
        int NOTES_URL = 14;
        int ROOM_ID = 15;
        int ROOM_NAME = 16;

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
}
