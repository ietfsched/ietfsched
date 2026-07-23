/*
 * Copyright 2011 Google Inc.
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

package org.ietf.ietfsched.io;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

/**
 * Extract either a HEAD (executeHead) or full page (executeGet).
 */
public class RemoteExecutor {

	private static final String TAG = "RemoteExecutor HTTP";

	/** Body and Content-Type from an HTTP GET (Content-Type may be null). */
	public static final class HttpGetResult {
		public final String body;
		public final String contentType;

		public HttpGetResult(String body, String contentType) {
			this.body = body != null ? body : "";
			this.contentType = contentType;
		}
	}

    public RemoteExecutor() { }


	public String executeHead(String urlString) throws Exception {
    	URL url;
    	HttpURLConnection urlConnection = null;
    	try {
    		url = new URI(urlString).toURL();
    		urlConnection = (HttpsURLConnection) url.openConnection();

    		int status = urlConnection.getResponseCode();
    		if (status == HttpsURLConnection.HTTP_OK) {
    			String header = Objects.requireNonNull(urlConnection.getHeaderFields().get("Etag")).get(0);

    			if (header != null) {
    				try {
    					Log.d(TAG, "Etag " + header);
    					return header;
					} catch (Exception e) {
    					e.printStackTrace();
    					return null;
					}
				}
			}
		} finally {
    		if (urlConnection != null){
    			urlConnection.disconnect();
			}
		}
    	if (urlConnection != null) {
			urlConnection.disconnect();
		}

		return null;
	}

	// Get a String object from a remote server.
	public String executeGet(String urlString) throws Exception {
		return executeGetWithContentType(urlString).body;
	}

	/**
	 * GET text body and Content-Type (e.g. text/markdown from Datatracker materials).
	 */
	public HttpGetResult executeGetWithContentType(String urlString) throws Exception {
		HttpsURLConnection urlConnection = null;
		try {
			URL url = new URI(urlString).toURL();
			urlConnection = (HttpsURLConnection) url.openConnection();

			int status = urlConnection.getResponseCode();
			Log.d(TAG, "executeGet: status=" + status + " for " + urlString);

			if (status == HttpsURLConnection.HTTP_OK) {
				String contentType = urlConnection.getContentType();
				StringBuilder result = new StringBuilder();
				BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					try {
						result.append(line.trim());
						result.append("\n");  // Preserve newlines for proper markdown formatting
					} catch (Exception e){
						e.printStackTrace();
						break;
					}
				}
				return new HttpGetResult(result.toString(), contentType);
			} else {
				Log.w(TAG, "executeGet: Non-200 status " + status + " for " + urlString);
				if (status == 503 || status >= 500) {
					throw new Exception("Server error: HTTP " + status);
				}
			}
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		return new HttpGetResult("", null);
	}

	// Get a JSON object from a remote server.
	public JSONObject executeJSONGet(String urlString) throws Exception {
		return executeJSONGet(urlString, 0, 0);
	}

	/**
	 * GET JSON with optional connect/read timeouts (milliseconds).
	 * Pass 0 for a timeout to keep the platform default.
	 */
	public JSONObject executeJSONGet(String urlString, int connectTimeoutMs, int readTimeoutMs)
			throws Exception {
		URL url;
		HttpURLConnection urlConnection = null;
		try {
			url = new URI(urlString).toURL();
			urlConnection = (HttpsURLConnection) url.openConnection();
			if (connectTimeoutMs > 0) {
				urlConnection.setConnectTimeout(connectTimeoutMs);
			}
			if (readTimeoutMs > 0) {
				urlConnection.setReadTimeout(readTimeoutMs);
			}

			int status = urlConnection.getResponseCode();
			if (status == HttpsURLConnection.HTTP_OK) {
			    StringBuilder result = new StringBuilder();
			    BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			    String line;
			    while ((line = reader.readLine()) != null) {
					try {
						result.append(line.trim());
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				}
			    if (urlConnection != null) {
					urlConnection.disconnect();
				}
				return new JSONObject(result.toString());
			}
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		return new JSONObject();
	}
}
