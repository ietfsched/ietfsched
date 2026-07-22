/*
 * Debug-only GeckoView OAuth lab for iterating on Meetecho Datatracker popup login.
 *
 * Launch:
 *   adb shell am start -n org.ietf.ietfsched/.ui.DebugOAuthActivity
 *
 * Optional extras:
 *   --es url "https://meetings.conf.meetecho.com/onsite126/?group=gaia"
 *   --es dt_user "user@example.com" --es dt_pass "secret"
 *   --ez clear_meetecho true
 *     (drop Meetecho site data only; keep auth.ietf.org / Datatracker cookies)
 */

package org.ietf.ietfsched.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.ietf.ietfsched.R;
import org.ietf.ietfsched.util.GeckoRuntimeHelper;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.BasicSelectionActionDelegate;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;

/**
 * Minimal harness: main Meetecho session + real popup session for window.open OAuth.
 * Both GeckoViews use TextureView so z-order and Paste ActionMode stay in one window.
 */
public class DebugOAuthActivity extends Activity {
    private static final String TAG = "DebugOAuth";
    private static final String DEFAULT_URL =
            "https://meetings.conf.meetecho.com/onsite126/?group=gaia";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private EditText mUrlField;
    private TextView mStatus;
    private GeckoView mMainView;
    private GeckoView mPopupView;
    private GeckoSession mMainSession;
    private GeckoSession mPopupSession;
    private String mPopupUrl;
    private String mDtUser;
    private String mDtPass;
    private boolean mLoginFillAttempted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_oauth);
        // Keep URL/Load row below the status bar (otherwise Load is untappable).
        View root = findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            View bar = findViewById(R.id.debug_oauth_toolbar);
            if (bar != null) {
                bar.setPadding(bar.getPaddingLeft(), top + 8, bar.getPaddingRight(),
                        bar.getPaddingBottom());
            }
            return insets;
        });

        mUrlField = findViewById(R.id.debug_oauth_url);
        mStatus = findViewById(R.id.debug_oauth_status);
        mMainView = findViewById(R.id.debug_oauth_main);
        mPopupView = findViewById(R.id.debug_oauth_popup);
        Button loadButton = findViewById(R.id.debug_oauth_load);
        Button clearMeetechoButton = findViewById(R.id.debug_oauth_clear_meetecho);

        // TextureView: same-window stacking + floating Paste toolbar work reliably.
        mMainView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        mPopupView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);

        String startUrl = DEFAULT_URL;
        boolean clearMeetecho = false;
        if (getIntent() != null && getIntent().hasExtra("url")) {
            startUrl = getIntent().getStringExtra("url");
        }
        if (getIntent() != null) {
            mDtUser = getIntent().getStringExtra("dt_user");
            mDtPass = getIntent().getStringExtra("dt_pass");
            clearMeetecho = getIntent().getBooleanExtra("clear_meetecho", false);
        }
        mUrlField.setText(startUrl);

        loadButton.setOnClickListener(v -> loadMain(mUrlField.getText().toString().trim()));
        clearMeetechoButton.setOnClickListener(
                v -> clearMeetechoSiteDataThenReload(mUrlField.getText().toString().trim()));

        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(this);
        if (runtime == null) {
            setStatus("ERROR: no GeckoRuntime");
            return;
        }

        openMainSession(runtime);

        final String urlToLoad = startUrl;
        final boolean doClear = clearMeetecho;
        setStatus("Waiting for GeckoRuntime…");
        // loadUri before Gecko reaches RUNNING is dropped; defer until ready.
        mHandler.postDelayed(() -> {
            if (doClear) {
                clearMeetechoSiteDataThenReload(urlToLoad);
            } else {
                setStatus("Lab ready — loading Meetecho");
                loadMain(urlToLoad);
            }
        }, 1500);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("clear_meetecho", false)) {
            Log.i(TAG, "onNewIntent: clear_meetecho");
            clearMeetechoSiteDataThenReload(mUrlField != null
                    ? mUrlField.getText().toString().trim()
                    : DEFAULT_URL);
        }
    }

    private void openMainSession(GeckoRuntime runtime) {
        mMainSession = new GeckoSession();
        applyMainDelegates(mMainSession);
        mMainSession.setSelectionActionDelegate(new BasicSelectionActionDelegate(this, true));
        mMainSession.open(runtime);
        mMainView.setSession(mMainSession);
    }

    /**
     * Drop Meetecho cookies/storage so Join shows the login gate again, while leaving
     * Datatracker ({@code auth.ietf.org}) cookies intact for quick OAuth re-auth.
     * Uses host clears (not ietf.org base-domain) so DT session survives.
     */
    private void clearMeetechoSiteDataThenReload(String url) {
        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(this);
        if (runtime == null) {
            setStatus("ERROR: no GeckoRuntime");
            return;
        }
        dismissPopup("clearMeetecho");
        setStatus("Clearing Meetecho site data…");
        Log.i(TAG, "clearMeetechoSiteData: releasing session then clearing hosts");

        if (mMainView != null) {
            try {
                GeckoSession released = mMainView.releaseSession();
                if (released != null) {
                    released.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "releaseSession failed", e);
            }
        }
        if (mMainSession != null) {
            try {
                // May already be closed via releaseSession path.
                mMainSession.close();
            } catch (Exception ignored) {
            }
            mMainSession = null;
        }

        StorageController storage = runtime.getStorageController();
        // ALL on Meetecho hosts only — SITE_DATA left a live Meetecho session behind.
        long flags = StorageController.ClearFlags.ALL;
        // Order: base domain meetecho.com, then explicit hosts (incl. meetecho.ietf.org).
        // Never clearDataFromBaseDomain("ietf.org") — that would wipe Datatracker.
        String[] hosts = new String[] {
                "meetecho.ietf.org",
                "meetings.conf.meetecho.com",
                "auth.conf.meetecho.com",
        };
        storage.clearDataFromBaseDomain("meetecho.com", flags).accept(
                unused -> {
                    Log.i(TAG, "cleared baseDomain meetecho.com");
                    clearHostsThenReload(storage, hosts, 0, flags, runtime, url);
                },
                error -> {
                    Log.w(TAG, "clearDataFromBaseDomain(meetecho.com) failed", error);
                    clearHostsThenReload(storage, hosts, 0, flags, runtime, url);
                });
    }

    private void wipeMeetechoDomStorageAndReload() {
        if (mMainSession == null) {
            return;
        }
        Log.i(TAG, "wipeMeetechoDomStorageAndReload via javascript:");
        setStatus("Wiping Meetecho DOM storage…");
        // Non-secret: clear client-side session leftovers then reload.
        String js = "javascript:(function(){"
                + "try{localStorage.clear();sessionStorage.clear();}catch(e){}"
                + "var done=function(){location.reload();};"
                + "if(indexedDB&&indexedDB.databases){"
                + "indexedDB.databases().then(function(dbs){"
                + "return Promise.all((dbs||[]).map(function(d){"
                + "return d&&d.name?new Promise(function(res){"
                + "var r=indexedDB.deleteDatabase(d.name);r.onsuccess=r.onerror=r.onblocked=function(){res();};"
                + "}):Promise.resolve();}));"
                + "}).then(done).catch(done);"
                + "}else{done();}"
                + "})()";
        try {
            mMainSession.loadUri(js);
        } catch (Exception e) {
            Log.w(TAG, "DOM wipe failed", e);
        }
    }

    private void clearHostsThenReload(StorageController storage, String[] hosts, int index,
            long flags, GeckoRuntime runtime, String url) {
        if (index >= hosts.length) {
            runOnUiThread(() -> {
                Log.i(TAG, "Meetecho site data cleared — reopening main");
                setStatus("Meetecho cleared — reloading (DT cookies kept)");
                openMainSession(runtime);
                // Give StorageController a moment to flush before the next load.
                mHandler.postDelayed(() -> {
                    loadMain(url);
                    // Wipe JS-visible storage after first paint in case http-clears missed it.
                    mHandler.postDelayed(() -> wipeMeetechoDomStorageAndReload(), 2500);
                }, 1500);
            });
            return;
        }
        final String host = hosts[index];
        Log.i(TAG, "clearDataFromHost: " + host);
        storage.clearDataFromHost(host, flags).accept(
                unused -> clearHostsThenReload(storage, hosts, index + 1, flags, runtime, url),
                error -> {
                    Log.w(TAG, "clearDataFromHost failed: " + host, error);
                    clearHostsThenReload(storage, hosts, index + 1, flags, runtime, url);
                });
    }

    private void loadMain(String url) {
        if (TextUtils.isEmpty(url) || mMainSession == null) {
            return;
        }
        dismissPopup("loadMain");
        setStatus("Loading main: " + url);
        Log.i(TAG, "loadMain: " + url);
        mMainSession.loadUri(url);
    }

    private void applyMainDelegates(GeckoSession session) {
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,
                    GeckoSession.NavigationDelegate.LoadRequest request) {
                Log.d(TAG, "main onLoadRequest: " + request.uri);
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                Log.i(TAG, "main onNewSession (OAuth popup): " + uri);
                setStatus("Popup requested: " + shorten(uri));
                return GeckoResult.fromValue(preparePopupSession());
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession s, String uri,
                    org.mozilla.geckoview.WebRequestError error) {
                Log.e(TAG, "main onLoadError uri=" + uri
                        + " category=" + error.code + "/" + error.category
                        + " error=" + error);
                setStatus("Main load error: " + shorten(uri) + " (" + error.code + ")");
                return GeckoResult.fromValue(null);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                Log.d(TAG, "main onPageStart: " + url);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                Log.d(TAG, "main onPageStop success=" + success);
                if (mPopupSession == null) {
                    setStatus("Main loaded (success=" + success + ")");
                }
            }
        });
    }

    /** Unopened session — Gecko opens it after onNewSession returns. */
    private GeckoSession preparePopupSession() {
        dismissPopup("replace");
        GeckoSession popup = new GeckoSession();
        mPopupSession = popup;
        mPopupUrl = null;
        mLoginFillAttempted = false;
        applyPopupDelegates(popup);
        popup.setSelectionActionDelegate(new BasicSelectionActionDelegate(this, true));
        // Attach after Gecko opens the session from onNewSession.
        mHandler.post(() -> {
            if (mPopupSession != popup) {
                return;
            }
            Log.i(TAG, "showPopup: attaching to popup GeckoView (main stays attached)");
            mPopupView.setVisibility(View.VISIBLE);
            mPopupView.setSession(popup);
            mPopupView.bringToFront();
            mPopupView.requestFocus();
            setStatus("Popup visible — complete Datatracker login");
        });
        return popup;
    }

    private void applyPopupDelegates(GeckoSession session) {
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,
                    GeckoSession.NavigationDelegate.LoadRequest request) {
                if (request.uri != null && !request.uri.startsWith("javascript:")) {
                    mPopupUrl = request.uri;
                    Log.d(TAG, "popup onLoadRequest: " + request.uri);
                }
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                Log.w(TAG, "popup nested onNewSession — loading in popup: " + uri);
                s.loadUri(uri);
                return GeckoResult.fromValue(null);
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession s, String uri,
                    org.mozilla.geckoview.WebRequestError error) {
                Log.e(TAG, "popup onLoadError uri=" + uri
                        + " code=" + error.code + " category=" + error.category
                        + " error=" + error);
                setStatus("Popup load error code=" + error.code + " cat=" + error.category);
                return GeckoResult.fromValue(null);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                mPopupUrl = url;
                Log.d(TAG, "popup onPageStart: " + url);
                setStatus("Popup: " + shorten(url));
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                Log.d(TAG, "popup onPageStop success=" + success + " url=" + mPopupUrl);
                if (success && isDatatrackerLogin(mPopupUrl)) {
                    maybeFillDatatrackerLogin(s);
                }
                if (success && isOAuthCallback(mPopupUrl)) {
                    setStatus("oauth callback — waiting for window.close / opener handoff");
                    Log.i(TAG, "Reached OAuth callback; leaving page JS to hand off via window.opener");
                }
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onCloseRequest(GeckoSession s) {
                if (s != mPopupSession) {
                    return;
                }
                Log.i(TAG, "popup onCloseRequest url=" + mPopupUrl);
                setStatus("Popup closed by page — restoring main (no reload)");
                dismissPopup("onCloseRequest");
            }
        });
    }

    private void dismissPopup(String reason) {
        if (mPopupSession == null && mPopupView.getVisibility() != View.VISIBLE) {
            return;
        }
        Log.i(TAG, "dismissPopup reason=" + reason);
        GeckoSession popup = mPopupSession;
        mPopupSession = null;
        mPopupUrl = null;
        mPopupView.setVisibility(View.GONE);
        if (popup != null) {
            try {
                popup.setSelectionActionDelegate(null);
            } catch (Exception ignored) {
            }
            try {
                popup.close();
            } catch (Exception e) {
                Log.w(TAG, "popup.close failed", e);
            }
        }
        mMainView.bringToFront();
        mMainView.requestFocus();
    }

    private static boolean isDatatrackerLogin(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.contains("auth.ietf.org") && lower.contains("/accounts/login");
    }

    private void maybeFillDatatrackerLogin(GeckoSession session) {
        if (mLoginFillAttempted || TextUtils.isEmpty(mDtUser) || TextUtils.isEmpty(mDtPass)) {
            return;
        }
        mLoginFillAttempted = true;
        Log.i(TAG, "Auto-filling Datatracker login via JS (dt_user intent extra)");
        setStatus("Auto-filling Datatracker login…");
        String user = escapeJsString(mDtUser);
        String pass = escapeJsString(mDtPass);
        String js = "(function(){"
                + "var u=document.querySelector('input[name=username],#id_username,input[type=text]');"
                + "var p=document.querySelector('input[name=password],#id_password,input[type=password]');"
                + "var f=document.querySelector('form');"
                + "if(!u||!p){console.log('ietfsched: login fields missing');return;}"
                + "u.value='" + user + "';"
                + "p.value='" + pass + "';"
                + "u.dispatchEvent(new Event('input',{bubbles:true}));"
                + "p.dispatchEvent(new Event('input',{bubbles:true}));"
                + "if(f){f.submit();}else{p.form&&p.form.submit();}"
                + "})();";
        // Delay slightly so the login DOM is interactive.
        mHandler.postDelayed(() -> evaluateJs(session, js), 400);
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void evaluateJs(GeckoSession session, String js) {
        String[] methodNames = {
                "evaluateJavascript", "evaluateJavaScript", "evaluateJS", "executeScript", "evaluate"
        };
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = session.getClass().getMethod(methodName, String.class);
                method.invoke(session, js);
                Log.i(TAG, "evaluateJs via " + methodName);
                return;
            } catch (NoSuchMethodException e) {
                // try next
            } catch (Exception e) {
                Log.w(TAG, "evaluateJs failed for " + methodName, e);
            }
        }
        // Do not fall back to javascript: URIs — NavigationDelegate logs them and
        // would leak credentials (dt_pass) into logcat.
        Log.e(TAG, "evaluateJs: no JS evaluation method; refusing javascript: fallback");
        setStatus("JS fill unavailable — enter Datatracker login manually");
    }

    private static boolean isOAuthCallback(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        if (lower.contains("oauth2callback")
                && (lower.contains("meetecho.com") || lower.contains("meetecho.ietf.org"))) {
            return true;
        }
        // Meetecho OIDC redirect_uri used by onsite/lite.
        return lower.contains("auth.conf.meetecho.com") && lower.contains("/ietf-new");
    }

    private void setStatus(String text) {
        runOnUiThread(() -> mStatus.setText(text));
    }

    private static String shorten(String url) {
        if (url == null) {
            return "null";
        }
        return url.length() <= 80 ? url : url.substring(0, 77) + "...";
    }

    @Override
    public void onBackPressed() {
        if (mPopupSession != null) {
            setStatus("Back — dismissed popup");
            dismissPopup("back");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        dismissPopup("destroy");
        if (mMainSession != null) {
            try {
                mMainSession.close();
            } catch (Exception ignored) {
            }
            mMainSession = null;
        }
        super.onDestroy();
    }
}
