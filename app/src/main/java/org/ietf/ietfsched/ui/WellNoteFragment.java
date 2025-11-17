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

import org.ietf.ietfsched.R;

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.ietf.ietfsched.service.SyncService;


/**
 * A fragment that shows the IETF Well Note.
 */
public class WellNoteFragment extends Fragment {
    private WebView mWebView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        
        // Create WebView
        mWebView = new WebView(getActivity());
        mWebView.setWebViewClient(new WebViewClient()); // Keep links within WebView
        mWebView.getSettings().setJavaScriptEnabled(false); // No JS needed
        mWebView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null);
        
        return mWebView;
    }
    
    /**
     * Handle back button press - navigate back in WebView if possible.
     * @return true if WebView handled the back press, false otherwise
     */
    public boolean onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }
}
