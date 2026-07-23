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
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/**
 * Helper class to manage a shared GeckoRuntime instance.
 * GeckoRuntime only allows one instance per process, so we need to share it.
 */
public class GeckoRuntimeHelper {
    private static final String TAG = "GeckoRuntimeHelper";
    private static final String CONFIG_FILE_NAME = "geckoview-runtime.yaml";
    private static GeckoRuntime sGeckoRuntime;
    
    /**
     * Get or create the shared GeckoRuntime instance.
     * @param context The Android context
     * @return The GeckoRuntime instance, or null if creation fails
     */
    public static synchronized GeckoRuntime getRuntime(Context context) {
        if (sGeckoRuntime == null) {
            try {
                // Datatracker's Cloudflare challenge pages send COOP: same-origin, which
                // severs window.opener and breaks Meetecho's popup OAuth handoff on fresh
                // login. Disable COOP process-isolation so opener survives that challenge.
                File configFile = writeRuntimeConfig(context);
                GeckoRuntimeSettings.Builder builder = new GeckoRuntimeSettings.Builder()
                        .consoleOutput(true)
                        .aboutConfigEnabled(true);
                if (configFile != null) {
                    builder.configFilePath(configFile.getAbsolutePath());
                    Log.d(TAG, "Using GeckoView config: " + configFile.getAbsolutePath());
                }
                sGeckoRuntime = GeckoRuntime.create(context, builder.build());
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
     * Writes a GeckoView automation config that turns off COOP/COEP browsing-context
     * isolation. Format: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/automation.html
     */
    private static File writeRuntimeConfig(Context context) {
        try {
            File dir = context.getFilesDir();
            File configFile = new File(dir, CONFIG_FILE_NAME);
            // YAML, not JSON — GeckoView only parses the automation YAML schema.
            String yaml = "# ietfsched: keep window.opener across Datatracker CF challenge (COOP)\n"
                    + "prefs:\n"
                    + "  browser.tabs.remote.useCrossOriginOpenerPolicy: false\n"
                    + "  browser.tabs.remote.useCrossOriginEmbedderPolicy: false\n";
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                writer.write(yaml);
            }
            return configFile;
        } catch (Exception e) {
            Log.w(TAG, "Failed to write GeckoView runtime config", e);
            return null;
        }
    }
    
    /**
     * Check if a GeckoRuntime instance exists.
     * @return true if runtime exists, false otherwise
     */
    public static synchronized boolean hasRuntime() {
        return sGeckoRuntime != null;
    }
}
