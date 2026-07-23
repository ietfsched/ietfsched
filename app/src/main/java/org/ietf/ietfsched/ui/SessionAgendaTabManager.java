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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.net.Uri;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.R;
import org.ietf.ietfsched.io.RemoteExecutor;
import org.ietf.ietfsched.util.MarkdownHtml;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Manages the Agenda tab using standard Android WebView (for easy CSS injection).
 */
public class SessionAgendaTabManager {
    private static final String TAG = "SessionAgendaTabManager";
    private static final String TAB_AGENDA = "agenda";
    
    private final Fragment mFragment;
    private final ViewGroup mRootView;
    private final android.widget.TabHost mTabHost;
    private WebView mWebView;
    private String mCurrentUrl;
    
    // Regex to detect common HTML tags and DOCTYPE
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<(!DOCTYPE html|html|body|div|p|h[1-6]|table|ul|ol|li|a|img|script|style)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    
    public SessionAgendaTabManager(Fragment fragment, ViewGroup rootView, android.widget.TabHost tabHost) {
        mFragment = fragment;
        mRootView = rootView;
        mTabHost = tabHost;
    }
    
    /**
     * Initialize WebView for Agenda tab.
     */
    private void initializeWebView() {
        if (mWebView != null) {
            return; // Already initialized
        }
        
        ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.tab_session_links);
        if (container == null) {
            Log.e(TAG, "initializeWebView: Container not found");
            return;
        }
        
        // Create WebView if it doesn't exist
        if (container.getChildCount() == 0) {
            mWebView = new WebView(mFragment.getActivity());
            container.addView(mWebView);
        } else {
            View child = container.getChildAt(0);
            if (child instanceof WebView) {
                mWebView = (WebView) child;
            } else {
                // Remove existing view and create WebView
                container.removeAllViews();
                mWebView = new WebView(mFragment.getActivity());
                container.addView(mWebView);
            }
        }
        
