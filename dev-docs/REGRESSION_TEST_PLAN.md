# Regression Test Plan - Espresso Tests

## Overview

This document outlines the regression test plan for the IETF Schedule app using Espresso UI testing framework. Tests focus on main app features and user flows, using the "tls" meeting as a stable test case.

**Testing Approach**: All tests are **black-box UI tests** - we test through the user interface only. No direct database/ContentProvider access, no JavaScript injection/execution, and no white-box testing of internal implementation details.

## Test Environment

- **Primary**: Android Emulator
- **Secondary**: Physical device (if available)
- **Test Meeting**: "tls" (assumed to always exist)

## Test Scenarios

### 1. Home Screen Navigation
**Objective**: Verify main navigation buttons work correctly

**Tests**:
- [ ] Home screen displays correctly
  - Verify all main buttons are visible: Schedule, Sessions, Starred
  - Verify "Now Playing" section is visible at bottom
- [ ] "Schedule" button navigates to schedule
  - Click Schedule button
  - Verify ScheduleActivity is displayed
  - Verify schedule list or calendar view is shown
- [ ] "Sessions" button navigates to sessions list
  - Click Sessions button
  - Verify SessionsActivity is displayed
  - Verify sessions list is populated
- [ ] "Starred" button navigates to starred items
  - Click Starred button
  - Verify StarredActivity is displayed
  - Verify starred items list is shown (may be empty initially)

**Test Data**: None required

### 2. Session List and Search
**Objective**: Verify session listing and filtering

**Tests**:
- [ ] Sessions list displays correctly
  - Navigate to Sessions list
  - Verify list is scrollable
  - Verify session items display title, time, room
  - Verify star buttons are visible on each item
- [ ] Search functionality works (search for "tls")
  - Open search (menu or action bar)
  - Enter "tls" in search box
  - Verify search results filter to matching sessions
  - Verify "tls" session appears in results
- [ ] Filtered results show matching sessions
  - Verify only sessions containing "tls" are shown
  - Verify case-insensitive matching works
- [ ] Clicking a session opens detail view
  - Click on "tls" session from search results
  - Verify SessionDetailActivity opens
  - Verify session title matches

**Test Data**: Requires "tls" session to exist (verified through UI, not DB access)

### 3. Session Detail - Content Tab
**Objective**: Verify Content tab displays correctly

**Tests**:
- [ ] Content tab is visible and accessible
  - Open session detail for "tls"
  - Verify Content tab is selected by default
  - Verify tab indicator shows Content tab
- [ ] Session title displays correctly
  - Verify session title is displayed in header
  - Verify title format: "area - group - title"
  - Verify star button is visible next to title
- [ ] Draft names are fetched and displayed (if available)
  - Wait for draft fetching to complete (may require IdlingResource)
  - If drafts exist, verify draft names are listed
  - Verify draft names are clickable links
  - Verify "Internet Drafts" section header appears when drafts exist
- [ ] Presentation slides are displayed (if available)
  - If slides exist, verify "Presentation Slides" section header
  - Verify slide titles are displayed (not just URLs)
  - Verify slides are clickable links

**Test Data**: Requires "tls" session with Content tab data

### 4. Session Detail - Agenda Tab
**Objective**: Verify Agenda tab functionality

**Tests**:
- [ ] Agenda tab is visible and accessible
  - Click on Agenda tab
  - Verify tab switches to Agenda
  - Verify WebView container is visible
- [ ] Agenda content loads (HTML or plain text)
  - Wait for agenda to load (may require IdlingResource for network)
  - Verify content appears in WebView
  - Verify no error messages are displayed
  - Verify loading indicator disappears
- [ ] Plain text agendas are styled correctly (only what can be done with no intrusive JS)
  - If agenda is plain text, verify:
    - Text is readable (not all jumbled)
    - Newlines are preserved (white-space: pre-wrap)
    - Font is appropriate size
    - Text color is readable
- [ ] URLs in plain text are clickable (only what can be done with no intrusive JS)
  - If plain text agenda contains URLs, verify:
    - URLs are displayed as clickable links (blue, underlined)
    - Can tap on URL links
- [ ] External links open in system browser (if such exist)
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
- [ ] Notes tab is visible and accessible
  - Click on Notes tab
  - Verify tab switches to Notes
  - Verify GeckoView container is visible
- [ ] Notes editor loads correctly
  - Wait for Notes editor to load (GeckoView may need IdlingResource)
  - Verify editor interface is displayed
  - Verify no error messages
