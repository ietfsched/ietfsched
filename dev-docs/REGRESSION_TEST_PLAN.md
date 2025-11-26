# Regression Test Plan - Espresso Tests

## Overview

This document outlines the regression test plan for the IETF Schedule app using Espresso UI testing framework. Tests focus on main app features and user flows, using the "tls" meeting as a stable test case.

**Testing Approach**: All tests are **black-box UI tests** - we test through the user interface only. No direct database/ContentProvider access, no JavaScript injection/execution, and no white-box testing of internal implementation details.

## Test Environment

- **Primary**: Physical device (Pixel 8a, API 36) - All 23 tests passing
- **Secondary**: Android Emulator (API 35 or lower recommended)
- **Test Meeting**: "tls" (assumed to always exist)
- **Note**: Tests verified working on physical device. Emulator may have different behavior.

## Test Scenarios

### 1. Home Screen Navigation
**Objective**: Verify main navigation buttons work correctly

**Tests**:
- [x] Home screen displays correctly (`testHomeScreenDisplays`)
  - Verify all main buttons are visible: Schedule, Sessions, Starred
  - Verify "Now Playing" section is visible at bottom
- [x] "Schedule" button navigates to schedule (`testScheduleButton`)
  - Click Schedule button
  - Verify ScheduleActivity is displayed
  - Verify schedule content is displayed
- [x] "Sessions" button navigates to sessions list (`testSessionsButton`)
  - Click Sessions button
  - Verify sessions list is displayed
  - Verify search box is visible
- [x] "Starred" button navigates to starred items (`testStarredButton`)
  - Click Starred button
  - Verify StarredActivity is displayed
  - Verify starred content is displayed (may be empty initially)

**Test Data**: None required

### 2. Session List and Search
**Objective**: Verify session listing and filtering

**Tests**:
- [x] Sessions list displays correctly (`testSessionsListDisplays`)
  - Navigate to Sessions list
  - Verify list is displayed
  - Verify search box is visible
- [x] Search functionality works (search for "tls") (`testSearchFunctionality`)
  - Enter "tls" in search box
  - Verify search results filter to matching sessions
  - Verify "tls" session appears in results (case-insensitive)
  - Uses `onData()` to verify list items contain search term
- [x] Clicking a session opens detail view (`testClickingSessionOpensDetail`)
  - Click on "tls" session from search results
  - Verify session detail is displayed
  - Verify session title contains "tls" (case-insensitive)

**Test Data**: Requires "tls" session to exist (verified through UI, not DB access)

### 3. Session Detail - Content Tab
**Objective**: Verify Content tab displays correctly

**Tests**:
- [x] Content tab is visible and accessible (`testContentTabDisplays`)
  - Open session detail for "tls"
  - Explicitly click Content tab to ensure visibility
  - Verify TabHost is visible
  - Verify session title and subtitle display correctly
  - Verify Content tab container is visible

**Test Data**: Requires "tls" session with Content tab data

### 4. Session Detail - Agenda Tab
**Objective**: Verify Agenda tab functionality

**Tests**:
- [x] Agenda tab is visible and accessible (`testAgendaTabLoads`)
  - Click on Agenda tab
  - Wait for agenda to load
  - Verify WebView container is visible
- [ ] Plain text agendas are styled correctly (`testPlainTextAgendaStyling` - TODO)
  - If agenda is plain text, verify:
    - Text is readable (not all jumbled)
    - Newlines are preserved (white-space: pre-wrap)
    - Font is appropriate size
    - Text color is readable
- [ ] URLs in plain text are clickable (`testUrlsInPlainTextAreClickable` - TODO)
  - If plain text agenda contains URLs, verify:
    - URLs are displayed as clickable links (blue, underlined)
    - Can tap on URL links
- [ ] External links open in system browser (`testExternalLinksOpenInBrowser` - TODO)
  - If agenda contains external links, tap one
  - Verify Intent is fired to open external browser
  - Note: May need to use IntentsTestRule to verify intent

**Test Data**: Requires "tls" session with agenda URL

**Implementation Notes**:
- WebView content verification: Use console logs and UI visibility checks (no JS injection/execution)
- Network requests may need custom IdlingResource
- External link testing requires IntentsTestRule

