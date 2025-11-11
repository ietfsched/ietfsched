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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * A fragment that shows a session agenda in a WebView.
 * Links within the agenda open in an external browser.
 */
public class AgendaFragment extends Fragment {
    private WebView mWebView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get agenda URL from activity's intent
        String agendaUrl = getActivity().getIntent().getStringExtra(AgendaActivity.EXTRA_AGENDA_URL);
        
        if (agendaUrl == null || agendaUrl.isEmpty()) {
            // No URL provided, show error message
            agendaUrl = "data:text/html,<html><body><p style='padding:16px;'>No agenda URL available.</p></body></html>";
        }
        
        // Create WebView
        mWebView = new WebView(getActivity());
        
        // Configure WebView to open all links in external browser
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Open link in external browser
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                startActivity(intent);
                return true; // Consume the event, don't load in WebView
            }
        });
        
        mWebView.getSettings().setJavaScriptEnabled(false); // No JS needed for agendas
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setDisplayZoomControls(false);
        mWebView.loadUrl(agendaUrl);
        
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

