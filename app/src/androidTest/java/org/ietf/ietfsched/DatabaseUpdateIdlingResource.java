package org.ietf.ietfsched;

import androidx.test.espresso.IdlingResource;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import org.ietf.ietfsched.R;

import java.util.concurrent.atomic.AtomicLong;

/**
 * IdlingResource that waits for checkbox state to stabilize after a click
 * 
 * This allows Espresso to wait for the checkbox state to stabilize after clicking,
 * accounting for potential query refreshes that might reset the state temporarily.
 * The resource becomes idle when the checkbox state matches the expected state
 * and has been stable for a short period.
 */
public class DatabaseUpdateIdlingResource implements IdlingResource {
    private static final String TAG = "DatabaseUpdateIdlingResource";
    private static final long STABILITY_DURATION_MS = 2000; // Wait 2 seconds for state to stabilize after database update
    
    private ResourceCallback resourceCallback;
    private View checkboxView;
    private boolean expectedCheckedState;
    private AtomicLong stableSince = new AtomicLong(0);
    
    /**
     * Set the checkbox view and expected state to monitor
     */
    public void setExpectedState(View checkboxView, boolean expectedChecked) {
        this.checkboxView = checkboxView;
        this.expectedCheckedState = expectedChecked;
        this.stableSince.set(0);
        Log.d(TAG, "setExpectedState: Monitoring checkbox, expected checked=" + expectedChecked);
    }
    
    @Override
    public String getName() {
        return "DatabaseUpdateIdlingResource";
    }
    
    @Override
    public boolean isIdleNow() {
        if (checkboxView == null || !(checkboxView instanceof CompoundButton)) {
            Log.w(TAG, "isIdleNow: Checkbox view not set or invalid, considering idle");
            return true;
        }
        
        CompoundButton checkbox = (CompoundButton) checkboxView;
        boolean currentState = checkbox.isChecked();
        boolean matchesExpected = (currentState == expectedCheckedState);
        
        long now = System.currentTimeMillis();
        long stableTime = stableSince.get();
        
        if (matchesExpected) {
            if (stableTime == 0) {
                // State just matched, start tracking stability
                stableSince.set(now);
                Log.d(TAG, "isIdleNow: State matches expected, starting stability timer");
                return false;
            } else {
                long elapsed = now - stableTime;
                if (elapsed >= STABILITY_DURATION_MS) {
                    // State has been stable long enough
                    Log.d(TAG, "isIdleNow: State stable for " + elapsed + "ms, resource is idle");
                    if (resourceCallback != null) {
                        resourceCallback.onTransitionToIdle();
                    }
                    return true;
                } else {
                    Log.d(TAG, "isIdleNow: State matches but only stable for " + elapsed + "ms, waiting");
                    return false;
                }
            }
        } else {
            // State doesn't match, reset stability timer
            if (stableTime != 0) {
                stableSince.set(0);
                Log.d(TAG, "isIdleNow: State changed, resetting stability timer");
            }
            return false;
        }
    }
    
    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.resourceCallback = callback;
    }
    
    /**
     * Reset the monitoring state
     */
    public void reset() {
        checkboxView = null;
        stableSince.set(0);
        Log.d(TAG, "reset: Reset monitoring state");
    }
}