        // Configure WebView settings
        android.webkit.WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        
        // Set WebViewClient to handle navigation (all links open externally)
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Allow data URIs (our wrapped content) - these are the initial loads
                if (url.startsWith("data:")) {
                    return false; // Let WebView handle data URIs
                }
                // Open ALL other links externally (including links clicked in the content)
                Log.d(TAG, "shouldOverrideUrlLoading: Opening link externally: " + url);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mFragment.getActivity().startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL in external browser: " + url, e);
                }
                return true; // We handled it - prevent WebView from loading it
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Handle older API version
                if (url.startsWith("data:")) {
                    return false;
                }
                Log.d(TAG, "shouldOverrideUrlLoading (String): Opening link externally: " + url);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    mFragment.getActivity().startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL in external browser: " + url, e);
                }
                return true;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject CSS for plain text agendas (data URIs)
                if (url != null && url.startsWith("data:text/html")) {
                    injectAgendaCSS();
                }
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.w(TAG, "onReceivedError: " + error.getDescription() + " for " + request.getUrl());
                // Show error message
                showErrorMessage();
            }
        });
        
        Log.d(TAG, "initializeWebView: WebView initialized");
    }
    
    /**
     * Update Agenda tab with the agenda URL from the session cursor.
     */
    public void updateAgendaTab(String agendaUrl) {
        if (agendaUrl == null || agendaUrl.isEmpty()) {
            initializeWebView();
            if (mWebView != null) {
                showLoadingMessage("Agenda is being downloaded. Please check back in a moment or use the Refresh button.");
            }
            return;
        }
        
        // Check if URL changed
        if (agendaUrl.equals(mCurrentUrl)) {
            return; // Already loaded
        }
        
        mCurrentUrl = agendaUrl;
        initializeWebView();
        
        if (mWebView == null) {
            Log.e(TAG, "updateAgendaTab: WebView not initialized");
            return;
        }
        
        // Check if this tab is currently active
        boolean isAgendaTabActive = mTabHost != null && TAB_AGENDA.equals(mTabHost.getCurrentTabTag());
        if (!isAgendaTabActive) {
            Log.d(TAG, "updateAgendaTab: Agenda tab not active, storing URL for later");
            return;
        }
        
        // For agenda URLs, try to fetch and wrap with CSS if plain text
        // For non-agenda URLs or if fetch fails, load directly
        if (agendaUrl.contains("datatracker.ietf.org") && agendaUrl.contains("agenda")) {
            // Fetch in background to check if it's plain text
            fetchAndLoadAgenda(agendaUrl);
        } else {
            mWebView.loadUrl(agendaUrl);
        }
    }
    
    /**
     * Fetch agenda page; use Content-Type when available (Datatracker materials).
     * text/html → load URL; text/markdown → commonmark; text/plain → escaped pre-wrap.
     */
    private void fetchAndLoadAgenda(String agendaUrl) {
        Log.d(TAG, "fetchAndLoadAgenda: Fetching " + agendaUrl);

        new Thread(() -> {
            try {
                RemoteExecutor executor = new RemoteExecutor();
                RemoteExecutor.HttpGetResult result = executor.executeGetWithContentType(agendaUrl);
                String content = result.body;
                String mime = primaryMimeType(result.contentType);

                Log.d(TAG, "fetchAndLoadAgenda: length=" +
                        (content != null ? content.length() : 0) + " contentType=" + result.contentType);

                if (content == null || content.isEmpty()) {
                    Log.w(TAG, "fetchAndLoadAgenda: Empty content, loading URL directly");
                    runOnUi(() -> {
                        if (mWebView != null) {
                            mWebView.loadUrl(agendaUrl);
                        }
                    });
                    return;
                }

                AgendaFormat format = classifyAgenda(mime, content);
                Log.d(TAG, "fetchAndLoadAgenda: format=" + format);

                if (format == AgendaFormat.HTML) {
                    runOnUi(() -> {
                        if (mWebView != null) {
                            mWebView.loadUrl(agendaUrl);
                        }
                    });
                    return;
                }

                final String wrappedHtml;
                if (format == AgendaFormat.MARKDOWN) {
                    // Links only via commonmark AutolinkExtension — do not run convertUrlsToLinks.
                    wrappedHtml = wrapMarkdownAgendaHtml(content);
                } else {
                    wrappedHtml = wrapPlainTextAgendaHtml(content);
                }

                runOnUi(() -> {
                    if (mWebView != null) {
                        loadHtmlDataUri(wrappedHtml, agendaUrl);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch agenda", e);
                runOnUi(() -> {
                    if (mWebView != null) {
                        mWebView.loadUrl(agendaUrl);
                    }
                });
            }
        }).start();
    }

    private enum AgendaFormat {
        HTML,
        MARKDOWN,
        PLAIN
    }

    private static String primaryMimeType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        int semi = contentType.indexOf(';');
        String primary = semi >= 0 ? contentType.substring(0, semi) : contentType;
        return primary.trim().toLowerCase(Locale.ROOT);
    }

    private AgendaFormat classifyAgenda(String mime, String content) {
        if (mime != null) {
            if (mime.equals("text/html") || mime.equals("application/xhtml+xml")) {
                return AgendaFormat.HTML;
            }
            if (mime.equals("text/markdown") || mime.equals("text/x-markdown")) {
                return AgendaFormat.MARKDOWN;
            }
            if (mime.equals("text/plain")) {
                return AgendaFormat.PLAIN;
            }
        }
        // No useful Content-Type: keep HTML sniff; otherwise treat as markdown (#45).
        if (isHtmlContent(content)) {
            return AgendaFormat.HTML;
        }
        return AgendaFormat.MARKDOWN;
    }

    private String wrapMarkdownAgendaHtml(String markdown) {
        String htmlContent = MarkdownHtml.render(markdown);
        String css =
                "body { " +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; " +
                "  font-size: 16px; " +
                "  line-height: 1.6; " +
                "  color: #333; " +
                "  padding: 16px; " +
                "  word-wrap: break-word; " +
                "} " +
                "a { color: #0066cc; text-decoration: underline; } " +
                "h1, h2, h3, h4 { margin-top: 1.2em; margin-bottom: 0.4em; } " +
                "ul, ol { padding-left: 1.4em; } " +
                "li { margin-bottom: 0.35em; } " +
                "code, pre { font-family: ui-monospace, Consolas, monospace; font-size: 0.92em; } " +
                "pre { overflow-x: auto; background: #f5f5f5; padding: 8px; }";
        return "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + css + "</style>" +
                "</head><body>" +
                htmlContent +
                "</body></html>";
    }

    private String wrapPlainTextAgendaHtml(String content) {
        String contentWithLinks = convertUrlsToLinks(content);
        String tempContent = contentWithLinks.replaceAll(
                "<a href=\"([^\"]+)\">([^<]+)</a>",
                "___LINK_START___$1___LINK_MID___$2___LINK_END___");
        String escapedContent = tempContent.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        escapedContent = escapedContent.replace("___LINK_START___", "<a href=\"")
                .replace("___LINK_MID___", "\">")
                .replace("___LINK_END___", "</a>");

        String css =
                "body { " +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; " +
                "  font-size: 16px; " +
                "  line-height: 1.6; " +
                "  color: #333; " +
                "  padding: 16px; " +
                "  white-space: pre-wrap; " +
                "  word-wrap: break-word; " +
                "} " +
                "a { " +
                "  color: #0066cc; " +
                "  text-decoration: underline; " +
                "}";

        return "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + css + "</style>" +
                "</head><body>" +
                escapedContent +
                "</body></html>";
    }

    private void loadHtmlDataUri(String wrappedHtml, String fallbackUrl) {
        try {
            byte[] htmlBytes = wrappedHtml.getBytes("UTF-8");
            String base64Html = android.util.Base64.encodeToString(htmlBytes, android.util.Base64.NO_WRAP);
            String dataUri = "data:text/html;charset=utf-8;base64," + base64Html;
            mWebView.loadUrl(dataUri);
            Log.d(TAG, "fetchAndLoadAgenda: Loaded agenda as data URI");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to encode HTML", e);
            mWebView.loadUrl(fallbackUrl);
        }
    }

    private void runOnUi(Runnable action) {
        if (mFragment.getActivity() != null) {
            mFragment.getActivity().runOnUiThread(action);
        }
    }

    /**
     * Inject CSS into the loaded page (for data URIs).
     */
    private void injectAgendaCSS() {
        if (mWebView == null) {
            return;
        }
        
        // CSS is already in the data URI wrapper, but we can inject additional CSS if needed
        // For now, CSS is already included in the wrapped HTML, so this is a no-op
        Log.d(TAG, "injectAgendaCSS: CSS already included in data URI wrapper");
    }
    
    /**
     * Convert bare https URLs to anchors. Used only for {@link AgendaFormat#PLAIN}
     * agendas — markdown uses {@link MarkdownHtml} / AutolinkExtension instead.
     */
    private String convertUrlsToLinks(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Pattern to match URLs starting with https://
        Pattern urlPattern = Pattern.compile(
            "(https://[\\w\\.\\-/:?#\\[\\]@!$&'()*+,;=]+)");
        
        Matcher matcher = urlPattern.matcher(text);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String url = matcher.group(1);
            // Replace with HTML anchor tag
            matcher.appendReplacement(result, "<a href=\"" + url + "\">" + url + "</a>");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Check if content is HTML or plain text.
     */
    private boolean isHtmlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check for common HTML indicators
        String trimmed = content.trim();
        
        // Check for HTML doctype
        if (trimmed.toLowerCase().startsWith("<!doctype html")) {
            return true;
        }
        
        // Check for HTML tags (common ones)
        if (trimmed.contains("<html") || trimmed.contains("<HTML")) {
            return true;
        }
        if (trimmed.contains("<body") || trimmed.contains("<BODY")) {
            return true;
        }
        if (trimmed.contains("<div") || trimmed.contains("<DIV")) {
            return true;
        }
        if (trimmed.contains("<p>") || trimmed.contains("<P>")) {
            return true;
        }
        if (trimmed.contains("<h1") || trimmed.contains("<H1")) {
            return true;
        }
        if (trimmed.contains("<table") || trimmed.contains("<TABLE")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Show plain text (e.g. side-meeting description) in the Agenda WebView.
     * Newlines become &lt;br&gt;; HTML in the source is escaped.
     */
    public void showPlainText(String text) {
        initializeWebView();
        if (mWebView == null) {
            return;
        }
        String body = text == null ? "" : android.text.TextUtils.htmlEncode(text).replace("\n", "<br>");
        String html = "<!DOCTYPE html>" +
            "<html><head>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" +
            "body { " +
            "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
            "  font-size: 16px; " +
            "  line-height: 1.45; " +
            "  padding: 16px; " +
            "  color: #222; " +
            "  white-space: normal; " +
            "}" +
            "</style>" +
            "</head><body>" +
            body +
            "</body></html>";
        mCurrentUrl = null;
        mWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    /**
     * Show loading or status message.
     */
    private void showLoadingMessage(String message) {
        if (mWebView == null) {
            return;
        }
        
        String html = "<!DOCTYPE html>" +
            "<html><head>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" +
            "body { " +
            "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
            "  font-size: 16px; " +
            "  padding: 16px; " +
            "  color: #666; " +
            "}" +
            "</style>" +
            "</head><body>" +
            message +
            "</body></html>";
        
        mWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
    
    /**
     * Show error message.
     */
    private void showErrorMessage() {
        if (mWebView == null) {
            return;
        }
        
        String errorMessage = "Unable to load agenda. Please check your internet connection and try again.";
        showLoadingMessage(errorMessage);
    }
    
    /**
     * Handle back button press - navigate back in WebView if possible.
     */
    public boolean onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }
    
    /**
     * Clean up resources.
     */
    public void cleanup() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
    }
}
