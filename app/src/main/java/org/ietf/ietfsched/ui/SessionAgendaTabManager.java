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
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Manages the Agenda tab GeckoView for SessionDetailFragment.
 */
public class SessionAgendaTabManager {
    private static final String TAG = "SessionAgendaTabManager";
    private static final String TAB_AGENDA = "agenda";
    
    private final Fragment mFragment;
    private final ViewGroup mRootView;
    private final GeckoViewHelper mGeckoViewHelper;
    private final java.util.Map<String, String> mGeckoViewTabUrls;
    private final java.util.Map<String, String> mGeckoViewInitialUrls;
    private final android.widget.TabHost mTabHost;
    
    public SessionAgendaTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls) {
        mFragment = fragment;
        mRootView = rootView;
        mTabHost = tabHost;
        mGeckoViewTabUrls = geckoViewTabUrls;
        mGeckoViewInitialUrls = geckoViewInitialUrls;
        
        // Get or create GeckoViewHelper for Agenda tab
        GeckoViewHelper helper = geckoViewHelpers.get(TAB_AGENDA);
        if (helper == null) {
            helper = new GeckoViewHelper(fragment, false); // Agenda tab is shallow - links open externally
            geckoViewHelpers.put(TAB_AGENDA, helper);
        }
        mGeckoViewHelper = helper;
        
        // Set up error callback to show error message on load failure
        mGeckoViewHelper.setLoadErrorCallback(new GeckoViewHelper.LoadErrorCallback() {
            @Override
            public void onLoadError(String uri) {
                loadErrorMessage(uri);
            }
        });
    }
    
    /**
     * Initialize GeckoView for Agenda tab.
     * @param sharedGeckoView Optional shared GeckoView instance (singleton pattern). If null, tries to find from XML.
     */
    public void initializeGeckoView(GeckoView sharedGeckoView) {
        if (sharedGeckoView != null) {
            // Use shared singleton GeckoView
            Log.d(TAG, "initializeGeckoView: Using shared GeckoView instance");
            mGeckoViewHelper.initialize(sharedGeckoView);
            return;
        }
        
        // Fallback: try to find from XML (for backward compatibility)
        // Note: With singleton pattern, this should not be used, but kept for safety
        if (mRootView == null) {
            Log.w(TAG, "initializeGeckoView: mRootView is null and no shared GeckoView provided");
            return;
        }
        
        // Try to find GeckoView in the container (shouldn't happen with singleton pattern)
        ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_links);
        if (container != null && container.getChildCount() > 0) {
            View child = container.getChildAt(0);
            if (child instanceof GeckoView) {
                GeckoView geckoView = (GeckoView) child;
                Log.d(TAG, "initializeGeckoView: Found agenda GeckoView in container, visibility=" + geckoView.getVisibility() + ", hasSession=" + (geckoView.getSession() != null));
                mGeckoViewHelper.initialize(geckoView);
                Log.d(TAG, "initializeGeckoView: After initialize, visibility=" + geckoView.getVisibility() + ", hasSession=" + (geckoView.getSession() != null) + ", session=" + geckoView.getSession());
                return;
            }
        }
        
        Log.e(TAG, "initializeGeckoView: Could not find agenda GeckoView - shared GeckoView must be provided");
    }
    
    /**
     * Update Agenda tab with the agenda URL from the session cursor.
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     */
    public void updateAgendaTab(String agendaUrl, GeckoView sharedGeckoView) {
        Log.d(TAG, "updateAgendaTab: agendaUrl=" + agendaUrl + ", mRootView=" + (mRootView != null) + ", sharedGeckoView=" + (sharedGeckoView != null));
        if (agendaUrl == null || agendaUrl.isEmpty()) {
            Log.w(TAG, "updateAgendaTab: agendaUrl is null or empty, showing loading message");
            loadLoadingMessage(sharedGeckoView);
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
    
    /**
     * Load a loading message when agenda content is not available.
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     */
    private void loadLoadingMessage(GeckoView sharedGeckoView) {
        if (mGeckoViewHelper == null || mFragment.getActivity() == null) {
            return;
        }
        
        // Ensure GeckoView is initialized
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            // Initialize with shared GeckoView if provided
            if (sharedGeckoView != null) {
                initializeGeckoView(sharedGeckoView);
            } else {
                // Fallback: Try to find shared GeckoView in container
                ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_links);
                if (container != null && container.getChildCount() > 0) {
                    View child = container.getChildAt(0);
                    if (child instanceof GeckoView) {
                        initializeGeckoView((GeckoView) child);
                    }
                }
            }
            
            if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
                Log.w(TAG, "loadLoadingMessage: GeckoView not initialized");
                return;
            }
        }
        
        String markdownText = "Agenda is being downloaded. Please check back in a moment or use the Refresh button.";
        
        // Convert markdown to HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        org.commonmark.node.Node document = parser.parse(markdownText);
        String htmlContent = renderer.render(document);
        
        // Wrap HTML with basic styling
        String styledHtml = "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" +
                "body { font-family: sans-serif; padding: 16px; line-height: 1.6; }" +
                "a { color: #0066cc; }" +
                "ul { padding-left: 20px; }" +
                "li { margin-bottom: 8px; }" +
                "</style>" +
                "</head><body>" +
                htmlContent +
                "</body></html>";
        
        // Load HTML using Base64 encoding (same approach as WellNoteFragment)
        try {
            byte[] htmlBytes = styledHtml.getBytes("UTF-8");
            String base64Html = android.util.Base64.encodeToString(htmlBytes, android.util.Base64.NO_WRAP);
            String dataUri = "data:text/html;charset=utf-8;base64," + base64Html;
            mGeckoViewHelper.loadUrl(dataUri);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to encode Agenda loading message HTML", e);
        }
    }
    
    /**
     * Load an error message when URL load fails.
     * @param failedUrl The URL that failed to load
     */
    private void loadErrorMessage(String failedUrl) {
        if (mGeckoViewHelper == null || mFragment.getActivity() == null) {
            return;
        }
        
        // Ensure GeckoView is initialized
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "loadErrorMessage: GeckoView not initialized");
            return;
        }
        
        String markdownText = "Unable to load agenda. Please check your internet connection and try again.";
        
        // Convert markdown to HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        org.commonmark.node.Node document = parser.parse(markdownText);
        String htmlContent = renderer.render(document);
        
        // Wrap HTML with basic styling
        String styledHtml = "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" +
                "body { font-family: sans-serif; padding: 16px; line-height: 1.6; }" +
                "a { color: #0066cc; }" +
                "ul { padding-left: 20px; }" +
                "li { margin-bottom: 8px; }" +
                "</style>" +
                "</head><body>" +
                htmlContent +
                "</body></html>";
        
        // Load HTML using Base64 encoding
        try {
            byte[] htmlBytes = styledHtml.getBytes("UTF-8");
            String base64Html = android.util.Base64.encodeToString(htmlBytes, android.util.Base64.NO_WRAP);
            String dataUri = "data:text/html;charset=utf-8;base64," + base64Html;
            mGeckoViewHelper.loadUrl(dataUri);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to encode Agenda error message HTML", e);
        }
    }
    
    /**
     * Get the GeckoViewHelper for the Agenda tab.
     */
    public GeckoViewHelper getGeckoViewHelper() {
        return mGeckoViewHelper;
    }
}

