/*
 * Copyright 2011 Google Inc.
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
import org.ietf.ietfsched.provider.ScheduleContract;
import org.ietf.ietfsched.ui.phone.ScheduleActivity;
import org.ietf.ietfsched.ui.tablet.ScheduleMultiPaneActivity;
import org.ietf.ietfsched.ui.tablet.SessionsMultiPaneActivity;
import org.ietf.ietfsched.util.UIUtils;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class DashboardFragment extends Fragment {
	private String TAG = "DashboardFragment";

    public void fireTrackerEvent(String label) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container);

		
        // Attach event handlers
        root.findViewById(R.id.home_btn_schedule).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
				HomeActivity activity = (HomeActivity) getActivity();
				if (activity.isRefreshing()) {
                    Toast.makeText(activity, "Check/Upload new agenda, pls wait", Toast.LENGTH_LONG).show();
					return;
					}
                fireTrackerEvent("Schedule");
                startActivity(new Intent(getActivity(), ScheduleActivity.class));
            }
            
        });
	
		root.findViewById(R.id.home_btn_sessions).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				HomeActivity activity = (HomeActivity) getActivity();
				if (activity.isRefreshing()) {
                    Toast.makeText(activity, "Check/Upload new agenda, pls wait", Toast.LENGTH_LONG).show();
					return;
					}
				fireTrackerEvent("Sessions");
				// Launch sessions list
				final Intent intent = new Intent(Intent.ACTION_VIEW, ScheduleContract.Tracks.CONTENT_URI);
				intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_session_tracks));
				intent.putExtra(TracksFragment.EXTRA_NEXT_TYPE,	TracksFragment.NEXT_TYPE_SESSIONS);
				startActivity(intent);
			}
		});	

       root.findViewById(R.id.home_btn_starred).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                fireTrackerEvent("Starred");
                // Launch list of sessions and vendors the user has starred
                startActivity(new Intent(getActivity(), StarredActivity.class));
            }
        });
		
        root.findViewById(R.id.home_btn_announcements).setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View view) {
                        // splicing in tag streamer
//                        fireTrackerEvent("Bulletin");
                        Intent intent = new Intent(getActivity(), WellNoteActivity.class);
                        startActivity(intent);
                    }
                });
        return root;
    }
}
