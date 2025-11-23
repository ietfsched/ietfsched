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
 * Manages the Agenda tab GeckoView for SessionDetailFragment.
 */
public class SessionAgendaTabManager extends BaseGeckoViewTabManager {
    private static final String TAG = "SessionAgendaTabManager";
    private static final String TAB_AGENDA = "agenda";
    
    public SessionAgendaTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls) {
        super(fragment, rootView, tabHost, geckoViewHelpers, geckoViewTabUrls, geckoViewInitialUrls,
                TAB_AGENDA, false); // Agenda tab is shallow - links open externally
    }
    
    /**
     * Initialize GeckoView for Agenda tab.
     * @param sharedGeckoView Optional shared GeckoView instance (singleton pattern). If null, tries to find from XML.
     */
    public void initializeGeckoView(GeckoView sharedGeckoView) {
        initializeGeckoView(sharedGeckoView, R.id.tab_session_links);
    }
    
    /**
     * Update Agenda tab with the agenda URL from the session cursor.
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     */
    public void updateAgendaTab(String agendaUrl, GeckoView sharedGeckoView) {
        Log.d(TAG, "updateAgendaTab: agendaUrl=" + agendaUrl + ", mRootView=" + (mRootView != null) + ", sharedGeckoView=" + (sharedGeckoView != null));
        if (agendaUrl == null || agendaUrl.isEmpty()) {
            Log.w(TAG, "updateAgendaTab: agendaUrl is null or empty, showing loading message");
            loadLoadingMessage(sharedGeckoView, R.id.tab_session_links, 
                    "Agenda is being downloaded. Please check back in a moment or use the Refresh button.");
            return;
        }
        
        // Ensure view is created before initializing
        if (mRootView == null) {
            Log.w(TAG, "updateAgendaTab: mRootView is null, returning");
            return;
        }
        
        // Initialize GeckoView if shared instance is provided
        if (sharedGeckoView != null) {
            initializeGeckoView(sharedGeckoView);
        } else {
            // Fallback: Try to find shared GeckoView in container (less efficient)
            ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_links);
            if (container != null && container.getChildCount() > 0) {
                View child = container.getChildAt(0);
                if (child instanceof GeckoView) {
                    initializeGeckoView((GeckoView) child);
                }
            }
        }
        
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "updateAgendaTab: GeckoView not initialized, storing URL for later");
            // Store URL for later loading when GeckoView is initialized
            mGeckoViewTabUrls.put(TAB_AGENDA, agendaUrl);
            return;
        }
        
        // Only load if URL has changed AND Agenda tab is currently active
        boolean agendaTabActive = mTabHost != null && TAB_AGENDA.equals(mTabHost.getCurrentTabTag());
        String lastUrl = mGeckoViewTabUrls.get(TAB_AGENDA);
        boolean isFirstLoad = (lastUrl == null);
        if (!agendaUrl.equals(lastUrl)) {
            Log.d(TAG, "updateAgendaTab: URL changed, agendaTabActive=" + agendaTabActive + ", isFirstLoad=" + isFirstLoad);
            // Only load if Agenda tab is active or if this is the first load
            if (agendaTabActive || isFirstLoad) {
                Log.d(TAG, "updateAgendaTab: Loading URL: " + agendaUrl);
                mGeckoViewHelper.loadUrl(agendaUrl);
                mGeckoViewTabUrls.put(TAB_AGENDA, agendaUrl);
                // Track this as the initial URL for this tab
                mGeckoViewInitialUrls.put(TAB_AGENDA, agendaUrl);
            } else {
                Log.d(TAG, "updateAgendaTab: Skipping load - Agenda tab not active, will load when tab is opened");
                // Store URL for later loading when Agenda tab is opened
                mGeckoViewTabUrls.put(TAB_AGENDA, agendaUrl);
            }
        } else {
            Log.d(TAG, "updateAgendaTab: URL unchanged, skipping reload: " + agendaUrl);
        }
    }
    
    /**
     * Update Agenda tab with the agenda URL (backward compatibility - no shared GeckoView).
     */
    public void updateAgendaTab(String agendaUrl) {
        updateAgendaTab(agendaUrl, null);
    }
    
    @Override
    protected String getErrorMessage() {
        return "Unable to load agenda. Please check your internet connection and try again.";
    }
}

