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
    private String mActualInitialUrl; // The actual URL before wrapping (for comparison)
    private boolean mInitialLoadComplete = false;
    private String mCurrentUrl; // Track current URL to prevent error message loops
    
    /**
     * Callback interface for load error events.
     */
    public interface LoadErrorCallback {
        void onLoadError(String uri);
    }
    
    /**
     * Callback interface for page load completion (for CSS injection, etc.).
     */
    public interface PageLoadCallback {
        void onPageLoadComplete(String uri);
    }
    
    private LoadErrorCallback mLoadErrorCallback;
    private PageLoadCallback mPageLoadCallback;
    
    /**
     * Set callback for load error events.
     * @param callback The callback to notify on load errors
     */
    public void setLoadErrorCallback(LoadErrorCallback callback) {
        mLoadErrorCallback = callback;
    }
    
    /**
     * Set callback for page load completion events.
     * @param callback The callback to notify when page loads successfully
     */
    public void setPageLoadCallback(PageLoadCallback callback) {
        mPageLoadCallback = callback;
    }
    
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
        // If this is the same GeckoView we already have, ensure session is attached
        if (mGeckoView == geckoView && mGeckoSession != null) {
            if (geckoView.getSession() != mGeckoSession) {
                Log.d(TAG, "initialize: Reattaching session to same GeckoView");
                geckoView.setSession(mGeckoSession);
            }
            return;
        }
        
        mGeckoView = geckoView;
        
        // If we already have a session, attach it to the view
        // GeckoView automatically handles detaching any previous session
        if (mGeckoSession != null) {
            Log.d(TAG, "initialize: Attaching existing session to GeckoView");
            geckoView.setVisibility(View.VISIBLE);
            geckoView.setSession(mGeckoSession);
            geckoView.bringToFront();
            return;
        }
        
        // No session yet - create one
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
                String uri = request.uri;
                
                // Always allow data URIs (local content)
                if (uri != null && uri.startsWith("data:")) {
                    Log.d(TAG, "onLoadRequest: Allowing data URI: " + uri.substring(0, Math.min(uri.length(), 50)) + "...");
                    return GeckoResult.allow();
                }
                
                if (mIsDeep) {
                    // Deep mode: Allow all navigation - GeckoView handles history automatically
                    return GeckoResult.allow();
                } else {
                    // Shallow mode: Allow initial URL to load, but open subsequent links externally
                    
                    // Check if this is the initial URL or a data URI (for wrapped content)
                    boolean isInitialUrl = (mInitialUrl != null && uri.equals(mInitialUrl)) ||
                                         (mActualInitialUrl != null && uri.equals(mActualInitialUrl));
                    boolean isDataUri = uri != null && uri.startsWith("data:");
                    
                    if ((isInitialUrl && !mInitialLoadComplete) || isDataUri) {
                        // This is the initial URL load or a data URI (wrapped content) - allow it
                        if (isDataUri) {
                            Log.d(TAG, "onLoadRequest (shallow): Allowing data URI load: " + uri.substring(0, Math.min(uri.length(), 50)) + "...");
                        } else {
                            Log.d(TAG, "onLoadRequest (shallow): Allowing initial URL load: " + uri);
                            mInitialLoadComplete = true;
                        }
                        return GeckoResult.allow();
                    } else {
                        // This is a subsequent navigation (link click) - open externally
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
            }
            
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                Log.d(TAG, "onCanGoBack: " + canGoBack + " (isDeep=" + mIsDeep + ", currentUrl=" + 
                      (mCurrentUrl != null ? (mCurrentUrl.startsWith("data:") ? "data:..." : mCurrentUrl) : "null") + ")");
                
                // If current URL is a data URI, it has no navigation history - force canGoBack to false
                if (mCurrentUrl != null && mCurrentUrl.startsWith("data:")) {
                    Log.d(TAG, "onCanGoBack: Ignoring callback for data URI, forcing canGoBack=false");
                    mCanGoBack = false;
                } else {
                    mCanGoBack = canGoBack;
                }
            }
            
            @Override
            public GeckoResult<String> onLoadError(GeckoSession session, String uri, org.mozilla.geckoview.WebRequestError error) {
                Log.w(TAG, "onLoadError: uri=" + uri + ", error=" + error + ", code=" + (error != null ? error.code : "null"));
                // Notify callback about load error
                if (mLoadErrorCallback != null && uri != null && !uri.startsWith("data:")) {
                    mLoadErrorCallback.onLoadError(uri);
                }
                // Return null to use default error page, or return a custom error page URL
                return GeckoResult.fromValue(null);
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
        
        // Set ProgressDelegate to detect load errors
        mGeckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                Log.d(TAG, "onPageStart: url=" + url);
                mCurrentUrl = url;
            }
            
            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                Log.d(TAG, "onPageStop: success=" + success + ", url=" + mCurrentUrl);
                if (!success && mCurrentUrl != null && !mCurrentUrl.startsWith("data:")) {
                    // Page load failed - notify callback (but not for data URIs to avoid loops)
                    if (mLoadErrorCallback != null) {
                        mLoadErrorCallback.onLoadError(mCurrentUrl);
                    }
                } else if (success && mCurrentUrl != null && !mCurrentUrl.startsWith("data:")) {
                    // Page loaded successfully - notify callback for CSS injection, etc.
                    if (mPageLoadCallback != null) {
                        mPageLoadCallback.onPageLoadComplete(mCurrentUrl);
                    }
                }
            }
        });
        
        // Set ContentDelegate to inject CSS when DOM is ready
        mGeckoSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                Log.d(TAG, "onTitleChange: title=" + title + ", currentUrl=" + 
                    (mCurrentUrl != null ? (mCurrentUrl.startsWith("data:") ? "data:..." : mCurrentUrl) : "null"));
                
                // Title change indicates DOM is ready - trigger CSS injection if needed
                // The actual injection will be handled by the PageLoadCallback
                if (mPendingCSS != null && !mPendingCSS.isEmpty()) {
                    // Inject CSS after a short delay to ensure DOM is fully ready
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        injectCSSDirectly(mPendingCSS);
                        mPendingCSS = null; // Clear after injection
                    }, 100);
                }
            }
            
            @Override
            public void onCloseRequest(GeckoSession session) {
                // Not used
            }
            
            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                // Not used
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
        
        // If loading a data URI, reset navigation state (data URIs have no history)
        if (url != null && url.startsWith("data:")) {
            mCanGoBack = false;
        }
        
        // Track actual initial URL for state management
        if (mActualInitialUrl == null) {
            mActualInitialUrl = url;
        }
        
        // Track the URL as the initial URL for navigation handling
        if (mInitialUrl == null) {
            mInitialUrl = url;
        }
        
        mGeckoSession.loadUri(url);
    }
    
    private String mPendingCSS; // Store CSS to inject after page loads
    
    /**
     * Inject CSS into the loaded page via JavaScript.
     * Uses GeckoRuntime's WebExtension API or ContentDelegate callback.
     * @param css The CSS to inject
     */
    public void injectCSS(String css) {
        if (mGeckoSession == null || mFragment.getActivity() == null) {
            Log.w(TAG, "injectCSS: GeckoSession or Activity not initialized");
            return;
        }
        
        // Store CSS to inject when page is ready
        mPendingCSS = css;
        
        // Try immediate injection (page might already be loaded)
        injectCSSDirectly(css);
        
        // Also schedule delayed injection in case DOM isn't ready yet
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (mPendingCSS != null && mPendingCSS.equals(css)) {
                injectCSSDirectly(css);
            }
        }, 500); // Delay to ensure DOM is ready
    }
    
    /**
     * Inject CSS directly using JavaScript evaluation.
     * Tries multiple methods: reflection-based method calls and javascript: URI approach.
     */
    private void injectCSSDirectly(String css) {
        if (mGeckoSession == null || css == null || css.isEmpty()) {
            return;
        }
        
        // Escape CSS for JavaScript string
        String escapedCSS = css.replace("\\", "\\\\")
                              .replace("'", "\\'")
                              .replace("\n", "\\n")
                              .replace("\r", "")
                              .replace("\"", "\\\"");
        
        // Create JavaScript to inject CSS
        String js = "(function() {" +
                    "  try {" +
                    "    if (document.getElementById('ietfsched-agenda-css')) return;" +
                    "    var style = document.createElement('style');" +
                    "    style.id = 'ietfsched-agenda-css';" +
                    "    style.type = 'text/css';" +
                    "    style.innerHTML = \"" + escapedCSS + "\";" +
                    "    var head = document.head || document.getElementsByTagName('head')[0];" +
                    "    if (head) head.appendChild(style);" +
                    "  } catch(e) { console.error('CSS injection failed:', e); }" +
                    "})();";
        
        // Try different method names that might exist
        // Note: Some sources mention evaluateJavascript (lowercase 's')
        String[] methodNames = {"evaluateJavascript", "evaluateJavaScript", "evaluateJS", "executeScript", "evaluate"};
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = mGeckoSession.getClass().getMethod(methodName, String.class);
                Object result = method.invoke(mGeckoSession, js);
                // Log success - if it returns a GeckoResult, we can't easily handle it without knowing the exact API
                Log.d(TAG, "injectCSSDirectly: CSS injection attempted via " + methodName + 
                      (result != null ? " (returned " + result.getClass().getSimpleName() + ")" : ""));
                return; // Success - method was called
            } catch (NoSuchMethodException e) {
                // Try next method name
                continue;
            } catch (Exception e) {
                Log.w(TAG, "injectCSSDirectly: Failed to call " + methodName, e);
            }
        }
        
        // Fallback: Try using javascript: URI approach
        // This loads a javascript: URI that injects CSS and returns undefined to keep the page
        try {
            String javascriptUri = "javascript:(function(){" +
                "try{" +
                "if(document.getElementById('ietfsched-agenda-css'))return;" +
                "var s=document.createElement('style');" +
                "s.id='ietfsched-agenda-css';" +
                "s.innerHTML=\"" + escapedCSS + "\";" +
                "(document.head||document.getElementsByTagName('head')[0]).appendChild(s);" +
                "}catch(e){console.error('CSS injection failed:',e);}" +
                "})();void(0);";
            
            // Load the javascript URI - this will execute the script
            // Note: This might replace the page, so we need to be careful
            // Actually, let's not use this as it replaces the page content
            Log.d(TAG, "injectCSSDirectly: javascript: URI approach not used (would replace page)");
        } catch (Exception e) {
            Log.w(TAG, "injectCSSDirectly: Failed to create javascript URI", e);
        }
        
        Log.w(TAG, "injectCSSDirectly: No JavaScript evaluation method found - CSS injection not available");
    }
    
    /**
     * Inject CSS via WebExtension content script.
     * This is a workaround for GeckoView's lack of direct JavaScript evaluation API.
     */
    private void injectCSSViaWebExtension(GeckoRuntime runtime, 
                                         org.mozilla.geckoview.WebExtensionController webExtController,
                                         String escapedCSS) {
        // WebExtension approach requires creating extension files
        // For now, fall back to direct injection attempt
        Log.d(TAG, "injectCSSViaWebExtension: WebExtension approach not implemented - using direct injection");
        injectCSSDirectly(mPendingCSS);
    }
    
    /**
     * Reset initial load state. Used when loading a URL that should be treated as initial.
     */
    public void resetInitialLoadState() {
        mInitialLoadComplete = false;
        mInitialUrl = null;
        mActualInitialUrl = null;
        mCanGoBack = false; // Reset navigation state
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
        mActualInitialUrl = null;
        mInitialLoadComplete = false;
        
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
        mActualInitialUrl = null;
        mInitialLoadComplete = false;
    }
}

