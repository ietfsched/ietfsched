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

package org.ietf.ietfsched.util;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * Helper class for managing GeckoView instances in fragments.
 * Supports both "deep" (navigation stays within GeckoView) and "shallow" (links open externally) modes.
 */
public class GeckoViewHelper {
    private static final String TAG = "GeckoViewHelper";
    
    private final Fragment mFragment;
    private GeckoView mGeckoView;
    private GeckoSession mGeckoSession;
    private boolean mCanGoBack = false;
    private boolean mIsDeep;
    private String mInitialUrl;
    
    /**
     * Create a GeckoViewHelper for a fragment.
     * @param fragment The fragment that will use this GeckoView
     * @param isDeep true for deep navigation (stays within GeckoView), false for shallow (links open externally)
     */
    public GeckoViewHelper(Fragment fragment, boolean isDeep) {
        mFragment = fragment;
        mIsDeep = isDeep;
    }
    
    /**
     * Initialize GeckoView from an existing View (e.g., from layout XML).
     * @param geckoView The GeckoView instance from the layout
     */
    public void initialize(GeckoView geckoView) {
        if (mGeckoView != null && mGeckoSession != null) {
            // Already initialized - ensure session is attached
            if (geckoView.getSession() != mGeckoSession) {
                Log.d(TAG, "initialize: Reattaching session to view");
                geckoView.setSession(mGeckoSession);
            }
            mGeckoView = geckoView;
            return;
        }
        
        mGeckoView = geckoView;
        createSession();
    }
    
    /**
     * Create a new GeckoView programmatically.
     * @return The created GeckoView instance
     */
    public GeckoView createGeckoView() {
        if (mGeckoView != null && mGeckoSession != null) {
            // Already initialized - return existing view
            return mGeckoView;
        }
        
        mGeckoView = new GeckoView(mFragment.getActivity());
        createSession();
        return mGeckoView;
    }
    
    /**
     * Create and configure the GeckoSession.
     */
    private void createSession() {
        if (mGeckoView == null || mFragment.getActivity() == null) {
            return;
        }
        
        // Get shared GeckoRuntime instance
        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(mFragment.getActivity());
        if (runtime == null) {
            Log.e(TAG, "Failed to get GeckoRuntime");
            return;
        }
        
        // Create GeckoSession
        mGeckoSession = new GeckoSession();
        
        // Configure GeckoSession for navigation handling based on mode
        mGeckoSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, GeckoSession.NavigationDelegate.LoadRequest request) {
                if (mIsDeep) {
                    // Deep mode: Allow all navigation - GeckoView handles history automatically
                    return GeckoResult.allow();
                } else {
                    // Shallow mode: Open all links in external browser
                    String uri = request.uri;
                    Log.d(TAG, "onLoadRequest (shallow): Opening link in external browser: " + uri);
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                        mFragment.getActivity().startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open URL in external browser: " + uri, e);
                    }
                    // Deny loading in GeckoView - we opened it externally
                    return GeckoResult.deny();
                }
            }
            
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                Log.d(TAG, "onCanGoBack: " + canGoBack + " (isDeep=" + mIsDeep + ")");
                mCanGoBack = canGoBack;
            }
            
            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                if (mIsDeep) {
                    // Deep mode: Load the URI in the current session
                    session.loadUri(uri);
                    // Return null to deny new session creation
                    return GeckoResult.fromValue((GeckoSession) null);
                } else {
                    // Shallow mode: Open in external browser
                    Log.d(TAG, "onNewSession (shallow): Opening link in external browser: " + uri);
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                        mFragment.getActivity().startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open URL in external browser: " + uri, e);
                    }
                    // Return null to deny new session creation
                    return GeckoResult.fromValue((GeckoSession) null);
                }
            }
        });
        
        // Ensure GeckoView can receive touch events
        mGeckoView.setFocusable(true);
        mGeckoView.setFocusableInTouchMode(true);
        
        // Open session and attach to view
        mGeckoSession.open(runtime);
        mGeckoView.setSession(mGeckoSession);
    }
    
    /**
     * Load a URL into the GeckoView.
     * @param url The URL to load
     */
    public void loadUrl(String url) {
        if (mGeckoSession == null) {
            Log.w(TAG, "loadUrl: GeckoSession not initialized");
            return;
        }
        
        // Track initial URL for state management
        if (mInitialUrl == null) {
            mInitialUrl = url;
        }
        
        mGeckoSession.loadUri(url);
    }
    
    /**
     * Re-initialize GeckoView by closing the old session and creating a new one.
     * This clears the navigation history.
     */
    public void reinitialize() {
        if (mGeckoSession != null) {
            Log.d(TAG, "reinitialize: Closing old GeckoSession");
            // Detach session from view before closing
            if (mGeckoView != null) {
                mGeckoView.setSession(null);
            }
            // Close the old session
            mGeckoSession.close();
            mGeckoSession = null;
        }
        
        // Reset navigation state
        mCanGoBack = false;
        mInitialUrl = null;
        
        // Re-create session
        createSession();
    }
    
    /**
     * Check if the current URL matches the initial URL.
     * @param currentUrl The current URL to check
     * @return true if the URL has changed from the initial URL
     */
    public boolean hasUrlChanged(String currentUrl) {
        return mInitialUrl != null && !currentUrl.equals(mInitialUrl);
    }
    
    /**
     * Handle back button press - navigate back in GeckoView if possible.
     * @return true if GeckoView handled the back press, false otherwise
     */
    public boolean onBackPressed() {
        if (mGeckoSession != null && mCanGoBack) {
            Log.d(TAG, "onBackPressed: Navigating back in GeckoView");
            mGeckoSession.goBack();
            return true;
        }
        return false;
    }
    
    /**
     * Get the GeckoView instance.
     * @return The GeckoView, or null if not initialized
     */
    public GeckoView getGeckoView() {
        return mGeckoView;
    }
    
    /**
     * Get the GeckoSession instance.
     * @return The GeckoSession, or null if not initialized
     */
    public GeckoSession getGeckoSession() {
        return mGeckoSession;
    }
    
    /**
     * Check if GeckoView can go back.
     * @return true if there's navigation history
     */
    public boolean canGoBack() {
        return mCanGoBack;
    }
    
    /**
     * Check if this helper is in deep mode.
     * @return true if deep mode (navigation stays within), false if shallow (links open externally)
     */
    public boolean isDeep() {
        return mIsDeep;
    }
    
    /**
     * Clean up resources when the fragment is destroyed.
     */
    public void cleanup() {
        // Detach session from view first (if view exists and has a session)
        if (mGeckoView != null) {
            try {
                // Only detach if there's actually a session attached
                if (mGeckoView.getSession() != null) {
                    mGeckoView.setSession(null);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error detaching session from view during cleanup", e);
            }
        }
        
        // Close session if it exists
        if (mGeckoSession != null) {
            try {
                mGeckoSession.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing GeckoSession during cleanup", e);
            }
            mGeckoSession = null;
        }
        
        mGeckoView = null;
        mCanGoBack = false;
        mInitialUrl = null;
    }
}

