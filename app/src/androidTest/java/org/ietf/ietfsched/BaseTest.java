package org.ietf.ietfsched;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.ietf.ietfsched.ui.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

/**
 * Base test class for all Espresso tests
 * 
 * Provides common setup and teardown:
 * - Disables animations (required for Espresso)
 * - Initializes Intents
 * - Registers SyncIdlingResource for sync completion detection
 * - Waits for initial sync
 * - Cleans up app state (returns to home, closes activities)
 * - Restores animations on teardown
 */
public abstract class BaseTest {
    protected static final String TAG = "BaseTest";
    private SyncIdlingResource syncIdlingResource;

    @Rule
    public ActivityScenarioRule<HomeActivity> activityRule =
            new ActivityScenarioRule<>(HomeActivity.class);

    @Before
    public void setUp() {
        TestUtils.disableAnimations(); // Required for Espresso to work reliably
        TestUtils.logTestSetup(getTestTag());
        Intents.init();
        
        // Register IdlingResource to signal when sync is complete
        // This allows Espresso to automatically wait for sync to finish
        // Use onActivity() callback - it will be called when activity is ready
        // The IdlingResource will be registered before waitForSync() uses it
        activityRule.getScenario().onActivity(activity -> {
            syncIdlingResource = TestUtils.registerSyncIdlingResource(activity);
        });
        
        // Wait for sync to complete - buttons are disabled during refresh
        // Subclasses can override waitForSync() to customize sync behavior
        // Note: waitForSync() will handle the case where IdlingResource is not yet registered
        waitForSync();
    }

    @After
    public void tearDown() {
        TestUtils.logTestTeardown(getTestTag());
        
        // Unregister IdlingResource
        TestUtils.unregisterSyncIdlingResource(syncIdlingResource);
        
        // Clean up app state: return to home screen and close any open activities
        // This ensures activities are properly closed between tests to prevent
        // state pollution and visibility issues
        TestUtils.cleanupAppState();
        
        TestUtils.enableAnimations(); // Restore animations
        Intents.release();
    }

    /**
     * Get the test tag for logging
     * Subclasses should override this to return their specific tag
     */
    protected abstract String getTestTag();

    /**
     * Wait for initial sync to complete
     * Subclasses can override this to customize sync behavior
     * 
     * Uses skipIfNotNeeded=true and forceSyncFirst=false to avoid forcing sync on every test run.
     * If sync is already complete, it will skip the wait. But if sync is in progress, it will wait.
     * Uses the registered IdlingResource for reliable sync completion detection.
     * 
     * Note: If IdlingResource is not yet registered (onActivity callback hasn't fired),
     * it will fall back to button visibility polling.
     */
    protected void waitForSync() {
        // Don't force sync (forceSyncFirst=false), but wait if sync is in progress
        // skipIfNotNeeded=true means: if sync appears complete, skip the wait
        // Pass the IdlingResource for reliable sync completion detection
        // If IdlingResource is null, waitForInitialSync will fall back to button visibility polling
        TestUtils.waitForInitialSync(true, false, syncIdlingResource); // skipIfNotNeeded=true, forceSyncFirst=false
    }
}