### 5. Session Detail - Notes Tab
**Objective**: Verify Notes tab functionality

**Tests**:
- [x] Notes tab is visible and accessible (`testNotesTabLoads`)
  - Click on Notes tab
  - Wait for Notes editor to load
  - Verify GeckoView container is visible
- [ ] Can switch between "view" and "edit" (`testNotesViewEditSwitch` - TODO)
  - Verify pencil icon is visible in edit mode
  - Click pencil icon to switch to view mode
  - Verify eye icon is visible in view mode
  - Click eye icon to switch back to edit mode
  - Verify mode switching works without errors

**Test Data**: Requires "tls" session, Notes tab uses GeckoView

**Implementation Notes**:
- GeckoView loading requires custom IdlingResource
- Text input in GeckoView may require special handling

### 6. Session Detail - Join Tab
**Objective**: Verify Join tab for Meetecho Lite

**Tests**:
- [x] Join tab is visible and accessible (`testJoinTabLoads`)
  - Click on Join tab
  - Wait for Meetecho Lite page to load
  - Verify GeckoView container is visible
  - Note: This test may fail if meeting is not active
- [ ] Group acronym is extracted correctly (`testGroupAcronymExtraction` - TODO)
  - Verify URL contains correct group acronym
  - Verify URL format: `https://meetings.conf.meetecho.com/onsite{N}/?group={group}`
  - Verify group acronym matches session title format

**Test Data**: Requires "tls" session with title format "area - group - title"

**Implementation Notes**:
- Meetecho Lite tests are time-sensitive (only work during meeting week)
- May need to skip or mark as conditional test
- GeckoView URL verification: Check console logs and UI visibility (no JS injection/execution)

### 7. Tab Switching
**Objective**: Verify tab navigation works smoothly

**Tests**:
- [x] Can switch between all tabs (`testTabSwitching`)
  - Start on Content tab
  - Switch to Agenda tab, verify content loads
  - Switch to Notes tab, verify editor loads
  - Switch to Join tab, verify page loads
  - Switch back to Content tab, verify content still displayed
- [ ] Tab state is preserved when switching (`testTabStatePreservation` - TODO)
  - On Content tab, note scroll position
  - Switch to Agenda tab
  - Switch back to Content tab
  - Verify scroll position is preserved (or at least tab content is still there)
- [x] Back button works correctly from each tab (`testBackButtonFromTabs`)
  - From Content tab, press back, verify returns to sessions list
  - From Agenda tab, press back, verify returns to sessions list
  - From Notes tab, press back, verify returns to sessions list

**Test Data**: Requires "tls" session with all tabs populated

**Implementation Notes**:
- Tab state preservation may be difficult to verify precisely
- Back button testing uses Espresso.pressBack()

### 8. Session Starring
**Objective**: Verify starring functionality

**Tests**:
- [x] Star button is visible in session detail (`testStarButtonIsVisible`)
  - Open "tls" session detail
  - Verify star button (CheckBox) is visible in header
  - Verify star button is clickable
- [x] Can star a session (`testCanStarSession`)
  - Click star button to star the session
  - Uses `DatabaseUpdateIdlingResource` to wait for async database update
  - Verify star button state changes to checked
- [x] Starred session appears in Starred list (`testStarredSessionAppearsInList`)
  - Navigate back to home (handles multiple back presses)
  - Click "Starred" button
  - Uses `ListQueryIdlingResource` to wait for list query
  - Verify "tls" session appears in starred list using `onData()`
- [x] Can unstar a session (`testCanUnstarSession`)
  - Open "tls" session detail again
  - Click star button to unstar
  - Uses `DatabaseUpdateIdlingResource` to wait for async database update
  - Verify star button state changes to unchecked
  - Navigate to Starred list
  - Uses `ListQueryIdlingResource` to wait for list query
  - Verify starred list is displayed (may be empty after unstarring)

**Test Data**: Requires "tls" session, may need to clean up starred state between tests

**Implementation Notes**:
- May need @Before/@After methods to reset starred state (via UI, not DB)
- All state verification done through UI (no direct database/ContentProvider access)

### 9. Note Well - TODO
**Objective**: Verify Note Well functionality

**Tests**:
- [ ] Note Well screen is accessible
- [ ] Note Well content displays correctly
- [ ] Navigation works correctly

