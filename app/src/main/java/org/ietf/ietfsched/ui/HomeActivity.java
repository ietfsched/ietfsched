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

package org.ietf.ietfsched.ui;

import org.ietf.ietfsched.R;
import org.ietf.ietfsched.service.SyncService;
import org.ietf.ietfsched.util.DetachableResultReceiver;

import android.app.Activity;
import android.app.BackgroundServiceStartNotAllowedException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import java.util.Random;

/**
 * Front-door {@link Activity} that displays high-level features the schedule application offers to
 * users. Depending on whether the device is a phone or an Android 3.0+ tablet, different layouts
 * will be used. For example, on a phone, the primary content is a {@link DashboardFragment},
 * whereas on a tablet, both a {@link DashboardFragment} and a {@link TagStreamFragment} are
 * displayed.
 */
public class HomeActivity extends BaseActivity {
    private static final String TAG = "HomeActivity";
    private static final String PREFS_NAME = "ietfsched_prefs";
    private static final String PREF_LAST_SYNC = "last_sync_millis";
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hour
    private static final long CACHE_JITTER_MS = 20 * 60 * 1000;   // ±20 minutes

    private SyncStatusUpdaterFragment mSyncStatusUpdaterFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        getActivityHelper().setupActionBar(null, 0);

        FragmentManager fm = getSupportFragmentManager();

        mSyncStatusUpdaterFragment = (SyncStatusUpdaterFragment) fm.findFragmentByTag(SyncStatusUpdaterFragment.TAG);
        if (mSyncStatusUpdaterFragment == null) {
            mSyncStatusUpdaterFragment = new SyncStatusUpdaterFragment();
            fm.beginTransaction().add(mSyncStatusUpdaterFragment,  SyncStatusUpdaterFragment.TAG).commit();
        }
        
        // Only check cache and refresh on initial creation (not on configuration changes)
        // Also don't trigger if a sync is already in progress
        if (savedInstanceState == null && !isRefreshing()) {
            if (isCacheStale()) {
                // Show toast for automatic sync too, so users know sync is happening
                Toast.makeText(this, "Refreshing schedule data...", Toast.LENGTH_SHORT).show();
                triggerRefresh();
            }
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupHomeActivity();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.refresh_menu_items, menu);
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            // Manual refresh always forces a sync, bypassing cache
            Toast.makeText(this, "Refreshing schedule data...", Toast.LENGTH_SHORT).show();
            triggerRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Check if cached data is stale and needs refresh.
     * Uses 1 hour + random jitter (0 to +20 minutes) to avoid thundering herd.
     * Jitter only adds time, never subtracts, ensuring minimum 1 hour cache.
     */
    private boolean isCacheStale() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastSync = prefs.getLong(PREF_LAST_SYNC, 0);
        
        if (lastSync == 0) {
            Log.d(TAG, "isCacheStale: Never synced before, cache is stale");
            return true; // Never synced before
        }
        
        long now = System.currentTimeMillis();
        long age = now - lastSync;
        
        // Add random jitter (0 to +20 minutes) to cache duration
        // This ensures minimum 1 hour cache, with up to 80 minutes to avoid thundering herd
        Random random = new Random();
        long jitter = (long) (random.nextDouble() * CACHE_JITTER_MS); // 0 to +20 minutes
        long threshold = CACHE_DURATION_MS + jitter;
        
        boolean stale = age > threshold;
        Log.d(TAG, "isCacheStale: lastSync=" + lastSync + " (" + (lastSync / 1000) + " seconds), now=" + now + 
              ", age=" + (age / 60000) + " min, threshold=" + (threshold / 60000) + " min, stale=" + stale);
        return stale;
    }

    private void triggerRefresh() {
        final Intent intent = new Intent(Intent.ACTION_SYNC, null, this, SyncService.class);
        intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mSyncStatusUpdaterFragment.mReceiver);
        try {
            startService(intent);
        } catch (BackgroundServiceStartNotAllowedException e) {
            // On Android 12+, if we can't start the service immediately, skip auto-refresh
            // User can manually refresh from the menu when the activity is fully visible
            Log.w(TAG, "Cannot start service in background, skipping auto-refresh", e);
        } catch (IllegalStateException e) {
            // Handle other service start exceptions
            Log.w(TAG, "Cannot start service", e);
        }
    }
    
    private void recordSyncTime() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long syncTime = System.currentTimeMillis();
        prefs.edit().putLong(PREF_LAST_SYNC, syncTime).commit(); // Use commit() instead of apply() to ensure persistence
        Log.d(TAG, "recordSyncTime: Recorded sync time: " + syncTime + " (" + (syncTime / 1000) + " seconds since epoch)");
    }

    private void updateRefreshStatus(boolean refreshing) {
        getActivityHelper().setRefreshActionButtonCompatState(refreshing);
    }

	public boolean isRefreshing() {
		return mSyncStatusUpdaterFragment != null ? mSyncStatusUpdaterFragment.mSyncing : false;
		}

    /**
     * A non-UI fragment, retained across configuration changes, that updates its activity's UI
     * when sync status changes.
     */
    public static class SyncStatusUpdaterFragment extends Fragment
            implements DetachableResultReceiver.Receiver {
        public static final String TAG = SyncStatusUpdaterFragment.class.getName();

        private boolean mSyncing = false;  // Start as false, will be set to true when sync actually starts
        private DetachableResultReceiver mReceiver;
			
		public SyncStatusUpdaterFragment() {
			mReceiver = new DetachableResultReceiver(new Handler());
			mReceiver.setReceiver(this);
		}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }


		public void onReceiveResult(int resultCode, Bundle resultData) {
            HomeActivity activity = (HomeActivity) getActivity();
            if (activity == null) {
                return;
            }

            switch (resultCode) {
                case SyncService.STATUS_RUNNING: {
                    mSyncing = true;
                    break;
                }
                case SyncService.STATUS_FINISHED: {
                    mSyncing = false;
                    activity.recordSyncTime(); // Cache the sync timestamp
                    Toast.makeText(activity, "Schedule updated", Toast.LENGTH_SHORT).show();
                    break;
                }
                case SyncService.STATUS_ERROR: {
                    // Error happened down in SyncService, show as toast.
                    mSyncing = false;
                    final String errorText = getString(R.string.toast_sync_error, resultData
                            .getString(Intent.EXTRA_TEXT));
                    Toast.makeText(activity, errorText, Toast.LENGTH_LONG).show();
                    break;
                }
            }

            activity.updateRefreshStatus(mSyncing);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ((HomeActivity) getActivity()).updateRefreshStatus(mSyncing);
        }
    }
}
