package org.ietf.ietfsched;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.ietf.ietfsched.R;
import org.ietf.ietfsched.ui.HomeActivity;
import org.ietf.ietfsched.ui.phone.ScheduleActivity;
import org.ietf.ietfsched.ui.phone.SessionsActivity;
import org.ietf.ietfsched.ui.StarredActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Regression tests for Home screen navigation
 * 
 * Tests verify that main navigation buttons work correctly and navigate to expected screens.
 */
@RunWith(AndroidJUnit4.class)
public class HomeScreenTest {
    private static final String TAG = "HomeScreenTest";

    @Rule
    public ActivityScenarioRule<HomeActivity> activityRule =
            new ActivityScenarioRule<>(HomeActivity.class);

    @Before
    public void setUp() {
        TestUtils.logTestSetup(TAG);
        Intents.init();
        // Wait for initial sync if needed (after fresh install)
        // Use skipIfNotNeeded=true to avoid long waits on subsequent runs
        TestUtils.waitForInitialSync(true);
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void testHomeScreenDisplays() {
        TestUtils.logTestStart(TAG, "testHomeScreenDisplays");
        // Verify home screen elements are visible
        onView(withId(R.id.home_btn_schedule))
                .check(ViewAssertions.matches(isDisplayed()));
        onView(withId(R.id.home_btn_sessions))
                .check(ViewAssertions.matches(isDisplayed()));
        onView(withId(R.id.home_btn_starred))
                .check(ViewAssertions.matches(isDisplayed()));
        
        // Verify "Now Playing" section is visible at bottom
        onView(withId(R.id.fragment_now_playing))
                .check(ViewAssertions.matches(isDisplayed()));
    }

    @Test
    public void testScheduleButton() {
        TestUtils.logTestStart(TAG, "testScheduleButton");
        // Click Schedule button
        onView(withId(R.id.home_btn_schedule))
                .perform(click());
        
        // Verify we navigated to ScheduleActivity
        intended(hasComponent(ScheduleActivity.class.getName()));
        
        // Verify schedule content is displayed
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
        
        // Navigate back
        pressBack();
    }

    @Test
    public void testSessionsButton() {
        TestUtils.logTestStart(TAG, "testSessionsButton");
        // Click Sessions button
        onView(withId(R.id.home_btn_sessions))
                .perform(click());
        
        // Verify we navigated to SessionsActivity
        intended(hasComponent(SessionsActivity.class.getName()));
        
        // Verify sessions list content is displayed
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
        
        // Navigate back
        pressBack();
    }

    @Test
    public void testStarredButton() {
        TestUtils.logTestStart(TAG, "testStarredButton");
        // Click Starred button
        onView(withId(R.id.home_btn_starred))
                .perform(click());
        
        // Verify we navigated to StarredActivity
        intended(hasComponent(StarredActivity.class.getName()));
        
        // Verify starred content is displayed (may be empty initially)
        onView(withId(android.R.id.content))
                .check(ViewAssertions.matches(isDisplayed()));
        
        // Navigate back
        pressBack();
    }
}