**Status**: Not yet implemented in app

### 10. Feedback Link - TODO
**Objective**: Verify feedback functionality

**Tests**:
- [ ] Feedback link is accessible
- [ ] Feedback form/action works correctly

**Status**: Not yet implemented in app

## Test Implementation

### Test Classes Structure

```
app/src/androidTest/java/org/ietf/ietfsched/
├── HomeScreenTest.java          - Home navigation tests
├── SessionsListTest.java        - Session list and search tests
├── SessionDetailTest.java       - Session detail tab tests
├── SessionStarringTest.java     - Starring functionality tests
└── TestUtils.java               - Helper methods and utilities
```

### Key Test Utilities

**TestUtils.java** - Helper methods for:
- Waiting for initial database sync (two toast notifications)
- Navigating to sessions via UI (no direct DB/ContentProvider access)
- Waiting for network loads (custom IdlingResource)
- Waiting for GeckoView loads (custom IdlingResource)
- Waiting for WebView loads
- Verifying tab state
- Resetting test data via UI (starred state, etc.)

**Matchers**:
- Use `onView()` with `withId()` and `withText()` matchers
- Use `onWebView()` for WebView content verification
- Use `onData()` for ListView/AdapterView items

**IdlingResources**:
- `SyncIdlingResource` - Wait for background sync operations to complete (monitors `HomeActivity.isRefreshing()`)
- `DatabaseUpdateIdlingResource` - Wait for checkbox state to stabilize after database updates (used for star button)
- `ListQueryIdlingResource` - Wait for ListFragment queries to complete and list/empty view to become visible

**Test Rules**:
- `ActivityScenarioRule` - Launch activities for testing
- `IntentsTestRule` - Verify Intent launches (for external browser)
- `GrantPermissionRule` - Grant runtime permissions if needed

### Test Data Setup

**Before Tests**:
- Launch app (if fresh install, wait for initial sync: two toast notifications)
- Call `TestUtils.waitForInitialSync()` if needed (after fresh install)
- Ensure app is synced with meeting data
- Verify "tls" session exists (by searching in UI, not DB access)
- Clear starred state for "tls" session (via UI: navigate to session, unstar if needed)
- Ensure network connectivity

**After Tests**:
- Clean up starred state (via UI: navigate to session, unstar if needed)
- Reset any test data modifications (all via UI, no DB access)

### Espresso Dependencies

Add to `app/build.gradle`:
```gradle
dependencies {
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
    androidTestImplementation 'androidx.test.espresso:espresso-contrib:3.6.1'
    androidTestImplementation 'androidx.test.espresso:espresso-web:3.6.1'
    androidTestImplementation 'androidx.test.espresso:espresso-intents:3.6.1'
    androidTestImplementation 'androidx.test:runner:1.5.2'
    androidTestImplementation 'androidx.test:rules:1.5.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
}

android {
    defaultConfig {
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

### Common Test Patterns

**Waiting for Initial Sync (after fresh install)**:
```java
// After app installation, wait for two toast notifications indicating sync completion
TestUtils.waitForInitialSync();
// Or manually wait for toasts:
// onView(withText(R.string.sync_complete))
//     .inRoot(withDecorView(not(is(activity.getWindow().getDecorView()))))
//     .check(matches(isDisplayed()));
```

**Waiting for Async Operations**:
```java
// Register IdlingResource
IdlingRegistry.getInstance().register(networkIdlingResource);

// Perform actions that trigger async operations
onView(withId(R.id.some_button)).perform(click());

// Unregister when done
IdlingRegistry.getInstance().unregister(networkIdlingResource);
```

**WebView Content Verification**:
```java
// Use console logs and UI visibility checks instead of JS execution
// Check for visible elements, error messages, loading indicators
// Review console logs via Logcat for page load status
onView(withId(R.id.agenda_webview))
    .check(matches(isDisplayed()));
// Verify no error messages are shown
```

**GeckoView Content Verification**:
```java
// Use console logs and UI visibility checks (no JS injection/execution)
// Use GeckoView-specific IdlingResource to wait for load
// Review console logs via Logcat for page load status
onView(withId(R.id.tab_session_notes))
    .check(matches(isDisplayed()));