- [ ] Can switch between "view" and "edit" (pencil/eye icon)
  - Verify pencil icon is visible in edit mode
  - Click pencil icon to switch to view mode
  - Verify eye icon is visible in view mode
  - Click eye icon to switch back to edit mode
  - Verify mode switching works without errors
- [ ] Can switch between tabs without losing state
  - Enter some text in Notes editor
  - Switch to Agenda tab
  - Switch back to Notes tab
  - Verify entered text is still present

**Test Data**: Requires "tls" session, Notes tab uses GeckoView

**Implementation Notes**:
- GeckoView loading requires custom IdlingResource
- Text input in GeckoView may require special handling

### 6. Session Detail - Join Tab
**Objective**: Verify Join tab for Meetecho Lite

**Tests**:
- [ ] Join tab is visible and accessible
  - Click on Join tab
  - Verify tab switches to Join
  - Verify GeckoView container is visible
- [ ] Meetecho Lite URL loads correctly (only if tests run during the week of the meeting)
  - Wait for Meetecho Lite page to load (GeckoView + network)
  - Verify page loads without errors
  - Verify Meetecho Lite interface is displayed
  - Note: This test may fail if meeting is not active
- [ ] Group acronym is extracted correctly from session title
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
- [ ] Can switch between all tabs (Content, Agenda, Notes, Join)
  - Start on Content tab
  - Switch to Agenda tab, verify content loads
  - Switch to Notes tab, verify editor loads
  - Switch to Join tab, verify page loads
  - Switch back to Content tab, verify content still displayed
- [ ] Tab state is preserved when switching
  - On Content tab, note scroll position
  - Switch to Agenda tab
  - Switch back to Content tab
  - Verify scroll position is preserved (or at least tab content is still there)
- [ ] Back button works correctly from each tab
  - From Content tab, press back, verify returns to sessions list
  - From Agenda tab, press back, verify returns to sessions list
  - From Notes tab, press back, verify returns to sessions list
  - From Join tab, press back, verify returns to sessions list

**Test Data**: Requires "tls" session with all tabs populated

**Implementation Notes**:
- Tab state preservation may be difficult to verify precisely
- Back button testing uses Espresso.pressBack()

### 8. Session Starring
**Objective**: Verify starring functionality

**Tests**:
- [ ] Star button is visible in session detail
  - Open "tls" session detail
  - Verify star button (CheckBox) is visible in header
  - Verify star button is clickable
- [ ] Can star a session
  - Click star button to star the session
  - Verify star button state changes to checked
  - Verify visual indicator (star filled)
- [ ] Starred session appears in Starred list
  - Navigate back to home
  - Click "Starred" button
  - Verify "tls" session appears in starred list
  - Verify star indicator is visible in list item
- [ ] Can unstar a session
  - Open "tls" session detail again
  - Click star button to unstar
  - Verify star button state changes to unchecked
  - Navigate to Starred list
  - Verify "tls" session no longer appears (or appears unstared)

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
- `NetworkIdlingResource` - Wait for HTTP requests to complete
- `GeckoViewIdlingResource` - Wait for GeckoView page loads
- `WebViewIdlingResource` - Wait for WebView page loads

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
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation 'androidx.test.espresso:espresso-contrib:3.5.1'
    androidTestImplementation 'androidx.test.espresso:espresso-web:3.5.1'
    androidTestImplementation 'androidx.test.espresso:espresso-intents:3.5.1'
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
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.ietf.ietfsched.SessionDetailTest
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

### Test Execution Strategy

1. **Setup Phase**:
   - Launch app
   - **Wait for initial database sync**: After app installation, wait for two toast notifications indicating sync completion
   - Navigate to test starting point

2. **Test Execution**:
   - Run tests in order (some depend on previous state)
   - Use IdlingResources for async operations
   - Verify expected outcomes

3. **Cleanup Phase**:
   - Reset test data (starred state, etc.)
   - Return to initial state

### Debugging Failed Tests

- Check logcat for errors and console logs (no JS injection needed)
- Review WebView/GeckoView console logs for page load status
- Verify network connectivity
- Verify test data exists ("tls" session)
- Check IdlingResource registration
- Verify UI elements are visible (may need scrolling)
- Check for timing issues (add explicit waits)

### Future Enhancements

- Add screenshot capture on test failures
- Add test reporting (HTML reports)
- Add CI/CD integration
- Add test data fixtures
- Add mock network responses for faster tests

