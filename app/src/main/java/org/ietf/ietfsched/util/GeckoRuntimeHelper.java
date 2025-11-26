/*
 * Copyright 2024
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

import android.content.Context;
import android.util.Log;
import org.mozilla.geckoview.GeckoRuntime;

/**
 * Helper class to manage a shared GeckoRuntime instance.
 * GeckoRuntime only allows one instance per process, so we need to share it.
 */
public class GeckoRuntimeHelper {
    private static final String TAG = "GeckoRuntimeHelper";
    private static GeckoRuntime sGeckoRuntime;
    
    /**
     * Get or create the shared GeckoRuntime instance.
     * @param context The Android context
     * @return The GeckoRuntime instance, or null if creation fails
     */
    public static synchronized GeckoRuntime getRuntime(Context context) {
        if (sGeckoRuntime == null) {
            try {
                sGeckoRuntime = GeckoRuntime.create(context);
                Log.d(TAG, "Created new GeckoRuntime instance");
            } catch (IllegalStateException e) {
                // Runtime already exists - this means another fragment created it
                // but we don't have a reference. We need to recreate our reference.
                // Unfortunately GeckoRuntime doesn't expose a way to get existing instance,
                // so we'll need to ensure only one place creates it.
                Log.w(TAG, "GeckoRuntime already exists - this should not happen if using shared helper", e);
                // Try to create anyway - this will fail, but at least we log the issue
                throw e;
            }
        }
        return sGeckoRuntime;
    }
    
    /**
     * Check if a GeckoRuntime instance exists.
     * @return true if runtime exists, false otherwise
     */
    public static synchronized boolean hasRuntime() {
        return sGeckoRuntime != null;
    }
}
