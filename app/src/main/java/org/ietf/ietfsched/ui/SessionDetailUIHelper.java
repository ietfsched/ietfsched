/*
 * Copyright 2025
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

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import org.ietf.ietfsched.R;

/**
 * UI helper methods for SessionDetailFragment.
 * Provides reusable methods for creating UI elements like headers, separators, and buttons.
 */
public class SessionDetailUIHelper {
    
    /**
     * Create a gradient drawable.
     */
    public static android.graphics.drawable.GradientDrawable createGradient(int startColor, int endColor, float cornerRadius) {
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {startColor, endColor});
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }
    
    /**
     * Create a section header with gradient background.
     */
    public static TextView createSectionHeader(Fragment fragment, int textResId) {
        TextView header = new TextView(fragment.getActivity());
        header.setText(textResId);
        header.setTextSize(14);
        header.setTextColor(0xFFFFFFFF);  // White text
        header.setBackground(createGradient(0xFF888888, 0xFFC0C0C0, 0));  // Gray gradient
        header.setPadding(20, 14, 16, 14);
        header.setTypeface(null, android.graphics.Typeface.ITALIC);
        return header;
    }
    
    /**
     * Create a separator view.
     */
    public static View createSeparator(Fragment fragment) {
        View separator = new ImageView(fragment.getActivity());
        separator.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        separator.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
        return separator;
    }
    
    /**
     * Create a thin separator line.
     */
    public static View createThinSeparator(Fragment fragment) {
        View separator = new ImageView(fragment.getActivity());
        separator.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        separator.setBackgroundColor(0xFFCCCCCC);
        return separator;
    }
    
    /**
     * Create Meetecho button with green gradient background.
     */
    public static TextView createMeetechoButton(Fragment fragment, final String meetechoUrl, 
            Runnable linkEventCallback) {
        TextView button = new TextView(fragment.getActivity());
        button.setText(R.string.session_link_meetecho);
        button.setTextSize(14);
        button.setTextColor(0xFFFFFFFF);  // White text
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(16, 12, 16, 12);
        
        // Green gradient background with rounded corners
        button.setBackground(createGradient(0xFF388E3C, 0xFF66BB6A, 4));
        
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (linkEventCallback != null) {
                    linkEventCallback.run();
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(meetechoUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                fragment.startActivity(intent);
            }
        });
        
        android.widget.LinearLayout.LayoutParams buttonParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.leftMargin = 8;   // Small gap between agenda and button
        buttonParams.rightMargin = 16; // Right padding to match left padding of agenda
        button.setLayoutParams(buttonParams);
        
        return button;
    }
    
    /**
     * Escape HTML special characters to prevent XSS.
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}

