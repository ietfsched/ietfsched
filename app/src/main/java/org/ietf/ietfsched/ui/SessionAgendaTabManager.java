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
        if (agendaUrl == null || agendaUrl.isEmpty()) {
            // Only show loading message if GeckoView is already initialized and visible
            if (sharedGeckoView != null && mGeckoViewHelper.getGeckoView() != null) {
                loadLoadingMessage(sharedGeckoView, R.id.tab_session_links, 
                        "Agenda is being downloaded. Please check back in a moment or use the Refresh button.");
            }
            return;
        }
        
        // Initialize GeckoView if provided
        if (sharedGeckoView != null) {
            initializeGeckoView(sharedGeckoView);
        }
        
        // Check if URL changed
        String lastUrl = mGeckoViewTabUrls.get(TAB_AGENDA);
        if (!agendaUrl.equals(lastUrl)) {
            // Store URL
            mGeckoViewTabUrls.put(TAB_AGENDA, agendaUrl);
            if (mGeckoViewInitialUrls.get(TAB_AGENDA) == null) {
                mGeckoViewInitialUrls.put(TAB_AGENDA, agendaUrl);
            }
            
            // Load URL if GeckoView is ready and session is attached
            if (mGeckoViewHelper.getGeckoView() != null && mGeckoViewHelper.getGeckoSession() != null) {
                // Only load if this tab's session is currently attached to avoid blinking
                if (mGeckoViewHelper.getGeckoView().getSession() == mGeckoViewHelper.getGeckoSession()) {
                    mGeckoViewHelper.loadUrl(agendaUrl);
                }
            }
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

