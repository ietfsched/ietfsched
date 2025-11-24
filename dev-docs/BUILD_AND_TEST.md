# Building and Testing the IETF Schedule App

## Quick Start Guide

### 1. Install Android Studio

1. Download Android Studio from: https://developer.android.com/studio
2. Install with default settings
3. During first run, let it download the Android SDK

### 2. Open the Project

```bash
cd /Users/ysheffer/misc/ietfsched
```

Then in Android Studio:
- File → Open
- Navigate to `/Users/ysheffer/misc/ietfsched/ietfsched`
- Click "Open"

### 3. Gradle Sync

Android Studio will automatically start a Gradle sync. If not:
- File → Sync Project with Gradle Files

Wait for Gradle to download dependencies (first time may take 5-10 minutes).

### 4. Create Virtual Device (Emulator)

If you don't have a physical Android device:

1. Tools → Device Manager (or AVD Manager)
2. Click "Create Device"
3. Select a device (e.g., "Pixel 6")
4. Select a system image (e.g., Android 14 / API 34)
5. Download the system image if prompted
6. Click "Finish"

### 5. Build and Run

1. Select your device/emulator from the dropdown at the top
2. Click the green "Run" button (▶️) or press `Shift + F10`
3. First build may take a few minutes

---

## Automated Regression Tests

The app includes Espresso UI tests for regression testing of main features.

### Running Tests

**Quick Start:**
```bash
cd ietfsched
./run_tests.sh
```

The test script will:
- Automatically find and connect to emulators/devices
- Start an emulator if none is running
- Run all regression tests
- Display progress with test names

**Manual Execution:**
```bash
cd ietfsched
./gradlew connectedAndroidTest
```

**Run Specific Test Class:**
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.ietf.ietfsched.HomeScreenTest
```

### Test Coverage

The regression tests cover:
- **Home Screen Navigation** - Verifies main navigation buttons work
- **Session List & Search** - Tests session listing and search functionality
- **Session Detail Tabs** - Tests Content, Agenda, Notes, and Join tabs
- **Session Starring** - Tests star/unstar functionality

### Test Details

See **[REGRESSION_TEST_PLAN.md](REGRESSION_TEST_PLAN.md)** for:
- Complete test scenarios
- Implementation details
- Test data requirements
- Known limitations

### Test Prerequisites

- App must be installed on device/emulator
- **IMPORTANT**: Tests require Android API 35 or lower. API 36 (Android 16) is not supported by Espresso 3.6.1
- After fresh install, tests wait for initial database sync (two toast notifications)
- Tests use "tls" session as stable test case
- Network connectivity required for some tests

### Test Output

Test results are available at:
```
app/build/reports/androidTests/connected/debug/index.html
```

Tests log progress to both Logcat and stdout, showing:
- Test setup messages
- Individual test names as they run
- Test completion status

---

## Feature Testing Guide

### Test 1: Dynamic Meeting Detection

**What to test**: App automatically detects and displays current IETF meeting

**Steps**:
1. Launch the app
2. Pull down to refresh (or wait for automatic sync)
3. Check Logcat for meeting detection:
   ```
   Filter by: MeetingDetector, SyncService, UIUtils
   ```
4. Look for messages like:
   ```
   SyncService: Using meeting 124 - IETF 124 in Montreal
   UIUtils: Conference config updated: IETF 124 in Montreal (timezone: America/Toronto)
   ```

**Expected**: Should detect IETF 124 (current meeting) automatically

---

### Test 2: Session Starring

**What to test**: Star/unstar sessions from list views

**Steps**:
1. Tap "Sessions" button on home screen
2. Find any session in the list
3. Tap the star icon next to it
4. Star should fill in and turn yellow/gold
5. Tap star again to unstar
6. Star should become outline/empty
7. Open session details - verify star state matches
8. Go to "Starred" tab - verify starred sessions appear there
9. Close and reopen app - verify stars persist

**Expected**: Stars toggle immediately, persist across app restarts

---

### Test 3: Session Search/Filter

**What to test**: Search box filters sessions by title/content

**Steps**:
1. Tap "Sessions" button on home screen
2. Notice search box at top of screen
3. Tap search box - keyboard should appear
4. Type "security" (or any term)
5. List should filter in real-time to matching sessions
6. Type a term with no matches
7. Should see "No sessions found" message
8. Search box should remain visible (not disappear)
9. Clear search text - full list should return

**Expected**: Real-time filtering, persistent search box

---

### Test 4: Meetecho Button

**What to test**: Green "Join Meeting On Site" button appears and is styled correctly

**Steps**:
1. Open any working group session from the Sessions list
2. Scroll to "Links" section
3. Look for green button labeled "Join Meeting On Site"
4. Button should:
   - Be positioned to the right of "Agenda" link
   - Have green gradient background (darker to lighter green)
   - Have white bold text
   - Have extra padding (looks like a button, not a link)
5. Tap the button
6. Should open Meetecho URL in Chrome/external browser

**Expected URL format**: `https://meetings.conf.meetecho.com/onsite124/?group=<groupname>`

