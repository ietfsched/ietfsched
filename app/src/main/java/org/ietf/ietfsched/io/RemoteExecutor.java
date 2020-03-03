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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

/**
 * Extract either a HEAD (executeHead) or full page (executeGet).
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

	public String[] executeGet(String urlString) throws Exception {
		URL url;
		HttpURLConnection urlConnection = null;
		try {
			url = new URL(urlString);
			urlConnection = (HttpsURLConnection) url.openConnection();

			int status = urlConnection.getResponseCode();
			if (status == HttpsURLConnection.HTTP_OK) {
			    ArrayList<String> result = new ArrayList<>();
			    BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			    String line;
			    while ((line = reader.readLine()) != null) {
					try {
						// Skip the initial header line.
						if (line.startsWith("\"b'Date")) {
							continue;
						}
						result.add(line.trim());
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				}
			    if (urlConnection != null) {
					urlConnection.disconnect();
				}
				return result.toArray(new String[0]);
			}
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		return null;
	}
}
