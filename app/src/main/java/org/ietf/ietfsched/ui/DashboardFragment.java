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
import org.ietf.ietfsched.util.UIUtils;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class DashboardFragment extends Fragment {
    public void fireTrackerEvent(String label) {
        // Placeholder for analytics tracking
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container);

		
        // Attach event handlers
        root.findViewById(R.id.home_btn_schedule).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
				HomeActivity activity = (HomeActivity) getActivity();
				if (activity.isRefreshing()) {
                    Toast.makeText(activity, "Check/Upload new agenda, please wait", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(activity, "Check/Upload new agenda, please wait", Toast.LENGTH_LONG).show();
					return;
					}
				fireTrackerEvent("Sessions");
				// Launch all sessions list directly (skip tracks screen)
				final Intent intent = new Intent(Intent.ACTION_VIEW, ScheduleContract.Sessions.CONTENT_URI);
				intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_sessions));
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
                        HomeActivity activity = (HomeActivity) getActivity();
                        if (activity.isRefreshing()) {
                            Toast.makeText(activity, "Check/Upload new agenda, please wait", Toast.LENGTH_LONG).show();
                            return;
                        }
                        fireTrackerEvent("Note Well");
                        startActivity(new Intent(getActivity(), WellNoteActivity.class));
                    }
                });

        // Set up Feedback and Support link
        TextView feedbackLink = root.findViewById(R.id.feedback_support_link);
        if (feedbackLink != null) {
            // Make text underlined using HTML
            String text = getString(R.string.feedback_and_support);
            Spanned spannedText = Html.fromHtml("<u>" + text + "</u>", Html.FROM_HTML_MODE_LEGACY);
            feedbackLink.setText(spannedText);
            
            feedbackLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    fireTrackerEvent("Feedback and Support");
                    String email = getString(R.string.feedback_email);
                    String subject = getString(R.string.feedback_email_subject);
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    // Build mailto URI with query parameters - construct manually to ensure email is preserved
                    String mailtoString = "mailto:" + email + "?subject=" + Uri.encode(subject);
                    intent.setData(Uri.parse(mailtoString));
                    try {
                        startActivity(intent);
                    } catch (android.content.ActivityNotFoundException e) {
                        Toast.makeText(getActivity(), "No email app found", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        return root;
    }
}
