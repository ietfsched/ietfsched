# IETF Schedule App - Changes Log

## Overview

This document provides a detailed chronological log of all changes made to the IETF Schedule Android app, organized by feature area and git commits.

---

## Release: v2.0 (Current - usability-features branch)

### Feature: Dynamic Meeting Detection

**Tag**: `v1.0-dynamic-meetings` (base for usability features)

#### New Files Created
- `MeetingMetadata.java` - Data model for meeting information
- `MeetingDetector.java` - Auto-detection logic with caching
- `MeetingPreferences.java` - Persistent storage for meeting info

#### Files Modified
- `SyncService.java` - Integrated MeetingDetector, removed hardcoded meeting 122
- `UIUtils.java` - Changed from static to dynamic timezone/dates
- `Meeting.java` - Uses dynamic UIUtils.getConferenceTimeZone()
- `ParserUtils.java` - Added updateTimezone() method
- `strings.xml` - Changed app name from "IETF 123" to "IETF Schedule"

#### Commits
- Initial implementation of dynamic meeting detection
- Added smart caching with jitter
- Fixed timezone conversion issues
- Added agenda availability checking

---

### Feature: Meetecho Integration

**Status**: ✅ Completed

#### Changes to SessionDetailFragment.java
1. **URL Construction**:
   - Extract group acronym from session title (format: "area - group - title")
   - Construct dynamic URL: `https://meetings.conf.meetecho.com/onsite{N}/?group={group}`
   - Get meeting number from `MeetingPreferences.getCurrentMeetingNumber()`

