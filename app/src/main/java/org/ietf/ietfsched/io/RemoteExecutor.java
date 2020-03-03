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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Executes an {@link HttpUriRequest} and passes the result as an
 * {@link XmlPullParser} to the given {@link XmlHandler}.
 */
public class RemoteExecutor {

	private static final String TAG = "RemoteExecutor HTTP";

    public RemoteExecutor() { }


	public String executeHead(String urlString) throws Exception {
    	URL url;
    	HttpURLConnection urlConnection = null;
    	try {
    		url = new URL(urlString);
    		urlConnection = (HttpsURLConnection) url.openConnection();

    		int status = urlConnection.getResponseCode();
    		Log.w("HTTPSTATUS", "Statuscode: " + status);
    		if (status == HttpsURLConnection.HTTP_OK) {
    			String header = urlConnection.getHeaderFields().get("Etag").get(0);

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
		} catch (Exception e) {
    		e.printStackTrace();
		} finally {
    		if (urlConnection != null){
    			urlConnection.disconnect();
			}
		}
		urlConnection.disconnect();

		return null;
	}

	/**
	 * Execute a {@link HttpGet} request, passing a valid response through
	 * {@link XmlHandler#parseAndApply(XmlPullParser, ContentResolver)}.
	 */
	public InputStream executeGet(String urlString) throws Exception {
		// Last-Modified Thu, 01 Sep 2011 16:11:33 GMT
		//ETag "aa4a6d-41d1-4abe37f915740"-gzip
		URL url;
		HttpURLConnection urlConnection = null;
		try {
			url = new URL(urlString);
			urlConnection = (HttpsURLConnection) url.openConnection();

			int status = urlConnection.getResponseCode();
			Log.w("HTTPSTATUS", "Statuscode: " + status);
			if (status == HttpsURLConnection.HTTP_OK) {
				return urlConnection.getInputStream();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		urlConnection.disconnect();
		return null;
	}
}