```

**Intent Verification**:
```java
intended(hasAction(Intent.ACTION_VIEW));
intended(hasData(Uri.parse("https://example.com")));
```

## Running Tests

### On Emulator
```bash
./gradlew connectedAndroidTest
```

### On Specific Device
```bash
# Target specific device using ANDROID_SERIAL
export ANDROID_SERIAL=<device_serial>
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.ietf.ietfsched.SessionDetailTest

# Run specific test method
./gradlew connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.ietf.ietfsched.SessionStarringTest#testCanStarSession"

# Run all tests with timeout
timeout 600 ./gradlew connectedAndroidTest
```

## Notes

### Test Prerequisites

- **After app installation**: Wait for initial database sync (two toast notifications appear)
- Tests assume "tls" meeting exists and has agenda content
- Some tests may require network connectivity
- App must be synced with meeting data before running tests
- Emulator/device must have internet access for network-dependent tests

### Known Limitations

- **GeckoView loading**: Requires custom IdlingResources
- **WebView/GeckoView content verification**: Uses console logs and UI visibility checks (no JS injection/execution)
- **Meetecho Lite tests**: Only work during active meeting week
- **Network-dependent tests**: May fail if network is unavailable
- **Time-sensitive tests**: Some tests depend on meeting schedule
- **Android API 36**: Tests use Espresso 3.6.1 for API 36 compatibility. Verified working on Pixel 8a (API 36).
- **Navigation depth**: Some tests require multiple back presses to return to home screen (device/Android version dependent)
- **Async database updates**: Star button state changes require `DatabaseUpdateIdlingResource` to wait for database updates to complete

### Test Execution Strategy

1. **Setup Phase**:
   - Launch app via `ActivityScenarioRule` (handled by `BaseTest`)
   - **Wait for initial database sync**: `BaseTest` uses `SyncIdlingResource` to wait for sync completion
   - Navigate to test starting point

2. **Test Execution**:
   - Run tests independently (each test is self-contained)
   - Use IdlingResources for async operations:
     - `SyncIdlingResource` for background syncs
     - `DatabaseUpdateIdlingResource` for star button state changes
     - `ListQueryIdlingResource` for list queries
   - Verify expected outcomes through UI assertions

3. **Cleanup Phase**:
   - Each test cleans up its own state (navigates back, unstars if needed)
   - `BaseTest` handles common cleanup (animation disabling, Intents cleanup)

### Debugging Failed Tests

- Check logcat for errors and console logs (no JS injection needed)
- Review WebView/GeckoView console logs for page load status
- Verify network connectivity
- Verify test data exists ("tls" session)
- Check IdlingResource registration
- Verify UI elements are visible (may need scrolling)
- Check for timing issues (add explicit waits)

### Test Status Summary

**Total Tests**: 23 test methods across 5 test classes

**Implemented and Passing** (17 tests with actual test code):
- Home Screen Navigation: 4/4 tests ✅
- Session List and Search: 3/3 tests ✅
- Session Detail - Content Tab: 1/1 test ✅
- Session Detail - Agenda Tab: 1/4 tests ✅ (3 TODOs)
- Session Detail - Notes Tab: 1/2 tests ✅ (1 TODO)
- Session Detail - Join Tab: 1/2 tests ✅ (1 TODO)
- Tab Switching: 2/3 tests ✅ (1 TODO)
- Session Starring: 4/4 tests ✅

**Empty TODO Tests** (6 tests - empty methods that pass by default):
- `testPlainTextAgendaStyling` - Plain text agenda styling verification
- `testUrlsInPlainTextAreClickable` - URL clickability in plain text agendas
- `testExternalLinksOpenInBrowser` - External link Intent verification
- `testNotesViewEditSwitch` - Notes editor view/edit mode switching
- `testGroupAcronymExtraction` - Join tab URL group acronym verification
- `testTabStatePreservation` - Tab scroll position preservation

**Note**: All 23 tests pass, but 6 are empty TODO methods with no assertions. They pass by default since there's nothing to fail.

### Future Enhancements

- Implement remaining TODO tests
- Add screenshot capture on test failures
- Add test reporting (HTML reports)
- Add CI/CD integration
- Add test data fixtures
- Add mock network responses for faster tests