---

### Test 5: Presentation Slides

**What to test**: Slides section displays with proper titles and working links

**Steps**:
1. Find a session with slides (look for "Presentation Slides" section header)
2. Section header should have gray gradient background
3. Multiple slides should be listed with actual presentation titles
4. Tap any slide link
5. Should open PDF in external browser/viewer

**Expected**: Clear section header, titled links (not raw URLs)

---

### Test 6: Agenda WebView

**What to test**: Session agendas open in-app instead of external browser

**Steps**:
1. Open any session detail
2. Tap "Agenda" link in Links section
3. Should open in-app WebView (not external browser)
4. Title at top should show "Agenda - area - wg" format
5. Tap any link within the agenda
6. Should open in external browser (not WebView)
7. Press back button from linked page
8. Should return to app (not stay in browser)

**Expected**: In-app viewing for agenda, external links for agenda hyperlinks

---

### Test 7: Note Well Display

**What to test**: Note Well opens in WebView with proper formatting

**Steps**:
1. From home screen, tap "Note Well" button
2. Should see markdown content formatted as HTML
3. Bullets should be rendered as bullets (not plain text)
4. Headings should be styled appropriately
5. Tap any link (e.g., to IETF policies)
6. Should navigate within WebView
7. Press back button
8. Should navigate back within WebView history
9. Press back again (if needed) to return to home

**Expected**: Formatted content, in-app link navigation, proper back button handling

---

### Test 8: Schedule Block Navigation

**What to test**: Stars appear in block session lists

**Steps**:
1. From home screen, tap "Schedule" button
2. Navigate to any day tab
3. Tap any schedule block (e.g., "Wednesday Morning I")
4. Session list should open
5. Notice: No search box at top (search only for full sessions list)
6. Each session should have a star icon
7. Star a session, then go back to schedule
8. Open another block - verify star persists

**Expected**: Stars work in block views, no search box for block-specific lists

---

### Test 9: Cache Behavior

**What to test**: App doesn't reload data constantly

**Steps**:
1. Enable debug mode:
   - Open `MeetingDetector.java`
   - Change `DEBUG = false` to `DEBUG = true`
   - Rebuild and run
2. Launch app and check logs:
   ```
   MeetingDetector: Cache age: XXX ms, interval: YYY ms, valid: true
   MeetingDetector: Using cached meeting number: 124
   ```
3. Pull to refresh multiple times
4. Should see "cache valid" messages (not fetching new data every time)
5. Wait for cache to expire (or manually clear)
6. Refresh - should see fetch messages

**Expected**: Cache prevents excessive API calls

---

### Test 10: Offline Behavior

**What to test**: App works offline with cached data

**Steps**:
1. Ensure app has synced at least once (pull to refresh)
2. In emulator: Extended controls (⋮) → Network → Set to "No network"
3. Close and reopen app
4. Should display cached schedule data
5. Try opening sessions, viewing details
6. Should work (reading from database)
7. Try starring a session - should work (local database update)
8. Re-enable network and refresh
9. Changes should persist

**Expected**: Schedule viewing and starring work offline, sync resumes when online

---

## Common Test Scenarios

### Scenario: Fresh Install
1. Install app for first time
2. App should show empty state or loading indicator
3. Pull to refresh
4. Should fetch meeting data and display schedule
5. All features should work after first sync

### Scenario: Between Meetings
1. Manually set device date to between IETF 124 and 125
2. App should display IETF 124 (previous meeting with available agenda)
3. Should show message or continue to work normally
4. Once IETF 125 agenda is published, should auto-switch on next sync

### Scenario: Historical Meeting Viewing
*Note: Manual override UI not implemented - requires code change*
1. In code, call `MeetingPreferences.setManualOverride(context, true, 123)`
2. Rebuild and run
3. Should display IETF 123 data
4. Useful for testing with older meeting data

---

## Debugging Tips

### Enable Detailed Logging

Set debug flags to true in:
- `SyncService.java`: `debug = true`
- `MeetingDetector.java`: `DEBUG = true`

### View Logs in Android Studio

1. Run app in debug mode
2. Open Logcat tab (bottom of window)
3. Filter by tag: `MeetingDetector|SyncService|UIUtils|SessionDetailFragment`
4. Set log level to "Debug" or "Verbose"

### Inspect SharedPreferences