2. **Styling Implementation**:
   - Created `createMeetechoButton()` helper method
   - Green gradient background (#388E3C → #66BB6A)
   - White bold text, rounded corners (4dp radius)
   - Extra padding (16dp horizontal, 12dp vertical)
   - Positioned flush right, horizontally aligned with Agenda link

3. **Layout**:
   - Wrapped Agenda link and Meetecho button in horizontal LinearLayout
   - Added spacing (8dp left margin on button, 16dp right margin)
   - Added 16dp vertical margin between button and "Presentation Slides" header

#### Commits
- feat: Add Meetecho Lite button to session details
- style: Apply green gradient styling to Meetecho button
- fix: Extract group acronym from session title instead of track query
- refactor: Move Meetecho button to align with Agenda link
- refactor: Extract createMeetechoButton() helper method

---

### Feature: Presentation Slides Display

**Status**: ✅ Completed

#### Changes to SessionDetailFragment.java
1. **Parsing Logic**:
   - Split `SESSION_PDF_URL` by "::" delimiter (multiple slides)
   - Parse each entry as "title|||url"
   - Display actual presentation titles instead of raw URLs

2. **Visual Improvements**:
   - Added gray gradient section header "Presentation Slides"
   - Only show header if slides exist (hasValidSlides check)
   - Created `createSectionHeader()` helper method
   - Consistent styling with other section headers

#### Commits
- fix: Parse and display multiple presentation slides with titles
- style: Add section header for Presentation Slides
- refactor: Extract createSectionHeader() helper method

---

### Feature: Session Starring

**Status**: ✅ Completed

#### Changes to list_item_session.xml
- Changed star from `ImageView` to `CheckBox` for better state management
- Added `android:button="@drawable/btn_star"` for star drawable
- Added right padding (48dp) to text container to prevent overlap

#### Changes to SessionsFragment.java
1. **Implementation**:
   - Created `setupStarButton()` helper method (eliminates duplication)
   - Added click handler to toggle `SESSION_STARRED` in database
   - Immediate UI update using CheckBox.setChecked()
   - Applied to both `SessionsAdapter` and `SearchAdapter`

2. **Query Changes**:
   - Added `SESSION_STARRED` to projection
   - Bind starred state in `bindView()`

#### Commits
- feat: Implement session starring in list views
- fix: Change star button to CheckBox for better state handling
- fix: Add padding to prevent star/text overlap
- refactor: Extract setupStarButton() helper method

---

### Feature: Session Search/Filter

**Status**: ✅ Completed

#### New File Created
- `fragment_sessions_with_search.xml` - Custom layout with persistent search box

#### Changes to SessionsFragment.java
1. **Layout Selection**:
   - Detect if viewing full sessions list vs block-specific list
   - Inflate custom layout only for full list (has search)
   - Use default layout for blocks (no search)

2. **Search Implementation**:
   - Added `EditText` with `TextWatcher` for real-time filtering
   - Search box is sibling to ListView (not header - remains visible when empty)
   - Auto-focus search box with keyboard on view creation
   - Filter uses CursorAdapter's built-in Filter mechanism

3. **Empty State**:
   - Styled "No sessions found" message
   - Consistent with "Starred" tab empty state
   - Search box remains visible even when no results

#### Changes to strings.xml
- Added `hint_search_sessions` string resource

#### Commits
- feat: Add session search filter with persistent search box
- fix: Keep search box visible when no results found
- fix: Only show search box for full sessions list, not blocks
- style: Improve "no sessions found" empty state styling

---

### Feature: Direct Sessions Access

**Status**: ✅ Completed

#### Changes to DashboardFragment.java
- Changed "Sessions" button to open full sessions list directly
- Removed intermediate tracks selection screen
- Intent now points to `ScheduleContract.Sessions.CONTENT_URI`

#### Commits
- feat: Replace tracks screen with direct sessions list

---

### Feature: Agenda WebView

**Status**: ✅ Completed

#### New Files Created
- `AgendaActivity.java` - Host activity for agenda viewing
- `AgendaFragment.java` - WebView fragment for displaying agenda

#### Implementation
1. **WebView Configuration**:
   - Load session agenda URL in WebView
   - `WebViewClient.shouldOverrideUrlLoading()` opens links in external browser
   - JavaScript disabled (not needed for agendas)
   - Zoom controls enabled

2. **Title Formatting**:
   - Extract area and group from session title
   - Display as "Agenda - area - group" in action bar
   - Title set before `super.onCreate()` to avoid override

3. **Navigation**:
   - Back button navigates WebView history
   - Falls back to activity back when WebView history empty
   - Links within agenda open in external browser

#### Changes to AndroidManifest.xml
- Added `AgendaActivity` declaration
- Set `hardwareAccelerated="false"` for compatibility

#### Changes to SessionDetailFragment.java
- Changed Agenda link click handler to launch `AgendaActivity`
- Refactored: extracted `openAgendaInWebView()` helper method

#### Commits
- feat: Implement in-app agenda WebView
- feat: Add custom title formatting for Agenda view
- fix: Set agenda title before super.onCreate() to prevent override
- refactor: Extract openAgendaInWebView() helper method

---

### Feature: Note Well WebView

**Status**: ✅ Completed

#### Changes to WellNoteFragment.java
1. **Markdown to HTML Conversion**:
   - Replaced Markwon TextView with WebView
   - Uses `commonmark-java` library for parsing
   - Converts markdown to HTML with CSS styling
   - Wraps in HTML template with basic styling

2. **In-App Navigation**:
   - `WebViewClient` keeps links within WebView
   - Back button navigates WebView history
   - Proper back navigation to home screen

3. **Persistence Fix**:
   - Load Note Well from SharedPreferences on startup
   - Fallback message if not yet downloaded
   - Restored to static variable after loading

#### Changes to WellNoteActivity.java
- Overrode `onBackPressed()` to delegate to fragment
- Allows WebView back navigation before activity back

#### Changes to SyncService.java
- Added persistence to SharedPreferences after download
- Store in `ietfsched_sync` prefs under `note_well_content` key

#### Changes to build.gradle
- Added `commonmark-java` dependency (0.21.0)
- Removed `markwon` dependency

#### Changes to RemoteExecutor.java
- Fixed: Preserve newlines in `executeGet()` for proper markdown formatting

#### Commits
- feat: Convert Note Well to WebView with markdown-to-HTML
- feat: Implement back button handling for Note Well WebView
- fix: Add SharedPreferences persistence for Note Well content
- fix: Preserve newlines in downloaded text for markdown rendering
- refactor: Remove unused Markwon dependency

---

### Feature: Code Cleanup - Tracks Removal

**Status**: ✅ Completed

#### Files Deleted (788 lines)
- `TracksActivity.java` - Phone activity for tracks
- `TracksFragment.java` - Tracks list fragment
- `TracksAdapter.java` - Tracks adapter
- `TracksDropdownFragment.java` - Tablet tracks dropdown
- `SessionsMultiPaneActivity.java` - Tablet multi-pane (dependent on tracks)

#### Changes to AndroidManifest.xml
- Removed `TracksActivity` declaration

#### Changes to DashboardFragment.java
- Removed unused `SessionsMultiPaneActivity` import

#### Commits
- refactor: Remove obsolete tracks-related code (788 lines)

---

### Feature: Code Cleanup - Google Moderator Removal

**Status**: ✅ Completed

#### Changes to SessionDetailFragment.java
- Removed `MODERATOR_URL` from `LINKS_INDICES` array
- Removed `R.string.session_link_moderator` from `LINKS_TITLES` array

#### Changes to strings.xml
- Removed `session_link_moderator` ("Ask questions")
- Removed `session_moderator_status` (question/vote count)
- Removed `moderator_header`
- Removed `dialog_moderator_title`
- Removed `dialog_moderator_message`
- Removed `dialog_moderator_web`
- Removed `dialog_moderator_market`

#### Changes to ids.xml
- Removed `dialog_moderator` ID

#### Note
Database column `SESSION_MODERATOR_URL` kept for backward compatibility (was never populated)

#### Commits
- refactor: Remove obsolete Google Moderator references

---

### Feature: Code Cleanup - Deprecated API Fixes

**Status**: ✅ Completed

#### Changes to RemoteExecutor.java
- Fixed deprecated `URL(String)` constructor usage (deprecated in Java 20 / JEP 418)
- Replaced with `new URI(urlString).toURL()` pattern
- Applied to: `executeHead()`, `executeGet()`, `executeJSONGet()`
- Added `import java.net.URI`

#### Commits
- fix: Replace deprecated URL(String) constructor with URI.toURL()

---

### Feature: Code Cleanup - SessionDetailFragment Refactoring

**Status**: ✅ Completed

#### Extracted Helper Methods
1. `createGradient(int, int, float)` - Create gradient drawables
2. `createSectionHeader(int)` - Create section headers with gray gradient
3. `createSeparator()` - Create standard horizontal separators
4. `createThinSeparator()` - Create thin 1px separator lines
5. `createMeetechoButton(String)` - Create styled Meetecho button
6. `openAgendaInWebView(String)` - Open agenda in AgendaActivity

#### Impact
- Reduced ~85 lines of duplicate code
- Improved maintainability and readability
- Consistent styling across all UI elements

#### Commits
- refactor: Extract UI helper methods in SessionDetailFragment

---

### Feature: Code Cleanup - Locale Fixes

**Status**: ✅ Completed

#### Files Fixed
1. **LocalExecutor.java**:
   - `toLowerCase(Locale.ROOT)`
   - `String.format(Locale.ROOT, ...)`

2. **Meeting.java**:
   - `SimpleDateFormat(..., Locale.ROOT)`
   - `String.format(Locale.ROOT, ...)`

3. **ParserUtils.java**:
   - `toLowerCase(Locale.ROOT)`
   - `String.format(Locale.ROOT, ...)`
   - `SimpleDateFormat(..., Locale.ROOT)`

4. **SessionDetailFragment.java**:
   - `toLowerCase(Locale.ROOT)` for group acronym extraction

#### Rationale
Internal identifiers and formats should use `Locale.ROOT` to be independent of user's device locale

#### Commits
- fix: Use explicit Locale.ROOT for internal string operations
- fix: Add Locale.ROOT to SimpleDateFormat constructors

---

## Bug Fixes

### Note Well Not Loaded on App Start
**Problem**: Static variable reset when app process killed

**Fix**: Added SharedPreferences persistence in `SyncService` and `WellNoteFragment`

**Commit**: fix: Add SharedPreferences persistence for Note Well content

---

### Markdown Bullet Points Not Rendered
**Problem**: Newlines stripped in `RemoteExecutor.executeGet()`

**Fix**: Preserve newlines when downloading text content

**Commit**: fix: Preserve newlines in downloaded text for markdown rendering

---

### Agenda Title Not Showing Correctly
**Problem**: Title set after `super.onCreate()`, overridden by manifest label

**Fix**: Set title before calling `super.onCreate()` in `AgendaActivity`

**Commit**: fix: Set agenda title before super.onCreate() to prevent override

---

### Search Box Disappearing When No Results
**Problem**: Search box added as ListView header, disappears when list empty

**Fix**: Made search box sibling to ListView in custom layout

**Commit**: fix: Keep search box visible when no results found

---

### Stars Overlapping Text
**Problem**: Star button and text in same layout without spacing

**Fix**: Added 48dp right padding to text container

**Commit**: fix: Add padding to prevent star/text overlap

---

### Group Acronym Extraction Failing
**Problem**: Track query commented out, couldn't get group acronym

**Fix**: Extract group directly from session title (format: "area - group - title")

**Commit**: fix: Extract group acronym from session title instead of track query

---

## Summary Statistics

### Lines of Code
- **Added**: ~1,500 lines (new features)
- **Removed**: ~1,000 lines (dead code cleanup)
- **Modified**: ~500 lines (refactoring)
- **Net**: ~+500 lines

### Files
- **Created**: 7 new files
- **Deleted**: 5 obsolete files
- **Modified**: ~20 existing files

### Features Completed
- ✅ Dynamic meeting detection
- ✅ Meetecho integration
- ✅ Session starring
- ✅ Session search/filter
- ✅ Agenda WebView
- ✅ Note Well WebView
- ✅ Presentation slides display
- ✅ Code modernization
- ✅ Bug fixes

### Features Deferred
- ⏳ End-to-end UI tests
- ⏳ Settings screen
- ⏳ Manual meeting selector UI
- ⏳ Offline-first architecture

---

## Git Tags

- `v1.0-dynamic-meetings` - Initial dynamic meeting detection implementation
- `usability-features` (current branch) - All UX improvements and cleanup
- `agenda-webview` - Agenda WebView feature completion
- (More tags as features are completed and released)

---

## Dependencies Changes

### Added
- `org.commonmark:commonmark:0.21.0` - Markdown to HTML conversion

### Removed
- `io.noties.markwon:core:4.2.0` - Replaced by commonmark-java

### Updated
- Various AndroidX libraries (part of AGP upgrade)

---

## Build Configuration Changes

### Gradle
- Android Gradle Plugin upgraded (via Android Studio)
- Changed from `jcenter()` to `mavenCentral()`
- Commented out release signing for development

### Manifest
- Added `AgendaActivity`
- Removed `TracksActivity`
- Hardware acceleration settings adjusted

---

## Testing Status

### Manual Testing
- ✅ All features tested on emulator
- ✅ Core flows verified
- ⏳ Physical device testing pending
- ⏳ Different Android versions pending

### Automated Testing
- ⏳ Espresso UI tests not yet implemented
- ⏳ Unit tests minimal (existing codebase)

---

## Known Limitations

1. **First Launch Requires Network**:
   - Must download meeting data and schedule
   - After first sync, works offline

2. **No Manual Meeting Selector UI**:
   - Can only view current meeting automatically
   - Manual override requires code changes

3. **No Settings Screen**:
   - Can't view current meeting info in UI
   - No manual refresh trigger

4. **Minimal Test Coverage**:
   - Heavy reliance on manual testing
   - No regression test suite

---

## Future Roadmap

### Short Term (Next Release)
- Settings screen for meeting info
- Manual meeting selector UI
- Improved offline support
- End-to-end UI tests

### Medium Term
- WorkManager for background sync
- Retrofit for network layer
- Room for database layer
- MVVM architecture with ViewModels

### Long Term
- Kotlin migration
- Coroutines for async operations
- Compose UI
- Multi-meeting support

---

## Contributors

- AI Assistant (Claude): Implementation and documentation
- User (Yaron Sheffer): Product requirements, testing, code review

---

## References

- IETF Datatracker: https://datatracker.ietf.org
- Android Developer Docs: https://developer.android.com
- Meetecho Guide: https://www.ietf.org/meeting/technology/meetecho-guide-participant/

