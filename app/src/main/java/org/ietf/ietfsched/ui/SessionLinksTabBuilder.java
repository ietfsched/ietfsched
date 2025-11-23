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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.R;

/**
 * Builds the Links tab for SessionDetailFragment.
 * Handles rendering of session links including agenda with Meetecho button.
 */
public class SessionLinksTabBuilder {
    private static final String TAG = "SessionLinksTabBuilder";
    
    private final Fragment mFragment;
    private final ViewGroup mRootView;
    private final String mTitleString;
    private final Runnable mLinkEventCallback;
    private final AgendaOpener mAgendaOpener;
    
    public interface AgendaOpener {
        void openAgenda(String url);
    }
    
    public SessionLinksTabBuilder(Fragment fragment, ViewGroup rootView, String titleString,
            Runnable linkEventCallback, AgendaOpener agendaOpener) {
        mFragment = fragment;
        mRootView = rootView;
        mTitleString = titleString;
        mLinkEventCallback = linkEventCallback;
        mAgendaOpener = agendaOpener;
    }
    
    /**
     * Updates the Links tab with session links.
     * 
     * @param cursor The session cursor
     * @param linksIndices Array of column indices for link types
     * @param linksTitles Array of string resource IDs for link titles
     * @param pdfUrlIndex Index of PDF_URL column (to skip it)
     * @param sessionUrlIndex Index of SESSION_URL column (for special handling)
     */
    public void updateLinksTab(Cursor cursor, int[] linksIndices, int[] linksTitles, 
            int pdfUrlIndex, int sessionUrlIndex) {
        ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.links_container);

        // Remove all views but the 'empty' view
        int childCount = container.getChildCount();
        if (childCount > 1) {
            container.removeViews(1, childCount - 1);
        }

        LayoutInflater inflater = mFragment.getLayoutInflater();
        boolean hasLinks = false;
        
        // Process each link type
        // Skip PDF_URL (presentation slides) - those are now in Content tab
        for (int i = 0; i < linksIndices.length; i++) {
            // Skip PDF_URL - moved to Content tab
            if (linksIndices[i] == pdfUrlIndex) {
                continue;
            }
            
            final String url = cursor.getString(linksIndices[i]);
            if (!TextUtils.isEmpty(url)) {
                hasLinks = true;
                
                // Special handling for Agenda link (SESSION_URL) - place Meetecho button on same row
                if (linksIndices[i] == sessionUrlIndex) {
                    // Extract group acronym from session title (format: "area - group - title")
                    String groupAcronym = extractGroupAcronym(mTitleString);
                    
                    if (groupAcronym != null && !groupAcronym.isEmpty()) {
                        // Create horizontal container for Agenda link + Meetecho button
                        android.widget.LinearLayout horizontalContainer = new android.widget.LinearLayout(mFragment.getActivity());
                        horizontalContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        horizontalContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        horizontalContainer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                        
                        // Add Agenda link on the left
                        ViewGroup linkContainer = (ViewGroup)
                                inflater.inflate(R.layout.list_item_session_link, container, false);
                        ((android.widget.TextView) linkContainer.findViewById(R.id.link_text)).setText(
                                linksTitles[i]);
                        final String agendaUrl = url;
                        linkContainer.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View view) {
                                if (mLinkEventCallback != null) {
                                    mLinkEventCallback.run();
                                }
                                if (mAgendaOpener != null) {
                                    mAgendaOpener.openAgenda(agendaUrl);
                                }
                            }
                        });
                        
                        android.widget.LinearLayout.LayoutParams agendaParams = new android.widget.LinearLayout.LayoutParams(
                            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                        linkContainer.setLayoutParams(agendaParams);
                        horizontalContainer.addView(linkContainer);
                        
                        // Add Meetecho button on the right
                        int meetingNumber = org.ietf.ietfsched.util.MeetingPreferences.getCurrentMeetingNumber(mFragment.getActivity());
                        final String meetechoUrl = "https://meetings.conf.meetecho.com/onsite" + meetingNumber + "/?group=" + groupAcronym;
                        horizontalContainer.addView(SessionDetailUIHelper.createMeetechoButton(mFragment, meetechoUrl, mLinkEventCallback));
                        
                        container.addView(horizontalContainer);
                        container.addView(SessionDetailUIHelper.createSeparator(mFragment));
                    } else {
                        // No group acronym, just add agenda link normally
                        addLinkItem(container, inflater, linksTitles[i], url, true);
                    }
                } else {
                    // Normal handling for other link types (single URL)
                    addLinkItem(container, inflater, linksTitles[i], url, false);
                }
            }
        }

        container.findViewById(R.id.empty_links).setVisibility(hasLinks ? View.GONE : View.VISIBLE);
    }
    
    private void addLinkItem(ViewGroup container, LayoutInflater inflater, int linkTitleResId, 
            final String url, boolean isAgenda) {
        ViewGroup linkContainer = (ViewGroup)
                inflater.inflate(R.layout.list_item_session_link, container, false);
        ((android.widget.TextView) linkContainer.findViewById(R.id.link_text)).setText(linkTitleResId);
        
        linkContainer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (mLinkEventCallback != null) {
                    mLinkEventCallback.run();
                }
                if (isAgenda && mAgendaOpener != null) {
                    mAgendaOpener.openAgenda(url);
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mFragment.startActivity(intent);
                }
            }
        });

        container.addView(linkContainer);
        container.addView(SessionDetailUIHelper.createSeparator(mFragment));
    }
    
    private String extractGroupAcronym(String titleString) {
        if (titleString == null || !titleString.contains(" - ")) {
            return null;
        }
        String[] parts = titleString.split(" - ", 3);
        if (parts.length >= 2) {
            return parts[1].toLowerCase(java.util.Locale.ROOT).trim();
        }
        return null;
    }
}

