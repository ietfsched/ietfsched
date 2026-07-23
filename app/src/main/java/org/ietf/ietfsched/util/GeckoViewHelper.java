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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.BasicSelectionActionDelegate;
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
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private GeckoView mGeckoView;
    /** Primary session (Meetecho page, notes, etc.). */
    private GeckoSession mGeckoSession;
    /** OAuth popup while Datatracker login runs; parent stays on {@link #mGeckoView}. */
    private GeckoSession mPopupSession;
    /**
     * OAuth popup GeckoView. Prefer a layout sibling of the main Join view (lab pattern).
     * If unset, falls back to an activity-content overlay (legacy).
     */
    private GeckoView mOAuthPopupView;
    /** True when {@link #mOAuthPopupView} comes from Join layout — do not remove from parent. */
    private boolean mOAuthPopupViewFromLayout;
    private boolean mCanGoBack = false;
    private boolean mIsDeep;
    private boolean mOAuthPopupsEnabled;
    private String mInitialUrl;
    private String mActualInitialUrl;
    private boolean mInitialLoadComplete = false;
    private String mCurrentUrl;
    private String mPopupCurrentUrl;
    private String mPendingCSS;
    /** Popup reached oauth2callback; {@code window.opener} handoff runs before dismiss. */
    private boolean mOAuthCallbackSeen;
    /** True after the main session finishes loading a page (survives brief background). */
    private boolean mMainSessionHasPage;

    public interface LoadErrorCallback {
        void onLoadError(String uri);
    }

    public interface PageLoadCallback {
        void onPageLoadComplete(String uri);
    }

    private LoadErrorCallback mLoadErrorCallback;
    private PageLoadCallback mPageLoadCallback;

    public GeckoViewHelper(Fragment fragment, boolean isDeep) {
        mFragment = fragment;
        mIsDeep = isDeep;
    }

    public void setLoadErrorCallback(LoadErrorCallback callback) {
        mLoadErrorCallback = callback;
    }

    public void setPageLoadCallback(PageLoadCallback callback) {
        mPageLoadCallback = callback;
    }

    /**
     * When enabled, {@code window.open} opens a popup {@link GeckoSession} so OAuth can use
     * {@code window.opener} to hand the auth token back to the Meetecho page.
     */
    public void setOAuthPopupsEnabled(boolean enabled) {
        mOAuthPopupsEnabled = enabled;
    }

    /**
     * Lab pattern: use a TextureView GeckoView that is a sibling of the main Join view.
     * Call before {@link #initialize(GeckoView)}.
     */
    public void setOAuthPopupGeckoView(GeckoView popupView) {
        mOAuthPopupView = popupView;
        mOAuthPopupViewFromLayout = popupView != null;
        if (popupView != null) {
            popupView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
            popupView.setVisibility(View.GONE);
        }
    }

    public void initialize(GeckoView geckoView) {
        if (geckoView != null && mOAuthPopupsEnabled) {
            // Join OAuth lab pattern: TextureView main (sibling of TextureView popup).
            geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        }
        if (mGeckoView == geckoView && mGeckoSession != null) {
            if (geckoView.getSession() != mGeckoSession) {
                Log.d(TAG, "initialize: Reattaching main session to shared GeckoView");
                geckoView.setSession(mGeckoSession);
            }
            if (mPopupSession != null) {
                showOAuthPopupSession(mPopupSession);
            }
            return;
        }

        mGeckoView = geckoView;

        if (mGeckoSession != null) {
            Log.d(TAG, "initialize: Attaching existing main session to GeckoView");
            geckoView.setVisibility(View.VISIBLE);
            geckoView.setSession(mGeckoSession);
            geckoView.bringToFront();
            if (mPopupSession != null) {
                showOAuthPopupSession(mPopupSession);
            }
            return;
        }

        createSession();
    }

    public GeckoView createGeckoView() {
        if (mGeckoView != null && mGeckoSession != null) {
            return mGeckoView;
        }
        mGeckoView = new GeckoView(mFragment.getActivity());
        createSession();
        return mGeckoView;
    }

    private void createSession() {
        if (mGeckoView == null || mFragment.getActivity() == null) {
            return;
        }

        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(mFragment.getActivity());
        if (runtime == null) {
            Log.e(TAG, "Failed to get GeckoRuntime");
            return;
        }

        mGeckoSession = new GeckoSession();
        applyDelegates(mGeckoSession, false);
        mGeckoSession.open(runtime);

        mGeckoView.setFocusable(true);
        mGeckoView.setFocusableInTouchMode(true);
        mGeckoView.setSession(mGeckoSession);
    }

    /** @return unopened session — GeckoView opens it after {@code onNewSession}. */
    private GeckoSession prepareOAuthPopupSession() {
        GeckoSession popup = new GeckoSession();
        applyDelegates(popup, true);
        mPopupSession = popup;
        mOAuthCallbackSeen = false;
        // Keep Meetecho on the main GeckoView (attached) for window.opener handoff.
        // Show the popup in the activity content root (same window) so Paste/ActionMode works.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPopupSession == popup) {
                    showOAuthPopupSession(popup);
                }
            }
        });
        return popup;
    }

    /**
     * Show the OAuth popup. Lab pattern: TextureView sibling of main, parent stays visible
     * and attached for {@code window.opener}. Fallback: activity-content overlay.
     */
    private void showOAuthPopupSession(GeckoSession popup) {
        android.app.Activity activity = mFragment.getActivity();
        if (popup == null || activity == null) {
            Log.w(TAG, "showOAuthPopupSession: activity or popup not ready");
            return;
        }
        // Parent must remain attached and visible (lab) for opener handoff.
        if (mGeckoView != null && mGeckoSession != null && mGeckoView.getSession() != mGeckoSession) {
            Log.d(TAG, "showOAuthPopupSession: ensuring Meetecho parent stays on main GeckoView");
            mGeckoView.setSession(mGeckoSession);
        }
        if (mGeckoView != null) {
            mGeckoView.setVisibility(View.VISIBLE);
        }

        if (mOAuthPopupView == null || !mOAuthPopupViewFromLayout) {
            // Legacy fallback — not used by Join's dedicated layout.
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) {
                Log.w(TAG, "showOAuthPopupSession: android.R.id.content missing");
                return;
            }
            if (mOAuthPopupView == null) {
                mOAuthPopupView = new GeckoView(activity);
                mOAuthPopupView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
                mOAuthPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                mOAuthPopupView.setClickable(true);
                mOAuthPopupView.setFocusable(true);
                mOAuthPopupView.setFocusableInTouchMode(true);
                content.addView(mOAuthPopupView);
                mOAuthPopupViewFromLayout = false;
            }
        }

        popup.setSelectionActionDelegate(new BasicSelectionActionDelegate(activity, true));

        mOAuthPopupView.setVisibility(View.VISIBLE);
        if (mOAuthPopupView.getSession() != popup) {
            Log.d(TAG, "showOAuthPopupSession: attaching popup session"
                    + (mOAuthPopupViewFromLayout ? " to Join sibling" : " to content overlay"));
            mOAuthPopupView.setSession(popup);
        }
        mOAuthPopupView.bringToFront();
        mOAuthPopupView.requestFocus();
        Log.d(TAG, "showOAuthPopupSession: OAuth popup visible (layoutSibling="
                + mOAuthPopupViewFromLayout + ")");
    }

    private void dismissOAuthOverlay() {
        if (mOAuthPopupView == null) {
            return;
        }
        Log.d(TAG, "dismissOAuthOverlay: hiding OAuth popup (layoutSibling="
                + mOAuthPopupViewFromLayout + ")");
        mOAuthPopupView.setVisibility(View.GONE);
        try {
            if (mOAuthPopupView.getSession() != null) {
                mOAuthPopupView.releaseSession();
            }
        } catch (Exception e) {
            Log.w(TAG, "dismissOAuthOverlay: releaseSession failed", e);
        }
        if (!mOAuthPopupViewFromLayout) {
            ViewGroup parent = (ViewGroup) mOAuthPopupView.getParent();
            if (parent != null) {
                parent.removeView(mOAuthPopupView);
            }
            mOAuthPopupView = null;
        }
        if (mGeckoView != null) {
            mGeckoView.setVisibility(View.VISIBLE);
        }
    }

    private void applyDelegates(final GeckoSession session, final boolean isPopup) {
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,
                    GeckoSession.NavigationDelegate.LoadRequest request) {
                String uri = request.uri;
                if (isPopup && uri != null) {
                    updatePopupLocation(uri);
                }
                if (uri != null && uri.startsWith("data:")) {
                    return GeckoResult.allow();
                }

                if (mIsDeep) {
                    return GeckoResult.allow();
                }

                boolean isInitialUrl = (mInitialUrl != null && uri.equals(mInitialUrl))
                        || (mActualInitialUrl != null && uri.equals(mActualInitialUrl));
                boolean isDataUri = uri != null && uri.startsWith("data:");

                if ((isInitialUrl && !mInitialLoadComplete) || isDataUri) {
                    if (!isDataUri) {
                        mInitialLoadComplete = true;
                    }
                    return GeckoResult.allow();
                }

                Log.d(TAG, "onLoadRequest (shallow): Opening link in external browser: " + uri);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mFragment.getActivity().startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL in external browser: " + uri, e);
                }
                return GeckoResult.deny();
            }

            @Override
            public void onCanGoBack(GeckoSession s, boolean canGoBack) {
                if (isPopup) {
                    return;
                }
                if (mCurrentUrl != null && mCurrentUrl.startsWith("data:")) {
                    mCanGoBack = false;
                } else {
                    mCanGoBack = canGoBack;
                }
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession s, String uri,
                    org.mozilla.geckoview.WebRequestError error) {
                Log.w(TAG, "onLoadError: popup=" + isPopup + " uri=" + uri + " error=" + error);
                if (!isPopup && mLoadErrorCallback != null && uri != null && !uri.startsWith("data:")) {
                    mLoadErrorCallback.onLoadError(uri);
                }
                return GeckoResult.fromValue(null);
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                if (isPopup) {
                    Log.d(TAG, "onNewSession: loading nested target in OAuth popup: " + uri);
                    s.loadUri(uri);
                    return GeckoResult.fromValue((GeckoSession) null);
                }
                if (mIsDeep && mOAuthPopupsEnabled) {
                    Log.d(TAG, "onNewSession: OAuth popup for " + uri);
                    return GeckoResult.fromValue(prepareOAuthPopupSession());
                }
                if (mIsDeep) {
                    s.loadUri(uri);
                    return GeckoResult.fromValue((GeckoSession) null);
                }
                Log.d(TAG, "onNewSession (shallow): external browser: " + uri);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mFragment.getActivity().startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL in external browser: " + uri, e);
                }
                return GeckoResult.fromValue((GeckoSession) null);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                if (isPopup) {
                    updatePopupLocation(url);
                } else {
                    mCurrentUrl = url;
                }
                Log.d(TAG, "onPageStart: popup=" + isPopup + " url=" + url);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                String url = isPopup ? mPopupCurrentUrl : mCurrentUrl;
                Log.d(TAG, "onPageStop: popup=" + isPopup + " success=" + success + " url=" + url);
                if (!success) {
                    // Transient failures during redirects/reloads are common; only surface a
                    // user-visible error when navigation has fully stopped on the failed URL.
                    return;
                }
                if (!isPopup && url != null && !url.startsWith("data:")) {
                    mMainSessionHasPage = true;
                    if (mPageLoadCallback != null) {
                        mPageLoadCallback.onPageLoadComplete(url);
                    }
                }
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession s, String title) {
                if (isPopup || mPendingCSS == null || mPendingCSS.isEmpty()) {
                    return;
                }
                mHandler.postDelayed(() -> {
                    injectCSSDirectly(mPendingCSS);
                    mPendingCSS = null;
                }, 100);
            }

            @Override
            public void onCloseRequest(GeckoSession s) {
                if (s != mPopupSession) {
                    return;
                }
                if (shouldIgnoreOAuthPopupClose(mPopupCurrentUrl) && !mOAuthCallbackSeen) {
                    Log.d(TAG, "onCloseRequest: ignored during OAuth login, url=" + mPopupCurrentUrl);
                    return;
                }
                Log.d(TAG, "onCloseRequest: OAuth popup closed, url=" + mPopupCurrentUrl);
                // Page called window.close() after opener handoff — do not reload Meetecho.
                dismissOAuthPopup(false);
            }

            @Override
            public void onFullScreen(GeckoSession s, boolean fullScreen) {
                // Not used
            }
        });
    }

    private void updatePopupLocation(String url) {
        if (url == null) {
            return;
        }
        mPopupCurrentUrl = url;
        Log.d(TAG, "updatePopupLocation: " + url);
        if (looksLikeCloudflareChallenge(url)) {
            Log.w(TAG, "OAuth popup hit Cloudflare challenge URL (COOP may sever window.opener): " + url);
        }
        if (isOAuthCallbackComplete(url)) {
            mOAuthCallbackSeen = true;
        }
        // Do not auto-dismiss or inject javascript: — that interrupts Meetecho's handoff.
    }

    /**
     * No-op retained for SessionDetailFragment lifecycle hooks. Auto-dismiss timers were
     * removed; they interrupted OAuth and killed mid-login when backgrounded.
     */
    public void pauseOAuthTimers() {
        if (mPopupSession != null) {
            Log.d(TAG, "pauseOAuthTimers: OAuth overlay left open while backgrounded");
        }
    }

    public void resumeOAuthTimers() {
        if (mPopupSession != null) {
            Log.d(TAG, "resumeOAuthTimers: ensuring OAuth overlay is showing");
            showOAuthPopupSession(mPopupSession);
        }
    }

    private static boolean looksLikeCloudflareChallenge(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("challenges.cloudflare.com")
                || lower.contains("cdn-cgi/challenge")
                || lower.contains("cdn-cgi/challenge-platform");
    }

    /**
     * Meetecho OIDC success surfaces: gatekeeper oauth2callback, or the
     * {@code auth.conf.meetecho.com/ietf-new} redirect_uri (before the gatekeeper hop).
     */
    private static boolean isOAuthCallbackComplete(String uri) {
        if (uri == null) {
            return false;
        }
        try {
            Uri parsed = Uri.parse(uri);
            String host = parsed.getHost();
            String path = parsed.getPath();
            if (host == null) {
                return false;
            }
            String hostLower = host.toLowerCase(java.util.Locale.ROOT);
            String pathLower = path != null ? path.toLowerCase(java.util.Locale.ROOT) : "";
            if (pathLower.contains("oauth2callback")
                    && (hostLower.endsWith("meetecho.com") || hostLower.endsWith("meetecho.ietf.org"))) {
                return true;
            }
            return hostLower.equals("auth.conf.meetecho.com") && pathLower.contains("/ietf-new");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Datatracker login pages sometimes emit spurious {@code window.close()} on input focus;
     * dismissing the popup there would flash the Meetecho login gate behind the form.
     */
    private static boolean shouldIgnoreOAuthPopupClose(String url) {
        if (url == null) {
            return true;
        }
        if (isOAuthCallbackComplete(url)) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("auth.ietf.org")) {
            return true;
        }
        if (lower.contains("datatracker.ietf.org")) {
            return true;
        }
        if (lower.contains("challenges.cloudflare.com") || lower.contains("cloudflare.com/cdn-cgi")) {
            return true;
        }
        return false;
    }

    /**
     * Close the OAuth overlay and popup session. Never reloads Meetecho — the opener handoff
     * already updated the parent session in memory.
     */
    public void dismissOAuthPopup(boolean reloadMeetecho) {
        if (mPopupSession == null) {
            dismissOAuthOverlay();
            return;
        }
        boolean oauthCompleted = mOAuthCallbackSeen;
        Log.d(TAG, "dismissOAuthPopup: closing overlay, oauthCompleted="
                + oauthCompleted + ", reloadMeetecho=" + reloadMeetecho + " (ignored)");
        GeckoSession popup = mPopupSession;
        mPopupSession = null;
        mPopupCurrentUrl = null;
        mOAuthCallbackSeen = false;
        try {
            popup.setSelectionActionDelegate(null);
        } catch (Exception e) {
            Log.w(TAG, "dismissOAuthPopup: clearing selection delegate", e);
        }
        dismissOAuthOverlay();
        try {
            popup.close();
        } catch (Exception e) {
            Log.w(TAG, "dismissOAuthPopup: error closing popup", e);
        }
    }

    public void dismissOAuthPopup() {
        dismissOAuthPopup(false);
    }

    public void dismissOAuthPopupIfOpen() {
        dismissOAuthPopup();
    }

    public boolean isOAuthPopupOpen() {
        return mPopupSession != null;
    }

    /** Whether the main session has loaded content and should not be reloaded on resume. */
    public boolean hasMainSessionContent() {
        return mMainSessionHasPage || mCurrentUrl != null;
    }

    public void loadUrl(String url) {
        dismissOAuthPopup();
        if (mGeckoSession == null) {
            Log.w(TAG, "loadUrl: GeckoSession not initialized");
            return;
        }
        if (url != null && url.startsWith("data:")) {
            mCanGoBack = false;
        }
        if (mActualInitialUrl == null) {
            mActualInitialUrl = url;
        }
        if (mInitialUrl == null) {
            mInitialUrl = url;
        }
        mGeckoSession.loadUri(url);
    }

    public void injectCSS(String css) {
        if (mGeckoSession == null || mFragment.getActivity() == null) {
            Log.w(TAG, "injectCSS: GeckoSession or Activity not initialized");
            return;
        }
        mPendingCSS = css;
        injectCSSDirectly(css);
        mHandler.postDelayed(() -> {
            if (mPendingCSS != null && mPendingCSS.equals(css)) {
                injectCSSDirectly(css);
            }
        }, 500);
    }

    private void injectCSSDirectly(String css) {
        if (mGeckoSession == null || css == null || css.isEmpty()) {
            return;
        }
        String escapedCSS = css.replace("\\", "\\\\")
                              .replace("'", "\\'")
                              .replace("\n", "\\n")
                              .replace("\r", "")
                              .replace("\"", "\\\"");
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
        String[] methodNames = {"evaluateJavascript", "evaluateJavaScript", "evaluateJS", "executeScript", "evaluate"};
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = mGeckoSession.getClass().getMethod(methodName, String.class);
                method.invoke(mGeckoSession, js);
                return;
            } catch (NoSuchMethodException e) {
                continue;
            } catch (Exception e) {
                Log.w(TAG, "injectCSSDirectly: Failed to call " + methodName, e);
            }
        }
        Log.w(TAG, "injectCSSDirectly: No JavaScript evaluation method found");
    }

    public void reinitialize() {
        dismissOAuthPopup();
        if (mGeckoSession != null) {
            Log.d(TAG, "reinitialize: Closing old GeckoSession");
            if (mGeckoView != null) {
                mGeckoView.setSession(null);
            }
            mGeckoSession.close();
            mGeckoSession = null;
        }
        mCanGoBack = false;
        mInitialUrl = null;
        mActualInitialUrl = null;
        mInitialLoadComplete = false;
        mCurrentUrl = null;
        mMainSessionHasPage = false;
        createSession();
    }

    public boolean onBackPressed() {
        if (mPopupSession != null) {
            dismissOAuthPopup();
            return true;
        }
        if (mGeckoSession != null && mCanGoBack) {
            Log.d(TAG, "onBackPressed: Navigating back in GeckoView");
            mGeckoSession.goBack();
            return true;
        }
        return false;
    }

    public GeckoView getGeckoView() {
        return mGeckoView;
    }

    public GeckoSession getGeckoSession() {
        return mGeckoSession;
    }

    public boolean canGoBack() {
        return mPopupSession != null || mCanGoBack;
    }

    public String getCurrentUrl() {
        return mCurrentUrl;
    }

    public boolean isDeep() {
        return mIsDeep;
    }

    public void cleanup() {
        dismissOAuthPopup();
        if (!mOAuthPopupViewFromLayout) {
            mOAuthPopupView = null;
        }
        if (mGeckoView != null) {
            try {
                if (mGeckoView.getSession() != null) {
                    mGeckoView.setSession(null);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error detaching session during cleanup", e);
            }
        }
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
        mCurrentUrl = null;
        mMainSessionHasPage = false;
    }
}
