# Sync Event Sequence and Validation

## Expected Time-Ordered Sequence of Events

### During `waitForInitialSync()`:

1. **Test calls `waitForInitialSync(false, true)`**
   - Log: `[Xms] waitForInitialSync: START`

2. **`forceSync()` clicks refresh button**
   - Log: `[Xms] waitForInitialSync: STEP 1 - Clicking refresh button to force sync`
   - Log: `[Xms] waitForInitialSync: STEP 1 - ✓ Refresh button clicked`

3. **HomeActivity.onOptionsItemSelected() -> triggerRefresh()**
   - Starts SyncService with STATUS_RECEIVER

4. **SyncService starts, sends STATUS_RUNNING**
   - Log: `onReceiveResult: STATUS_RUNNING received -> setting mSyncing=true`

5. **HomeActivity.onReceiveResult(STATUS_RUNNING)**
   - Sets `mSyncing = true`
   - Calls `updateRefreshStatus(true)`
   - Log: `updateRefreshStatus: refreshing=true, isRefreshing()=true`
   - Log: `updateRefreshStatus: complete, isRefreshing()=true`

6. **Refresh button disappears (View.GONE)**
   - Log: `[Xms] waitForInitialSync: STEP 2 - ✓ Refresh button disappeared (sync started)`

7. **SyncService processes sync**
   - Fetches data from remote server
   - Updates database via ContentProvider
   - ContentProvider notifies observers

8. **SyncService sends STATUS_FINISHED**
   - Log: `onReceiveResult: STATUS_FINISHED received -> setting mSyncing=false`

9. **HomeActivity.onReceiveResult(STATUS_FINISHED)**
   - Sets `mSyncing = false`
   - Shows toast "Schedule updated"
   - Calls `updateRefreshStatus(false)`
   - Log: `updateRefreshStatus: refreshing=false, isRefreshing()=false`
   - Log: `updateRefreshStatus: complete, isRefreshing()=false`

10. **Refresh button reappears (View.VISIBLE)**
    - Log: `[Xms] waitForInitialSync: STEP 3 - ✓ Refresh button reappeared (sync completed)`

11. **`isRefreshing()` returns false**
    - Should return `mSyncing` value (false)

12. **Home screen buttons become clickable**
    - Log: `[Xms] waitForInitialSync: STEP 4 - ✓ Home screen buttons ready, sync completed successfully`
    - Log: `[Xms] waitForInitialSync: COMPLETE`

### During Test Execution:

13. **Test clicks Sessions button**
    - Log: `testSessionsListDisplays: Attempting to click Sessions button...`
    - DashboardFragment.onClick() checks `isRefreshing()`
    - Log: `home_btn_sessions onClick: isRefreshing()=X`
    - If true: Shows toast "Check/Upload new agenda, please wait" and returns early
    - Log: `home_btn_sessions onClick: BLOCKED - sync in progress`
    - If false: Launches intent
    - Log: `home_btn_sessions onClick: ALLOWED - launching SessionsActivity`

14. **Intent launched (if not blocked)**
    - Log: `testSessionsListDisplays: Sessions button clicked successfully`
    - Log: `testSessionsListDisplays: Verifying intent was launched...`
    - Intent: ACTION_VIEW with Sessions.CONTENT_URI

15. **SessionsActivity launches**
    - SessionsFragment queries ContentProvider
    - List is populated and displayed

16. **Test verifies list is displayed**
    - Log: `testSessionsListDisplays: Waiting for sessions list to appear...`
    - Log: `testSessionsListDisplays: Sessions list displayed`

## Logging Added

### TestUtils.java
- Timestamped logs for each step of `waitForInitialSync()`
- Format: `[Xms] waitForInitialSync: STEP N - description`

### HomeActivity.java
- Logs in `onReceiveResult()` for each status code
- Logs in `updateRefreshStatus()` showing state changes
- Logs `isRefreshing()` value before and after updates

### DashboardFragment.java
- Logs in `home_btn_sessions` onClick handler
- Shows `isRefreshing()` value
- Shows whether click was blocked or allowed

### SessionsListTest.java
- Logs at each step of test execution
- Shows when button click is attempted
- Shows when intent verification happens

## Current Issue

The test is failing with "Wanted to match 1 intents. Actually matched 0 intents."

This indicates that step 13 is being blocked - `isRefreshing()` is returning `true` even after step 10 completes (refresh button reappears).

## Possible Causes

1. **Race condition**: `updateRefreshStatus(false)` is called, but `isRefreshing()` check happens before state is fully updated
2. **Timing issue**: Refresh button reappears before `mSyncing` is set to false
3. **State inconsistency**: `isRefreshing()` returns a stale value

## Next Steps

1. Run test and capture logs to see actual event sequence
2. Compare actual sequence with expected sequence
3. Identify where the sequence diverges
4. Fix the timing/state issue



