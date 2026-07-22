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
    /** Safety net if the Success page never calls {@code window.close()}. */
    private static final long OAUTH_POPUP_SAFETY_DISMISS_MS = 15000;
    /** Time for oauth2callback JS / cookie commit before returning to Meetecho. */
    private static final long OAUTH_CALLBACK_DISMISS_MS = 2500;

    private final Fragment mFragment;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private GeckoView mGeckoView;
    /** Overlay for OAuth popup — main {@link #mGeckoView} keeps the Meetecho session for {@code window.opener}. */
    private GeckoView mOAuthPopupView;
    /** Primary session (Meetecho page, notes, etc.). */
    private GeckoSession mGeckoSession;
    /** OAuth popup while Datatracker login runs; parent keeps Meetecho for {@code window.opener}. */
    private GeckoSession mPopupSession;
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

    /** Fired after OAuth popup closes following a successful oauth2callback. */
    public interface OAuthPopupDismissCallback {
        void onOAuthPopupDismissed();
    }

    private LoadErrorCallback mLoadErrorCallback;
    private PageLoadCallback mPageLoadCallback;
    private OAuthPopupDismissCallback mOAuthPopupDismissCallback;

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

    public void setOAuthPopupDismissCallback(OAuthPopupDismissCallback callback) {
        mOAuthPopupDismissCallback = callback;
    }

    /**
     * When enabled, {@code window.open} opens a popup {@link GeckoSession} so OAuth can use
     * {@code window.opener} to hand the auth token back to the Meetecho page.
     */
    public void setOAuthPopupsEnabled(boolean enabled) {
        mOAuthPopupsEnabled = enabled;
    }

    public void initialize(GeckoView geckoView) {
        if (mGeckoView == geckoView && mGeckoSession != null) {
            if (geckoView.getSession() != mGeckoSession) {
                Log.d(TAG, "initialize: Reattaching main session to shared GeckoView");
                geckoView.setSession(mGeckoSession);
            }
            if (mPopupSession != null) {
                attachOAuthPopupToOverlay(mPopupSession);
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
                attachOAuthPopupToOverlay(mPopupSession);
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPopupSession == popup) {
                    attachOAuthPopupToOverlay(popup);
                }
            }
        });
        return popup;
    }

    private void attachOAuthPopupToOverlay(GeckoSession popup) {
        if (mGeckoView == null || mFragment.getActivity() == null) {
            Log.w(TAG, "attachOAuthPopupToOverlay: main GeckoView not ready");
            return;
        }
        android.view.ViewGroup container = null;
        if (mGeckoView.getParent() instanceof android.view.ViewGroup) {
            container = (android.view.ViewGroup) mGeckoView.getParent();
        }
        if (container == null) {
            Log.w(TAG, "attachOAuthPopupToOverlay: no parent container");
            return;
        }
        if (mOAuthPopupView == null) {
            mOAuthPopupView = new GeckoView(mFragment.getActivity());
            mOAuthPopupView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            container.addView(mOAuthPopupView);
        }
        Log.d(TAG, "attachOAuthPopupToOverlay: showing popup overlay (main session stays on shared GeckoView)");
        mOAuthPopupView.setVisibility(View.VISIBLE);
        mOAuthPopupView.setSession(popup);
        mOAuthPopupView.bringToFront();
    }

    private void detachOAuthPopupOverlay() {
        if (mOAuthPopupView != null) {
            // Do not call setSession(null) — GeckoView throws if Owner is null.
            mOAuthPopupView.setVisibility(View.GONE);
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
                if (isPopup && success && isOAuthCallbackComplete(url)) {
                    scheduleOAuthCallbackDismiss();
                    requestPopupClose();
                }
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
        if (isOAuthCallbackComplete(url)) {
            mOAuthCallbackSeen = true;
            scheduleOAuthCallbackDismiss();
        } else {
            mHandler.removeCallbacks(mDismissOAuthPopupRunnable);
        }
    }

    private void scheduleOAuthCallbackDismiss() {
        mHandler.removeCallbacks(mDismissOAuthPopupRunnable);
        mHandler.postDelayed(mDismissOAuthPopupRunnable, OAUTH_CALLBACK_DISMISS_MS);
    }

    /** oauth2callback pages often never call {@code window.close()} in embedded GeckoView. */
    private void requestPopupClose() {
        if (mPopupSession == null) {
            return;
        }
        mHandler.postDelayed(() -> {
            if (mPopupSession == null) {
                return;
            }
            evaluateJavaScript(mPopupSession,
                    "try { if (window.opener && !window.opener.closed) { window.opener.focus(); }"
                            + " window.close(); } catch (e) { console.log('oauth close:', e); }");
        }, 500);
    }

    private void evaluateJavaScript(GeckoSession session, String js) {
        if (session == null) {
            return;
        }
        String[] methodNames = {"evaluateJavascript", "evaluateJavaScript", "evaluateJS", "executeScript", "evaluate"};
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = session.getClass().getMethod(methodName, String.class);
                method.invoke(session, js);
                return;
            } catch (NoSuchMethodException e) {
                continue;
            } catch (Exception e) {
                Log.w(TAG, "evaluateJavaScript: Failed to call " + methodName, e);
            }
        }
    }

    /** Final Meetecho callback path — not redirect_uri mentions in query params. */
    private static boolean isOAuthCallbackComplete(String uri) {
        if (uri == null) {
            return false;
        }
        try {
            Uri parsed = Uri.parse(uri);
            String host = parsed.getHost();
            String path = parsed.getPath();
            if (host == null || path == null || !path.contains("oauth2callback")) {
                return false;
            }
            return host.endsWith("meetecho.com") || host.endsWith("meetecho.ietf.org");
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

    private void scheduleOAuthSafetyDismiss() {
        mHandler.removeCallbacks(mDismissOAuthPopupRunnable);
        mHandler.postDelayed(mDismissOAuthPopupRunnable, OAUTH_POPUP_SAFETY_DISMISS_MS);
    }

    private final Runnable mDismissOAuthPopupRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "OAuth popup callback timeout — reloading Meetecho (window.close did not fire)");
            dismissOAuthPopup(true);
        }
    };

    /**
     * Return to Meetecho parent session.
     *
     * @param reloadMeetecho If true, caller reloads Meetecho (cookie fallback when {@code window.close}
     *                       never ran). If false, trust {@code window.opener} handoff — reloading would
     *                       reset the parent page and show the login gate again.
     */
    public void dismissOAuthPopup(boolean reloadMeetecho) {
        mHandler.removeCallbacks(mDismissOAuthPopupRunnable);
        if (mPopupSession == null) {
            return;
        }
        boolean oauthCompleted = mOAuthCallbackSeen;
        Log.d(TAG, "dismissOAuthPopup: returning to main session, oauthCompleted="
                + oauthCompleted + ", reloadMeetecho=" + reloadMeetecho);
        GeckoSession popup = mPopupSession;
        mPopupSession = null;
        mPopupCurrentUrl = null;
        mOAuthCallbackSeen = false;
        try {
            popup.close();
        } catch (Exception e) {
            Log.w(TAG, "dismissOAuthPopup: error closing popup", e);
        }
        detachOAuthPopupOverlay();
        if (mGeckoView != null && mGeckoSession != null && mGeckoView.getSession() != mGeckoSession) {
            mGeckoView.setSession(mGeckoSession);
        }
        if (oauthCompleted && reloadMeetecho && mOAuthPopupDismissCallback != null) {
            mOAuthPopupDismissCallback.onOAuthPopupDismissed();
        }
    }

    public void dismissOAuthPopup() {
        dismissOAuthPopup(false);
    }

    /** Reload the main session without touching an OAuth popup (popup already dismissed). */
    public void reloadMainUrl(String url) {
        if (mGeckoSession == null || url == null) {
            return;
        }
        Log.d(TAG, "reloadMainUrl: " + url);
        mGeckoSession.loadUri(url);
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
        if (mOAuthPopupView != null) {
            android.view.ViewGroup parent = (android.view.ViewGroup) mOAuthPopupView.getParent();
            if (parent != null) {
                parent.removeView(mOAuthPopupView);
            }
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
