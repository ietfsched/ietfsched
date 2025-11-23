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
import org.ietf.ietfsched.util.GeckoViewHelper;
import org.mozilla.geckoview.GeckoView;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Base class for GeckoView tab managers with common functionality for loading messages and error handling.
 */
public abstract class BaseGeckoViewTabManager {
    protected static final String TAG = "BaseGeckoViewTabManager";
    
    protected final Fragment mFragment;
    protected final ViewGroup mRootView;
    protected final GeckoViewHelper mGeckoViewHelper;
    protected final java.util.Map<String, String> mGeckoViewTabUrls;
    protected final java.util.Map<String, String> mGeckoViewInitialUrls;
    protected final android.widget.TabHost mTabHost;
    
    protected BaseGeckoViewTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost,
            java.util.Map<String, GeckoViewHelper> geckoViewHelpers,
            java.util.Map<String, String> geckoViewTabUrls,
            java.util.Map<String, String> geckoViewInitialUrls,
            String tabId, boolean isDeep) {
        mFragment = fragment;
        mRootView = rootView;
        mTabHost = tabHost;
        mGeckoViewTabUrls = geckoViewTabUrls;
        mGeckoViewInitialUrls = geckoViewInitialUrls;
        
        // Get or create GeckoViewHelper for this tab
        GeckoViewHelper helper = geckoViewHelpers.get(tabId);
        if (helper == null) {
            helper = new GeckoViewHelper(fragment, isDeep);
            geckoViewHelpers.put(tabId, helper);
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
     * Initialize GeckoView for this tab.
     * @param sharedGeckoView Optional shared GeckoView instance (singleton pattern). If null, tries to find from XML.
     * @param containerId The container view ID to search for GeckoView
     */
    protected void initializeGeckoView(GeckoView sharedGeckoView, int containerId) {
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
        ViewGroup container = (ViewGroup) mRootView.findViewById(containerId);
        if (container != null && container.getChildCount() > 0) {
            View child = container.getChildAt(0);
            if (child instanceof GeckoView) {
                GeckoView geckoView = (GeckoView) child;
                Log.d(TAG, "initializeGeckoView: Found GeckoView in container, visibility=" + geckoView.getVisibility() + ", hasSession=" + (geckoView.getSession() != null));
                mGeckoViewHelper.initialize(geckoView);
                Log.d(TAG, "initializeGeckoView: After initialize, visibility=" + geckoView.getVisibility() + ", hasSession=" + (geckoView.getSession() != null) + ", session=" + geckoView.getSession());
                return;
            }
        }
        
        Log.e(TAG, "initializeGeckoView: Could not find GeckoView - shared GeckoView must be provided");
    }
    
    /**
     * Load a loading message when content is not available.
     * @param sharedGeckoView Optional shared GeckoView instance. If provided, will initialize with it.
     * @param containerId The container view ID
     * @param loadingMessage The loading message text
     */
    protected void loadLoadingMessage(GeckoView sharedGeckoView, int containerId, String loadingMessage) {
        if (mGeckoViewHelper == null || mFragment.getActivity() == null) {
            return;
        }
        
        // Ensure GeckoView is initialized
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            // Initialize with shared GeckoView if provided
            if (sharedGeckoView != null) {
                initializeGeckoView(sharedGeckoView, containerId);
            } else {
                // Fallback: Try to find shared GeckoView in container
                ViewGroup container = (ViewGroup) mRootView.findViewById(containerId);
                if (container != null && container.getChildCount() > 0) {
                    View child = container.getChildAt(0);
                    if (child instanceof GeckoView) {
                        initializeGeckoView((GeckoView) child, containerId);
                    }
                }
            }
            
            if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
                Log.w(TAG, "loadLoadingMessage: GeckoView not initialized");
                return;
            }
        }
        
        loadMarkdownAsDataUri(loadingMessage);
    }
    
    /**
     * Load an error message when URL load fails.
     * @param failedUrl The URL that failed to load
     */
    protected void loadErrorMessage(String failedUrl) {
        if (mGeckoViewHelper == null || mFragment.getActivity() == null) {
            return;
        }
        
        // Ensure GeckoView is initialized
        if (mGeckoViewHelper.getGeckoView() == null || mGeckoViewHelper.getGeckoSession() == null) {
            Log.w(TAG, "loadErrorMessage: GeckoView not initialized");
            return;
        }
        
        String errorMessage = getErrorMessage();
        loadMarkdownAsDataUri(errorMessage);
    }
    
    /**
     * Get the error message text for this tab.
     * @return The error message text
     */
    protected abstract String getErrorMessage();
    
    /**
     * Load markdown text as a data URI in GeckoView.
     * @param markdownText The markdown text to load
     */
    private void loadMarkdownAsDataUri(String markdownText) {
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
            Log.e(TAG, "Failed to encode HTML", e);
        }
    }
    
    /**
     * Get the GeckoViewHelper for this tab.
     */
    public GeckoViewHelper getGeckoViewHelper() {
        return mGeckoViewHelper;
    }
}

