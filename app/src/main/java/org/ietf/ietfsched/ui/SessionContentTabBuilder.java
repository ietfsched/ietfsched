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
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.R;

/**
 * Builds the Content tab for SessionDetailFragment.
 * Handles rendering of presentation slides and Internet drafts.
 */
public class SessionContentTabBuilder {
    private static final String TAG = "SessionContentTabBuilder";
    
    private final Fragment mFragment;
    private final ViewGroup mRootView;
    private final Runnable mLinkEventCallback;
    
    public SessionContentTabBuilder(Fragment fragment, ViewGroup rootView, Runnable linkEventCallback) {
        mFragment = fragment;
        mRootView = rootView;
        mLinkEventCallback = linkEventCallback;
    }
    
    /**
     * Updates the Content tab with Internet drafts and presentation slides.
     */
    public void updateContentTab(Cursor cursor, int pdfUrlIndex, int draftsUrlIndex) {
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

        LayoutInflater inflater = mFragment.getLayoutInflater();
        boolean hasContent = false;

        // Note: cursor should already be at first position from onSessionQueryComplete
        // But ensure it's valid
        if (cursor == null) {
            Log.e(TAG, "updateContentTab: cursor is null!");
            return;
        }

        // First, add Presentation Slides section
        final String pdfUrl = cursor.getString(pdfUrlIndex);
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
                TextView slidesHeader = SessionDetailUIHelper.createSectionHeader(mFragment, R.string.session_link_pdf);
                // Add padding above the header
                android.widget.LinearLayout.LayoutParams headerParams = 
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                headerParams.topMargin = mFragment.getResources().getDimensionPixelSize(R.dimen.body_padding_medium);
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
                            ? mFragment.getString(R.string.session_link_pdf)
                            : mFragment.getString(R.string.session_link_pdf) + " " + (j + 1);
                        slideUrl = slideEntry;
                    }
                    
                    ViewGroup linkContainer = (ViewGroup)
                            inflater.inflate(R.layout.list_item_session_link, container, false);
                    
                    ((TextView) linkContainer.findViewById(R.id.link_text)).setText(slideTitle);
                    
                    linkContainer.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            if (mLinkEventCallback != null) {
                                mLinkEventCallback.run();
                            }
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(slideUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                            mFragment.startActivity(intent);
                        }
                    });
                    
                    container.addView(linkContainer);
                    container.addView(SessionDetailUIHelper.createThinSeparator(mFragment));
                }
            }
        }

        // Then, add Internet Drafts section
        final String draftsUrl = cursor.getString(draftsUrlIndex);
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
                TextView draftsHeader = SessionDetailUIHelper.createSectionHeader(mFragment, R.string.session_drafts);
                // Add padding above the header
                android.widget.LinearLayout.LayoutParams headerParams = 
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                headerParams.topMargin = mFragment.getResources().getDimensionPixelSize(R.dimen.body_padding_medium);
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
                            if (mLinkEventCallback != null) {
                                mLinkEventCallback.run();
                            }
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(draftUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                            mFragment.startActivity(intent);
                        }
                    });
                    
                    container.addView(linkContainer);
                    container.addView(SessionDetailUIHelper.createThinSeparator(mFragment));
                }
            }
        }

        // Show empty message if no content
        if (!hasContent) {
            TextView emptyView = new TextView(mFragment.getActivity());
            emptyView.setId(android.R.id.empty);
            emptyView.setText(mFragment.getString(R.string.empty_session_detail));
            emptyView.setGravity(android.view.Gravity.CENTER);
            emptyView.setTextAppearance(mFragment.getActivity(), android.R.style.TextAppearance_Medium);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
            emptyView.setLayoutParams(params);
            container.addView(emptyView);
        }
    }
}


