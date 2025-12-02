package org.ietf.ietfsched;

import androidx.test.espresso.IdlingResource;
import android.util.Log;

import org.ietf.ietfsched.ui.HomeActivity;

/**
 * IdlingResource that signals when sync is complete
 * 
 * This allows Espresso to wait for sync to finish before proceeding with tests.
 * The resource is idle when HomeActivity.isRefreshing() returns false.
 */
public class SyncIdlingResource implements IdlingResource {
    private static final String TAG = "SyncIdlingResource";
    private ResourceCallback resourceCallback;
    HomeActivity homeActivity; // Package-private for TestUtils access
    
    public SyncIdlingResource(HomeActivity activity) {
        this.homeActivity = activity;
    }
    
    @Override
    public String getName() {
        return "SyncIdlingResource";
    }
    
    @Override
    public boolean isIdleNow() {
        if (homeActivity == null) {
            Log.w(TAG, "isIdleNow: homeActivity is null, considering idle");
            return true;
        }
        
        boolean isRefreshing = homeActivity.isRefreshing();
        boolean isIdle = !isRefreshing;
        
        if (isIdle) {
            Log.d(TAG, "isIdleNow: Sync complete (isRefreshing=false), resource is idle");
            if (resourceCallback != null) {
                resourceCallback.onTransitionToIdle();
            }
        } else {
            Log.d(TAG, "isIdleNow: Sync in progress (isRefreshing=true), resource is busy");
        }
        
        return isIdle;
    }
    
    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.resourceCallback = callback;
    }
}

