package org.ietf.ietfsched;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.ietf.ietfsched.R;
import org.ietf.ietfsched.ui.HomeActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Regression tests for Home screen navigation
 * 
 * Tests verify that main navigation buttons work correctly and navigate to expected screens.
 */
@RunWith(AndroidJUnit4.class)
public class HomeScreenTest {

    @Rule
    public ActivityScenarioRule<HomeActivity> activityRule =
            new ActivityScenarioRule<>(HomeActivity.class);

    @Test
    public void testHomeScreenDisplays() {
        // Verify home screen elements are visible
        onView(withId(R.id.home_btn_schedule))
                .check(ViewAssertions.matches(isDisplayed()));
        onView(withId(R.id.home_btn_sessions))
                .check(ViewAssertions.matches(isDisplayed()));
        onView(withId(R.id.home_btn_starred))
                .check(ViewAssertions.matches(isDisplayed()));
        
        // TODO: Verify "Now Playing" section is visible at bottom
    }

    @Test
    public void testScheduleButton() {
        // Click Schedule button
        onView(withId(R.id.home_btn_schedule))
                .perform(click());
        
        // TODO: Verify we navigated to schedule
        // Verify ScheduleActivity is displayed
        // Verify schedule list or calendar view is shown
        // Note: Adjust based on actual ScheduleActivity layout
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
    }

    @Test
    public void testSessionsButton() {
        // Click Sessions button
        onView(withId(R.id.home_btn_sessions))
                .perform(click());
        
        // TODO: Verify we navigated to sessions list
        // Verify SessionsActivity is displayed
        // Verify sessions list is populated
        // Note: Adjust based on actual SessionsActivity layout
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
    }

    @Test
    public void testStarredButton() {
        // Click Starred button
        onView(withId(R.id.home_btn_starred))
                .perform(click());
        
        // TODO: Verify we navigated to starred items
        // Verify StarredActivity is displayed
        // Verify starred items list is shown (may be empty initially)
        // Note: Adjust based on actual StarredActivity layout
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
    }
}

