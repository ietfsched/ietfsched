/*
 * Copyright 2011 Isabelle Dalmasso
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

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.ietf.ietfsched.service.SyncService;
import org.ietf.ietfsched.util.GeckoRuntimeHelper;


/**
 * A fragment that shows the IETF Well Note.
 */
public class WellNoteFragment extends Fragment {
    private static final String TAG = "WellNoteFragment";
    private GeckoView mGeckoView;
    private GeckoSession mGeckoSession;
    private boolean mCanGoBack = false;
    private MyNavigationDelegate mNavigationDelegate = new MyNavigationDelegate();
    
    /**
     * NavigationDelegate that allows all navigation within GeckoView.
     * GeckoView has built-in history management - we just allow navigation and let it work.
     */
    private class MyNavigationDelegate implements GeckoSession.NavigationDelegate {
        @Override
        public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, GeckoSession.NavigationDelegate.LoadRequest request) {
            // Allow all navigation - GeckoView handles history automatically
            return GeckoResult.allow();
        }
        
        @Override
        public void onCanGoBack(GeckoSession session, boolean canGoBack) {
            WellNoteFragment.this.mCanGoBack = canGoBack;
        }
        
        @Override
        public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
            Log.d(TAG, "New session requested for: " + uri);
            // Load the URI in the current session
            session.loadUri(uri);
            // Return null to deny new session creation
            return GeckoResult.fromValue((GeckoSession) null);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain fragment instance across configuration changes to preserve GeckoView state
        setRetainInstance(true);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Reuse existing GeckoView if available (from retained instance)
        if (mGeckoView != null) {
            ViewGroup parent = (ViewGroup) mGeckoView.getParent();
            if (parent != null) {
                parent.removeView(mGeckoView);
            }
            return mGeckoView;
        }
        
        // Create GeckoView
        mGeckoView = new GeckoView(getActivity());
        
        // Get shared GeckoRuntime instance
        GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(getActivity());
        if (runtime == null) {
            Log.e(TAG, "Failed to get GeckoRuntime");
            return mGeckoView; // Return view even if runtime failed
        }
        
        // Create GeckoSession and attach navigation delegate
        mGeckoSession = new GeckoSession();
        mGeckoSession.setNavigationDelegate(mNavigationDelegate);
        
        // Open session and attach to view
        mGeckoSession.open(runtime);
        mGeckoView.setSession(mGeckoSession);
        
        // Load Note Well content
        loadNoteWellContent();
        
        return mGeckoView;
    }
    
    /**
     * Load Note Well content into GeckoView.
     */
    private void loadNoteWellContent() {
        if (mGeckoSession == null || getActivity() == null) {
            return;
        }
        
        String markdownText;
        
        // Check if Note Well data is available in memory
        if (SyncService.noteWellString != null && SyncService.noteWellString.length() > 0) {
            markdownText = SyncService.noteWellString;
        } else {
            // Try to load from SharedPreferences (persists across app restarts)
            // Must use the same SharedPreferences file as SyncService
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ietfsched_sync", android.content.Context.MODE_PRIVATE);
            markdownText = prefs.getString("note_well_content", "");
            
            if (markdownText.length() == 0) {
                // Not yet downloaded - show a message
                markdownText = "Note Well text is being downloaded. Please check back in a moment or use the Refresh button.";
            } else {
                // Restore to static variable for future access
                SyncService.noteWellString = markdownText;
            }
        }
        
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
        
        // Load HTML using Base64 encoding (same approach as SessionDetailFragment)
        try {
            byte[] htmlBytes = styledHtml.getBytes("UTF-8");
            String base64Html = android.util.Base64.encodeToString(htmlBytes, android.util.Base64.NO_WRAP);
            String dataUri = "data:text/html;charset=utf-8;base64," + base64Html;
            mGeckoSession.loadUri(dataUri);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to encode Note Well HTML", e);
        }
    }
    
    /**
     * Handle back button press - navigate back in GeckoView if possible.
     * @return true if GeckoView handled the back press, false otherwise
     */
    public boolean onBackPressed() {
        if (mGeckoSession != null && mCanGoBack) {
            mGeckoSession.goBack();
            return true;
        }
        return false;
    }
}
