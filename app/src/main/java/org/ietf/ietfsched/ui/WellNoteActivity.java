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

import android.os.Bundle;
import androidx.fragment.app.Fragment;

public class WellNoteActivity extends BaseSinglePaneActivity {
    @Override
    protected Fragment onCreatePane() {
        return new WellNoteFragment();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();
    }
    
    @Override
    public void onBackPressed() {
        // Check if the fragment can handle back press (WebView navigation)
        Fragment fragment = getSupportFragmentManager().findFragmentById(org.ietf.ietfsched.R.id.root_container);
        if (fragment instanceof WellNoteFragment) {
            WellNoteFragment wellNoteFragment = (WellNoteFragment) fragment;
            if (wellNoteFragment.onBackPressed()) {
                return; // Fragment handled it
            }
        }
        // Fragment didn't handle it, use default behavior
        super.onBackPressed();
    }
}
