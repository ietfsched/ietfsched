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

import org.ietf.ietfsched.R;

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;

import io.noties.markwon.Markwon;
import org.ietf.ietfsched.service.SyncService;


/**
 * A fragment that shows the IETF Well Note.
 */
public class WellNoteFragment extends Fragment {
    private static final String noteWellURL = "https://www.ietf.org/media/documents/note-well.md";
	// private static final String default_note = R.string.note_well_default;
    public String default_note = String.valueOf(R.string.note_well_default);
    private static final String TAG = "WellNoteFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {super.onCreate(savedInstanceState); }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String disp_text = default_note;
        if (SyncService.noteWellString.length() > 0) {
            disp_text = SyncService.noteWellString;
        }
        // Create a ScrollView, add to that a TextView, stick the text in the TextView.
		ScrollView scroller = new ScrollView(getActivity());
		TextView text = new TextView(getActivity());
        // Reformat the MD to useful Android formatted strings.
        final Context context = getContext();
        final Markwon mw = Markwon.create(context);
        mw.setMarkdown(text, disp_text);
		scroller.addView(text);
        return scroller;
    }
}
