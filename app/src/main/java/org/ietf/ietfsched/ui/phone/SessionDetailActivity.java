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

package org.ietf.ietfsched.ui.phone;

import org.ietf.ietfsched.ui.BaseSinglePaneActivity;
import org.ietf.ietfsched.ui.SessionDetailFragment;
import org.ietf.ietfsched.util.GeckoRuntimeHelper;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

public class SessionDetailActivity extends BaseSinglePaneActivity {
    private OnBackPressedCallback mBackCallback;
    
    @Override
    protected Fragment onCreatePane() {
        return new SessionDetailFragment();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();
        
        // Set up back button handling for Android 13+ (API 33+)
        // Always enabled - we'll check fragment state inside handleOnBackPressed
        mBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                android.util.Log.d("SessionDetailActivity", "handleOnBackPressed called, callback enabled=" + isEnabled() + ", activity=" + SessionDetailActivity.this);
                Fragment fragment = getSupportFragmentManager().findFragmentById(org.ietf.ietfsched.R.id.root_container);
                android.util.Log.d("SessionDetailActivity", "Fragment: " + fragment);
                if (fragment instanceof SessionDetailFragment) {
                    SessionDetailFragment sessionDetailFragment = (SessionDetailFragment) fragment;
                    boolean handled = sessionDetailFragment.onBackPressed();
                    android.util.Log.d("SessionDetailActivity", "Fragment onBackPressed returned: " + handled);
                    if (handled) {
                        android.util.Log.d("SessionDetailActivity", "Fragment handled back press (navigated in GeckoView)");
                        return; // Fragment handled it (navigated back in GeckoView)
                    }
                } else {
                    android.util.Log.w("SessionDetailActivity", "Fragment is not SessionDetailFragment: " + (fragment != null ? fragment.getClass().getName() : "null"));
                }
                // Fragment didn't handle it, finish the activity
                android.util.Log.d("SessionDetailActivity", "Fragment did not handle back press, calling finish()");
                finish();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);
        android.util.Log.d("SessionDetailActivity", "Back callback registered, enabled=" + mBackCallback.isEnabled());
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Notify GeckoRuntime of configuration change (required when using configChanges)
        org.mozilla.geckoview.GeckoRuntime runtime = GeckoRuntimeHelper.getRuntime(this);
        if (runtime != null) {
            runtime.configurationChanged(newConfig);
        }
    }
    
    @Override
    public void finish() {
        // Just finish - back button handling is done in handleOnBackPressed/onBackPressed
        super.finish();
    }
    
    @Override
    public void onBackPressed() {
        // For older Android versions (API < 33)
        Fragment fragment = getSupportFragmentManager().findFragmentById(org.ietf.ietfsched.R.id.root_container);
        if (fragment instanceof SessionDetailFragment) {
            SessionDetailFragment sessionDetailFragment = (SessionDetailFragment) fragment;
            if (sessionDetailFragment.onBackPressed()) {
                return; // Fragment handled it
            }
        }
        // Fragment didn't handle it, use default behavior
        super.onBackPressed();
    }
}
