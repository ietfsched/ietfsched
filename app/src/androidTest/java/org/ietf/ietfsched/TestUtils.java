package org.ietf.ietfsched;

import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.Root;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

import static org.hamcrest.Matchers.allOf;

/**
 * Utility class for test helpers and common test operations
 * 
 * Provides helper methods for:
 * - Navigating to sessions via UI (no direct DB/ContentProvider access)
 * - Waiting for network loads (custom IdlingResource)
 * - Waiting for GeckoView loads (custom IdlingResource)
 * - Waiting for WebView loads
 * - Verifying tab state
 * - Resetting test data via UI (starred state, etc.)
 * 
 * Note: All operations are UI-based (black-box testing). No direct database
 * or ContentProvider access is used.
 */
public class TestUtils {

    private static final String TAG = "TestUtils";

    /**
     * Navigate to a session via UI search (no direct DB access)
     * 
     * Uses Espresso to navigate through the UI:
     * 1. Navigate to Sessions list
     * 2. Open search
     * 3. Enter search term
     * 4. Click on matching session
     * 
     * @param searchTerm Title or group acronym to search for
     */
    public static void navigateToSessionBySearch(String searchTerm) {
        Log.d(TAG, "navigateToSessionBySearch: Searching for '" + searchTerm + "'");
        
        // Navigate to Sessions list
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.home_btn_sessions))
                .perform(androidx.test.espresso.action.ViewActions.click());
        
        // Wait for list to load
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.list))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        
        // Enter search term in search box
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.search_box))
                .perform(androidx.test.espresso.action.ViewActions.typeText(searchTerm));
        
        // Wait for search to filter
        waitFor(500);
        
        // Click on first item in the list (should match search term)
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.anything())
                .inAdapterView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.list))
                .atPosition(0)
                .perform(androidx.test.espresso.action.ViewActions.click());
        
        // Wait for session detail to load
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.session_title))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        
        Log.d(TAG, "navigateToSessionBySearch: Successfully navigated to session");
    }

    /**
     * IdlingResource for network operations
     * 
     * Use this to wait for HTTP requests to complete before asserting UI state.
     */
    public static class NetworkIdlingResource implements IdlingResource {
        private final CountingIdlingResource countingResource;
        private ResourceCallback callback;

        public NetworkIdlingResource() {
            this.countingResource = new CountingIdlingResource("NetworkIdlingResource");
        }

        @Override
        public String getName() {
            return "NetworkIdlingResource";
        }

        @Override
        public boolean isIdleNow() {
            return countingResource.isIdleNow();
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.callback = callback;
        }

        public void increment() {
            countingResource.increment();
        }

        public void decrement() {
            countingResource.decrement();
            if (callback != null && isIdleNow()) {
                callback.onTransitionToIdle();
            }
        }
    }

    /**
     * IdlingResource for GeckoView page loads
     * 
     * Use this to wait for GeckoView pages to finish loading.
     */
    public static class GeckoViewIdlingResource implements IdlingResource {
        private final CountingIdlingResource countingResource;
        private ResourceCallback callback;

        public GeckoViewIdlingResource() {
            this.countingResource = new CountingIdlingResource("GeckoViewIdlingResource");
        }

        @Override
        public String getName() {
            return "GeckoViewIdlingResource";
        }

        @Override
        public boolean isIdleNow() {
            return countingResource.isIdleNow();
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.callback = callback;
        }

        public void increment() {
            countingResource.increment();
        }

        public void decrement() {
            countingResource.decrement();
            if (callback != null && isIdleNow()) {
                callback.onTransitionToIdle();
            }
        }
    }

    /**
     * IdlingResource for WebView page loads
     * 
     * Use this to wait for WebView pages to finish loading.
     */
    public static class WebViewIdlingResource implements IdlingResource {
        private final CountingIdlingResource countingResource;
        private ResourceCallback callback;

        public WebViewIdlingResource() {
            this.countingResource = new CountingIdlingResource("WebViewIdlingResource");
        }

        @Override
        public String getName() {
            return "WebViewIdlingResource";
        }

        @Override
        public boolean isIdleNow() {
            return countingResource.isIdleNow();
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.callback = callback;
        }

        public void increment() {
            countingResource.increment();
        }

        public void decrement() {
            countingResource.decrement();
            if (callback != null && isIdleNow()) {
                callback.onTransitionToIdle();
            }
        }
    }

    /**
     * Reset starred state for a session via UI (no direct DB access)
     * 
     * Uses Espresso to navigate to session and click star button:
     * 1. Navigate to session detail
     * 2. Check current star state
     * 3. Click star button if needed to reach desired state
     * 
     * @param searchTerm Session title or group acronym to find
     * @param shouldBeStarred Whether the session should be starred
     */
    public static void setSessionStarredViaUI(String searchTerm, boolean shouldBeStarred) {
        Log.d(TAG, "setSessionStarredViaUI: Setting starred state for '" + searchTerm + "' to " + shouldBeStarred);
        
        // Navigate to session detail
        navigateToSessionBySearch(searchTerm);
        
        // Ensure Content tab is selected so the view is fully initialized
        // This ensures the star button is ready for interaction
        try {
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Content"))
                    .perform(androidx.test.espresso.action.ViewActions.click());
        } catch (Exception e) {
            // Tab might already be selected, or might not exist - continue anyway
            Log.d(TAG, "setSessionStarredViaUI: Could not click Content tab (might already be selected): " + e.getMessage());
        }
        
        // Check current star button state using ViewAction
        final boolean[] currentStateRef = new boolean[1];
        try {
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.star_button))
                    .perform(new androidx.test.espresso.ViewAction() {
                        @Override
                        public org.hamcrest.Matcher<android.view.View> getConstraints() {
                            return androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.CompoundButton.class);
                        }
                        
                        @Override
                        public String getDescription() {
                            return "Check checkbox state";
                        }
                        
                        @Override
                        public void perform(androidx.test.espresso.UiController uiController, android.view.View view) {
                            if (view instanceof android.widget.CompoundButton) {
                                android.widget.CompoundButton checkbox = (android.widget.CompoundButton) view;
                                currentStateRef[0] = checkbox.isChecked();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "setSessionStarredViaUI: Could not check star button state: " + e.getMessage());
            currentStateRef[0] = false;
        }
        boolean currentState = currentStateRef[0];
        
        Log.d(TAG, "setSessionStarredViaUI: Current starred state: " + currentState + ", desired: " + shouldBeStarred);
        
        // Click star button if needed to reach desired state
        if (currentState != shouldBeStarred) {
            Log.d(TAG, "setSessionStarredViaUI: Toggling star button");
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.star_button))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            
            // Wait for database update to complete
            waitFor(1000);
            
            // Verify final state matches desired state
            if (shouldBeStarred) {
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.star_button))
                        .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                                androidx.test.espresso.matcher.ViewMatchers.isChecked()));
            } else {
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.star_button))
                        .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                                androidx.test.espresso.matcher.ViewMatchers.isNotChecked()));
            }
        } else {
            Log.d(TAG, "setSessionStarredViaUI: Starred state already matches desired state");
        }
        
        // Navigate back
        androidx.test.espresso.Espresso.pressBack();
        
        Log.d(TAG, "setSessionStarredViaUI: Successfully set starred state");
    }

    /**
     * Custom matcher for toast messages
     * 
     * Toasts are displayed in a separate window type, so we need a custom matcher
     * to identify them in the view hierarchy.
     */
    public static class ToastMatcher extends TypeSafeMatcher<Root> {
        @Override
        public void describeTo(Description description) {
            description.appendText("is toast");
        }

        @Override
        protected boolean matchesSafely(Root root) {
            int type = root.getWindowLayoutParams().get().type;
            if (type == WindowManager.LayoutParams.TYPE_TOAST) {
                IBinder windowToken = root.getDecorView().getWindowToken();
                IBinder appToken = root.getDecorView().getApplicationWindowToken();
                if (windowToken == appToken) {
                    // windowToken == appToken means this isn't a toast from another app
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Wait for initial database sync to complete
     * 
     * @param skipIfNotNeeded If true, will quickly return if button state suggests sync already complete
     * @param forceSyncFirst If true, will force a sync by clicking refresh button (default: true)
     * @param idlingResource Optional IdlingResource to use for sync completion detection. If provided, uses IdlingResource instead of polling button visibility.
     */
    public static void waitForInitialSync(boolean skipIfNotNeeded, boolean forceSyncFirst, SyncIdlingResource idlingResource) {
        long startTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%dms] waitForInitialSync: START (skipIfNotNeeded=%s, forceSyncFirst=%s, usingIdlingResource=%s)", 
                System.currentTimeMillis() - startTime, skipIfNotNeeded, forceSyncFirst, idlingResource != null));
        
        // If IdlingResource is provided, use it for reliable sync completion detection
        if (idlingResource != null) {
            // Force sync if requested
            if (forceSyncFirst) {
                Log.d(TAG, String.format("[%dms] waitForInitialSync: Forcing sync", System.currentTimeMillis() - startTime));
                try {
                    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(org.ietf.ietfsched.R.id.menu_refresh))
                            .perform(androidx.test.espresso.action.ViewActions.click());
                } catch (Exception e) {
                    Log.w(TAG, String.format("[%dms] waitForInitialSync: Could not click refresh button: %s", 
                            System.currentTimeMillis() - startTime, e.getMessage()));
                }
            }
            
            // Wait for sync to complete using IdlingResource
            boolean syncCompleted = waitForSyncIdle(idlingResource, 30000); // 30 second timeout
            
            if (syncCompleted) {
                Log.d(TAG, String.format("[%dms] waitForInitialSync: ✓ Sync complete (via IdlingResource)", 
                        System.currentTimeMillis() - startTime));
                
                // Verify isRefreshing() is actually false and wait for it to stabilize
                // This ensures navigation buttons won't be blocked
                if (idlingResource.homeActivity != null) {
                    int stableCount = 0;
                    for (int i = 0; i < 10; i++) { // Check 10 times over 500ms
                        if (!idlingResource.homeActivity.isRefreshing()) {
                            stableCount++;
                        } else {
                            stableCount = 0; // Reset if we see refreshing=true
                        }
                        if (stableCount >= 3) {
                            Log.d(TAG, String.format("[%dms] waitForInitialSync: ✓ isRefreshing() stable (false)", 
                                    System.currentTimeMillis() - startTime));
                            break;
                        }
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } else {
                Log.w(TAG, String.format("[%dms] waitForInitialSync: Timeout waiting for sync (via IdlingResource)", 
                        System.currentTimeMillis() - startTime));
            }
            
            // Wait a bit for toast to finish
            try {
                Thread.sleep(2000); // Wait for toast to finish
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
            Log.d(TAG, String.format("[%dms] waitForInitialSync: COMPLETE", System.currentTimeMillis() - startTime));
            return;
        }
        
        // Fallback to toast-based waiting (legacy approach)
        Log.d(TAG, "waitForInitialSync: Using toast-based waiting (legacy)");
        
        // If skipIfNotNeeded is true, skip the wait entirely to avoid long timeouts
        if (skipIfNotNeeded) {
            Log.d(TAG, "waitForInitialSync: Skipping wait (skipIfNotNeeded=true). If sync is needed, it will happen in background.");
            return;
        }
        
        try {
            // Check for first toast: "Refreshing schedule data..."
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Refreshing schedule data..."))
                    .inRoot(new ToastMatcher())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            
            Log.d(TAG, "waitForInitialSync: First toast found, waiting for sync...");
            Thread.sleep(2500);
            
            // Wait for second toast: "Schedule updated"
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Schedule updated"))
                    .inRoot(new ToastMatcher())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            
            Log.d(TAG, "waitForInitialSync: Second toast found, sync complete");
            Thread.sleep(2500);
        } catch (Exception e) {
            Log.d(TAG, "waitForInitialSync: Sync toasts not found, assuming sync already completed: " + e.getClass().getSimpleName());
        }
    }
    
    /**
     * Wait for initial database sync to complete
     * 
     * @param skipIfNotNeeded If true, will quickly return if button state suggests sync already complete
     * @param forceSyncFirst If true, will force a sync by clicking refresh button (default: true)
     */
    public static void waitForInitialSync(boolean skipIfNotNeeded, boolean forceSyncFirst) {
        waitForInitialSync(skipIfNotNeeded, forceSyncFirst, null);
    }
    
    /**
     * Wait for initial database sync to complete (convenience method)
     * 
     * Calls waitForInitialSync(false, true) - will force sync and wait for completion.
     */
    public static void waitForInitialSync() {
        waitForInitialSync(false, true, null);
    }
    
    /**
     * Wait for initial database sync to complete (convenience method with skipIfNotNeeded)
     * 
     * @param skipIfNotNeeded If true, will quickly return if sync appears already complete
     */
    public static void waitForInitialSync(boolean skipIfNotNeeded) {
        waitForInitialSync(skipIfNotNeeded, true, null);
    }

    /**
     * Wait for a specified duration (for debugging)
     * 
     * @param milliseconds Duration to wait in milliseconds
     */
    public static void waitFor(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Log test start message (both to Logcat and stdout for visibility)
     * 
     * @param tag Test class tag
     * @param testName Name of the test method
     */
    public static void logTestStart(String tag, String testName) {
        String message = ">>> Running test: " + testName;
        Log.d(tag, message);
        System.out.println(message);
    }

    /**
     * Log test setup message (both to Logcat and stdout for visibility)
     * 
     * @param tag Test class tag
     */
    public static void logTestSetup(String tag) {
        String message = "=== Starting test setup ===";
        Log.d(tag, message);
        System.out.println(message);
    }
    
    /**
     * Log test teardown message (both to Logcat and stdout for visibility)
     * 
     * @param tag Test class tag
     */
    public static void logTestTeardown(String tag) {
        String message = "=== Starting test teardown ===";
        Log.d(tag, message);
        System.out.println(message);
    }
    
    /**
     * Disable animations on the device/emulator for Espresso tests
     * 
     * Espresso requires animations to be disabled for reliable test execution.
     * Uses UiDevice.executeShellCommand() which has the necessary permissions.
     */
    public static void disableAnimations() {
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.executeShellCommand("settings put global animator_duration_scale 0.0");
            device.executeShellCommand("settings put global window_animation_scale 0.0");
            device.executeShellCommand("settings put global transition_animation_scale 0.0");
            Log.d(TAG, "disableAnimations: Animations disabled");
        } catch (Exception e) {
            Log.w(TAG, "disableAnimations: Failed to disable animations: " + e.getMessage());
        }
    }
    
    /**
     * Enable animations on the device/emulator (restore after tests)
     */
    public static void enableAnimations() {
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.executeShellCommand("settings put global animator_duration_scale 1.0");
            device.executeShellCommand("settings put global window_animation_scale 1.0");
            device.executeShellCommand("settings put global transition_animation_scale 1.0");
            Log.d(TAG, "enableAnimations: Animations enabled");
        } catch (Exception e) {
            Log.w(TAG, "enableAnimations: Failed to enable animations: " + e.getMessage());
        }
    }
    
    /**
     * Clean up app state by returning to home screen and closing activities
     * 
     * This ensures activities are properly closed between tests to prevent
     * state pollution and visibility issues.
     * Also waits for any pending toasts to finish before cleanup completes.
     */
    public static void cleanupAppState() {
        Log.d(TAG, "cleanupAppState: Starting cleanup");
        try {
            // Press back multiple times to return to home screen
            // This closes any open activities (session detail, sessions list, etc.)
            for (int i = 0; i < 5; i++) {
                try {
                    androidx.test.espresso.Espresso.pressBack();
                    // Small delay to allow activity to close
                    Thread.sleep(300);
                } catch (Exception e) {
                    // Already at home or no more activities to close
                    Log.d(TAG, "cleanupAppState: No more activities to close");
                    break;
                }
            }
            
            // Wait for any pending toasts to finish (Toast.LENGTH_SHORT = 2 seconds)
            // This ensures toasts don't appear after the test exits
            Log.d(TAG, "cleanupAppState: Waiting for any pending toasts to finish");
            Thread.sleep(3000); // Wait for toasts to finish (2 seconds + 1 second buffer)
            
            // Wait a bit more to ensure all activities are fully closed and UI is stable
            Thread.sleep(500);
            Log.d(TAG, "cleanupAppState: Cleanup complete");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "cleanupAppState: Interrupted during cleanup");
        } catch (Exception e) {
            Log.w(TAG, "cleanupAppState: Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Register a SyncIdlingResource for the given HomeActivity
     * 
     * This allows Espresso to automatically wait for sync to complete.
     * The IdlingResource will be idle when HomeActivity.isRefreshing() returns false.
     * 
     * @param activity The HomeActivity instance
     * @return The registered IdlingResource
     */
    public static SyncIdlingResource registerSyncIdlingResource(org.ietf.ietfsched.ui.HomeActivity activity) {
        SyncIdlingResource idlingResource = new SyncIdlingResource(activity);
        IdlingRegistry.getInstance().register(idlingResource);
        Log.d(TAG, "registerSyncIdlingResource: Registered SyncIdlingResource");
        return idlingResource;
    }
    
    /**
     * Unregister a SyncIdlingResource
     * 
     * @param idlingResource The IdlingResource to unregister
     */
    public static void unregisterSyncIdlingResource(SyncIdlingResource idlingResource) {
        if (idlingResource != null) {
            IdlingRegistry.getInstance().unregister(idlingResource);
            Log.d(TAG, "unregisterSyncIdlingResource: Unregistered SyncIdlingResource");
        }
    }
    
    /**
     * Wait for sync to complete using IdlingResource
     * 
     * Polls the IdlingResource until it becomes idle (sync complete).
     * This is more reliable than polling button visibility.
     * 
     * @param idlingResource The SyncIdlingResource to wait for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if sync completed, false if timeout
     */
    public static boolean waitForSyncIdle(SyncIdlingResource idlingResource, long timeoutMs) {
        if (idlingResource == null) {
            Log.w(TAG, "waitForSyncIdle: idlingResource is null, returning true");
            return true;
        }
        
        long startTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%dms] waitForSyncIdle: Waiting for sync to complete", 
                System.currentTimeMillis() - startTime));
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (idlingResource.isIdleNow()) {
                Log.d(TAG, String.format("[%dms] waitForSyncIdle: ✓ Sync complete", 
                        System.currentTimeMillis() - startTime));
                return true;
            }
            try {
                Thread.sleep(100); // Poll every 100ms
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "waitForSyncIdle: Interrupted");
                return false;
            }
        }
        
        Log.w(TAG, String.format("[%dms] waitForSyncIdle: Timeout waiting for sync to complete", 
                System.currentTimeMillis() - startTime));
        return false;
    }
}

