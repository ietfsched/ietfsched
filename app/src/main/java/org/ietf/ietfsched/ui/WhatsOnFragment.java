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
import org.ietf.ietfsched.util.MeetingPreferences;
import org.ietf.ietfsched.util.UIUtils;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Home strip that shows meeting-aware status:
 * before → countdown to IETF-N in City;
 * during → Welcome to IETF-N in City;
 * after → See you at the next meeting (when known).
 */
public class WhatsOnFragment extends Fragment {

    private final Handler mHandler = new Handler();

    private TextView mStatusTextView;
    private ViewGroup mRootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_whats_on, container, false);
        refresh();
        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /** Re-read prefs and update the status strip (e.g. after sync). */
    public void refreshStatus() {
        if (mRootView != null) {
            refresh();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mHandler.removeCallbacks(mDayRolloverRunnable);
    }

    private void refresh() {
        mHandler.removeCallbacks(mDayRolloverRunnable);
        mRootView.removeAllViews();
        mStatusTextView = null;

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Prefer prefs (includes city); fall back to UIUtils dates from sync this process.
        long start = MeetingPreferences.getCurrentMeetingStart(activity);
        long end = MeetingPreferences.getCurrentMeetingEnd(activity);
        if (start == 0 || end == 0) {
            start = UIUtils.getConferenceStart();
            end = UIUtils.getConferenceEnd();
        }
        if (start == 0 || end == 0 || end == Long.MAX_VALUE) {
            return; // Nothing useful to show yet
        }

        final long now = System.currentTimeMillis();
        final int number = MeetingPreferences.getCurrentMeetingNumber(activity);
        final String city = MeetingPreferences.getCurrentMeetingCity(activity);

        mStatusTextView = (TextView) activity.getLayoutInflater().inflate(
                R.layout.whats_on_status, mRootView, false);
        mRootView.addView(mStatusTextView);

        if (now < start) {
            updateCountdownText(number, city, start, now);
            scheduleNextDayRefresh();
        } else if (now > end) {
            int nextNumber = MeetingPreferences.getNextMeetingNumber(activity);
            String nextCity = MeetingPreferences.getNextMeetingCity(activity);
            // Avoid "See you at IETF-N" when prefs still point at the meeting that just ended.
            if (nextNumber > 0 && nextNumber != number && !TextUtils.isEmpty(nextCity)) {
                mStatusTextView.setText(getString(
                        R.string.whats_on_see_you, nextNumber, nextCity));
            } else if (nextNumber > 0 && nextNumber != number) {
                mStatusTextView.setText(getString(
                        R.string.whats_on_see_you_no_city, nextNumber));
            } else {
                mStatusTextView.setText(R.string.whats_on_thank_you_title);
            }
        } else {
            if (number > 0 && !TextUtils.isEmpty(city)) {
                mStatusTextView.setText(getString(
                        R.string.whats_on_welcome, number, city));
            } else if (number > 0) {
                mStatusTextView.setText(getString(
                        R.string.whats_on_welcome_no_city, number));
            } else {
                mStatusTextView.setText(R.string.whats_on_welcome_generic);
            }
        }
    }

    /**
     * Full calendar days from local today until the meeting's local start date.
     */
    private static int fullLocalDaysUntil(long startMillis, long nowMillis) {
        ZoneId local = ZoneId.systemDefault();
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(local).toLocalDate();
        LocalDate startDay = Instant.ofEpochMilli(startMillis).atZone(local).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, startDay);
        return (int) Math.max(0, days);
    }

    private void updateCountdownText(int number, String city, long startMillis, long now) {
        if (mStatusTextView == null) {
            return;
        }
        if (now >= startMillis) {
            mHandler.post(new Runnable() {
                public void run() {
                    refresh();
                }
            });
            return;
        }

        final int days = fullLocalDaysUntil(startMillis, now);
        if (number > 0 && !TextUtils.isEmpty(city)) {
            mStatusTextView.setText(getResources().getQuantityString(
                    R.plurals.whats_on_countdown_to_meeting, days, days, number, city));
        } else if (number > 0) {
            mStatusTextView.setText(getResources().getQuantityString(
                    R.plurals.whats_on_countdown_to_meeting_no_city, days, days, number));
        } else {
            mStatusTextView.setText(getResources().getQuantityString(
                    R.plurals.whats_on_countdown_title, days, days, ""));
        }
    }

    /** Recompute when the local calendar day rolls over (full-day countdown). */
    private void scheduleNextDayRefresh() {
        ZoneId local = ZoneId.systemDefault();
        ZonedDateTime nextMidnight = ZonedDateTime.now(local).toLocalDate()
                .plusDays(1).atStartOfDay(local);
        long delayMs = Math.max(1000L,
                nextMidnight.toInstant().toEpochMilli() - System.currentTimeMillis());
        mHandler.postDelayed(mDayRolloverRunnable, delayMs);
    }

    private final Runnable mDayRolloverRunnable = new Runnable() {
        public void run() {
            refresh();
        }
    };
}
