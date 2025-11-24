package org.ietf.ietfsched;

import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.Root;
import androidx.test.espresso.idling.CountingIdlingResource;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

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
        // TODO: Implement UI-based navigation
        // Use Espresso to:
        // - Click Sessions button
        // - Open search UI
        // - Type searchTerm
        // - Click on first matching result
        // No direct ContentProvider queries
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
        // TODO: Implement UI-based starred state reset
        // Use Espresso to:
        // - Navigate to session detail via searchTerm
        // - Check current star button state
        // - Click star button if needed to reach shouldBeStarred state
        // No direct ContentProvider updates
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
     * After app installation, the app shows two toast notifications:
     * 1. "Refreshing schedule data..." (when sync starts)
     * 2. "Schedule updated" (when sync completes)
     * 
     * This method waits for both toast messages to appear and disappear.
     * If toasts don't appear (sync already completed), returns immediately.
     * 
     * Note: On subsequent test runs, sync may already be complete, so toasts
     * won't appear. In this case, Espresso will timeout (default ~10 seconds)
     * before catching the exception. To avoid this delay on subsequent runs,
     * set skipIfNotNeeded=true to quickly check and skip if toasts don't appear.
     * 
     * @param skipIfNotNeeded If true, will quickly return if toasts don't appear immediately
     */
    public static void waitForInitialSync(boolean skipIfNotNeeded) {
        Log.d(TAG, "waitForInitialSync: Checking for sync toasts (skipIfNotNeeded=" + skipIfNotNeeded + ")...");
        
        // If skipIfNotNeeded is true, skip the wait entirely to avoid long timeouts
        // This is useful on subsequent test runs when sync is already complete
        if (skipIfNotNeeded) {
            Log.d(TAG, "waitForInitialSync: Skipping wait (skipIfNotNeeded=true). If sync is needed, it will happen in background.");
            return;
        }
        
        try {
            // Check for first toast: "Refreshing schedule data..."
            // Note: This will wait up to Espresso's default timeout (~10s) if toast doesn't exist
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Refreshing schedule data..."))
                    .inRoot(new ToastMatcher())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            
            Log.d(TAG, "waitForInitialSync: First toast found, waiting for sync...");
            
            // Wait for toast to disappear (toasts typically show for 2 seconds)
            Thread.sleep(2500);
            
            // Wait for second toast: "Schedule updated"
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Schedule updated"))
                    .inRoot(new ToastMatcher())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            
            Log.d(TAG, "waitForInitialSync: Second toast found, sync complete");
            
            // Wait for toast to disappear
            Thread.sleep(2500);
        } catch (Exception e) {
            // If toast doesn't appear (e.g., sync already completed), that's okay
            Log.d(TAG, "waitForInitialSync: Sync toasts not found, assuming sync already completed: " + e.getClass().getSimpleName());
            // No need to wait - sync is already done
        }
    }
    
    /**
     * Wait for initial database sync to complete (convenience method)
     * 
     * Calls waitForInitialSync(false) - will wait for full timeout if toasts don't appear.
     * For faster execution on subsequent runs, use waitForInitialSync(true) instead.
     */
    public static void waitForInitialSync() {
        waitForInitialSync(false);
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
}