Use Android Studio's Device File Explorer:
1. View → Tool Windows → Device File Explorer
2. Navigate to: `data/data/org.ietf.ietfsched/shared_prefs/`
3. View:
   - `meeting_detector_prefs.xml` - Cache info
   - `meeting_prefs.xml` - Current meeting display info
   - `ietfsched_sync.xml` - Note Well content and sync status

### Inspect Database

1. In Device File Explorer, navigate to:
   `data/data/org.ietf.ietfsched/databases/`
2. Download `schedule.db`
3. Open in SQLite browser tool
4. Check `sessions` table for SESSION_STARRED values

---

## Common Issues

### Issue: "Failed to detect current meeting"

**Causes**:
- No network connection
- IETF Datatracker API is down
- API response format changed

**Solutions**:
- Check network connectivity
- Test API manually: `curl https://datatracker.ietf.org/api/v1/meeting/meeting/?format=json&type=ietf`
- Check logcat for detailed error messages

### Issue: Stars don't persist

**Causes**:
- Database write failed
- App data cleared
- Star click handler not working

**Solutions**:
- Check logcat for SQL errors
- Verify SESSION_STARRED column exists in database
- Check click listener is attached to star button

### Issue: Search box doesn't appear

**Causes**:
- Viewing block-specific session list (by design)
- Wrong layout inflated

**Solutions**:
- Search only appears for full sessions list (from "Sessions" button)
- Block views intentionally don't have search (usually short lists)

### Issue: Build errors

**Solutions**:
- If you see "Could not find method jcenter()": Fixed - now uses mavenCentral()
- If you see "Could not get unknown property 'release'": Fixed - release signing commented out
- File → Invalidate Caches → Invalidate and Restart
- Build → Clean Project, then Build → Rebuild Project

---

## Testing Checklist

### Automated Regression Tests

Before committing changes, run the automated regression tests:
```bash
cd ietfsched
./run_tests.sh
```

All tests should pass. See [REGRESSION_TEST_PLAN.md](REGRESSION_TEST_PLAN.md) for complete test details.

### Manual Feature Testing

Before considering features complete, verify:

**Core Functionality**
- [ ] App launches successfully
- [ ] Displays correct meeting number (124 for current)
- [ ] Shows correct location (Montreal for IETF 124)
- [ ] Schedule loads and displays sessions
- [ ] Timezone conversions work correctly

**Dynamic Features**
- [ ] Cache prevents excessive API calls
- [ ] Refresh button triggers sync
- [ ] App works offline (with cached data)
- [ ] Logs show proper meeting detection

**Session Management**
- [ ] Stars toggle in session lists
- [ ] Stars toggle in block views
- [ ] Stars persist across app restarts
- [ ] Starred tab shows correct sessions

**Search and Filter**
- [ ] Search box appears in full sessions list
- [ ] Search filters in real-time
- [ ] Search box remains visible with no results
- [ ] Search box doesn't appear in block views

**Links and WebViews**
- [ ] Meetecho button appears and is styled correctly
- [ ] Meetecho button opens correct URL
- [ ] Agenda opens in WebView
- [ ] Agenda links open in external browser
- [ ] Note Well displays with proper formatting
- [ ] Note Well links navigate in-app
- [ ] Back button works in WebViews
- [ ] Presentation slides show with titles

**Stability**
- [ ] No crashes in logcat
- [ ] No memory leaks
- [ ] Smooth scrolling and navigation
- [ ] Fast startup time

---

## Performance Notes

### API Call Frequency

With implemented caching:
- During meeting: ~24 calls per day (hourly with jitter)
- Between meetings: ~1 call per day

### Network Usage

Each API call:
- Meeting list: ~10-50 KB
- Meeting details: ~1-5 KB
- Agenda check (HEAD): < 1 KB
- Agenda data: ~100-500 KB
- Note Well: ~5-10 KB

**Total**: Minimal impact on user's data plan

---

## Next Steps After Testing

Once you've verified everything works:

1. **Disable Debug Logging**:
   - Set all `debug`/`DEBUG` flags back to `false`
   - Rebuild for release

2. **Generate Release APK**:
   - Build → Generate Signed Bundle / APK
   - Follow Android Studio prompts

3. **Update Version Number**:
   - In `app/build.gradle`, increment `versionCode` and `versionName`

4. **Test Release Build**:
   - Install release APK on test device
   - Verify everything works without debug logs

5. **Deploy**:
   - Upload to Google Play Store
   - Or distribute APK directly to users

---

## Additional Documentation

- **[OVERVIEW.md](OVERVIEW.md)** - Project overview and architecture
- **[IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md)** - Technical implementation details
- **[CHANGES_LOG.md](CHANGES_LOG.md)** - Detailed changelog
- **[REGRESSION_TEST_PLAN.md](REGRESSION_TEST_PLAN.md)** - Automated Espresso test plan and implementation

