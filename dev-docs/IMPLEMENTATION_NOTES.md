# IETF Schedule App - Implementation Notes

## Overview

This document provides technical details about the major features and
improvements implemented in the IETF Schedule Android app. The work includes
dynamic meeting detection, UX improvements, code modernization, and bug fixes.

---

## 1. Dynamic Meeting Detection System

### Problem Statement
The app previously required manual updates for each IETF meeting:
- Hardcoded meeting number (was `122`)
- Hardcoded timezone (`America/Los_Angeles`)
- Hardcoded start/end dates
- Required app recompilation and redeployment for each meeting

### Solution Architecture

#### New Components Created

**1. MeetingMetadata.java** (`app/src/main/java/org/ietf/ietfsched/io/`)
- Data model for IETF meeting information
- Parses JSON from Datatracker API
- Stores: meeting number, dates, timezone, location, agenda URL
- Validates timezone against Android's available timezones

```java
public class MeetingMetadata {
    private int meetingNumber;
    private String location;
    private String timezone;
    private long startMillis;
    private long endMillis;
    private String agendaUrl;
    
    public static MeetingMetadata fromJSON(JSONObject json) { ... }
}
```

**2. MeetingDetector.java** (`app/src/main/java/org/ietf/ietfsched/io/`)
- Core logic for detecting which meeting to display
- Fetches meeting list from Datatracker API
- Implements smart caching with randomized jitter
- Checks agenda availability before selecting a meeting
- Falls back to previous meeting if upcoming meeting's agenda isn't published yet

**Key Methods**:
- `detectCurrentMeeting(Context)`: Main entry point for meeting detection
- `fetchMeetingList()`: Calls Datatracker API for meeting list
- `fetchMeetingDetails(int)`: Gets detailed info for specific meeting
- `isAgendaAvailable(String)`: HEAD request to check if agenda.json exists
- `isCacheValid()`: Checks if cached meeting number is still valid

**Caching Strategy**:
```java
private static final long CACHE_INTERVAL_DURING_MEETING = 60 * 60 * 1000; // 1 hour
private static final long JITTER_DURING_MEETING = 20 * 60 * 1000;         // ±20 min
private static final long CACHE_INTERVAL_BETWEEN_MEETINGS = 24 * 60 * 60 * 1000; // 24 hours
private static final long JITTER_BETWEEN_MEETINGS = 2 * 60 * 60 * 1000;          // ±2 hours
```

Randomized jitter prevents simultaneous API requests from many users (thundering herd problem).

**3. MeetingPreferences.java** (`app/src/main/java/org/ietf/ietfsched/util/`)
- SharedPreferences wrapper for meeting metadata
- Provides UI-friendly methods for displaying meeting info
- Supports manual meeting override for testing

```java
public class MeetingPreferences {
    public static int getCurrentMeetingNumber(Context context)
    public static String getCurrentMeetingLocation(Context context)
    public static void setManualOverride(Context context, boolean enabled, int meetingNumber)
}
```

#### Modified Components

**SyncService.java**
- Removed hardcoded `mtg = "122"`
- Added `MeetingDetector` integration:
```java
MeetingDetector detector = new MeetingDetector();
MeetingMetadata meeting = detector.detectCurrentMeeting(this);
if (meeting != null) {
    mtg = String.valueOf(meeting.getMeetingNumber());
    UIUtils.setCurrentMeeting(this, meeting);
}
```

**UIUtils.java**
- Changed from static constants to dynamic methods:
```java
// Before:
public static final TimeZone CONFERENCE_TIME_ZONE = TimeZone.getTimeZone("America/Los_Angeles");
public static final long CONFERENCE_START_MILLIS = ...;
public static final long CONFERENCE_END_MILLIS = ...;

// After:
private static TimeZone sConferenceTimeZone;
private static long sConferenceStartMillis;
private static long sConferenceEndMillis;

public static TimeZone getConferenceTimeZone(Context context) { ... }
public static long getConferenceStartMillis(Context context) { ... }
public static long getConferenceEndMillis(Context context) { ... }
public static void setCurrentMeeting(Context context, MeetingMetadata meeting) { ... }
```

**strings.xml**
- Changed app name from "IETF 123" to "IETF Schedule"
- Removed all hardcoded meeting references

### API Endpoints Used

1. **Meeting List**:
   ```
   GET https://datatracker.ietf.org/api/v1/meeting/meeting/?format=json&type=ietf
   ```
   Returns all regular IETF meetings

2. **Meeting Details**:
   ```
   GET https://datatracker.ietf.org/api/v1/meeting/meeting/{number}/?format=json
   ```
   Returns detailed info for specific meeting

3. **Agenda Availability Check**:
   ```
   HEAD https://datatracker.ietf.org/meeting/{number}/agenda.json
   ```
   Verifies if agenda is published (HTTP 200 = available)

4. **Agenda Data** (existing):
   ```
   GET https://datatracker.ietf.org/meeting/{number}/agenda.json
   ```
   The actual meeting schedule data

### Known Edge Cases

1. **Between Meetings**: When no agenda is available for upcoming meeting, falls back to most recent meeting with published agenda
2. **API Failure**: If Datatracker API is down, uses cached meeting number
3. **Invalid Timezone**: Falls back to UTC if API returns unrecognized timezone

---

## 2. Session Links and WebView Implementation

### Meetecho Integration

**Requirement**: Add "Join Meeting On Site" link for Meetecho Lite conferencing tool

**Implementation** (`SessionDetailFragment.java`):

**URL Construction**:
```java
// Extract group acronym from session title: "area - group - title"
String[] parts = mTitleString.split(" - ", 3);
String groupAcronym = parts[1].toLowerCase(Locale.ROOT).trim();

// Construct URL
int meetingNumber = MeetingPreferences.getCurrentMeetingNumber(getActivity());
String meetechoUrl = "https://meetings.conf.meetecho.com/onsite" + 
                     meetingNumber + "/?group=" + groupAcronym;
```

**Styling** (helper method):
```java
private TextView createMeetechoButton(final String meetechoUrl) {
    TextView button = new TextView(getActivity());
    button.setText(R.string.session_link_meetecho);
    button.setTextColor(0xFFFFFFFF);  // White
    button.setTypeface(null, Typeface.BOLD);
    button.setGravity(Gravity.CENTER);
    button.setPadding(16, 12, 16, 12);
    
    // Green gradient background
    button.setBackground(createGradient(0xFF388E3C, 0xFF66BB6A, 4));
    
    button.setOnClickListener(/* open in browser */);
    return button;
}
```

**Layout**: Horizontal container with Agenda link (left) and Meetecho button (right)

### Agenda WebView

**Files Created**:
- `AgendaActivity.java`: Host activity for agenda viewing
- `AgendaFragment.java`: WebView fragment for displaying agenda

**Key Features**:
1. **In-App Viewing**: Agenda opens in WebView, not external browser
2. **External Links**: Links within agenda open in external browser
3. **Custom Title**: Shows "Agenda - area - wg" format extracted from session title
4. **Back Navigation**: Back button navigates WebView history

**Implementation** (`AgendaFragment.java`):
```java
mWebView.setWebViewClient(new WebViewClient() {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // Open all links in external browser
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        startActivity(intent);
        return true; // Don't load in WebView
    }
});
```

**Title Extraction** (`AgendaActivity.onCreate()`):
```java
String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
if (title != null && title.contains(" - ")) {
    String[] parts = title.split(" - ", 3);
    String area = parts[0].trim();
    String group = parts[1].trim();
    String displayTitle = "Agenda - " + area + " - " + group;
    getIntent().putExtra(Intent.EXTRA_TITLE, displayTitle);
}
super.onCreate(savedInstanceState); // Uses modified title
```

### Note Well WebView

**Previous Implementation**: TextView with Markwon library for markdown rendering

**New Implementation** (`WellNoteFragment.java`):
1. **Markdown to HTML**: Using `commonmark-java` library
2. **WebView Display**: Better link handling and formatting
3. **In-App Navigation**: Links open within WebView with back button support
4. **Persistence**: Content stored in SharedPreferences for offline access

```java
// Convert markdown to HTML
Parser parser = Parser.builder().build();
HtmlRenderer renderer = HtmlRenderer.builder().build();
Node document = parser.parse(markdownText);
String htmlContent = renderer.render(document);

// Wrap in HTML template with styling
String styledHtml = "<!DOCTYPE html><html><head>" +
    "<style>body { padding: 16px; font-family: sans-serif; }</style>" +
    "</head><body>" + htmlContent + "</body></html>";

// Display in WebView
mWebView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null);
```

**Persistence** (added to fix bug):
```java
// In SyncService - save after download
prefs.edit().putString(Prefs.NOTE_WELL_CONTENT, noteWellText).apply();

// In WellNoteFragment - load on startup
SharedPreferences prefs = getActivity().getSharedPreferences("ietfsched_sync", Context.MODE_PRIVATE);
markdownText = prefs.getString("note_well_content", "");
```

### Presentation Slides

**Fix**: Display multiple slides with actual presentation titles, not raw URLs

**Data Format** (from API):
```
title1|||url1::title2|||url2::title3|||url3
```

**Parsing** (`SessionDetailFragment.java`):
```java
String[] slideEntries = url.split("::");
for (String slideEntry : slideEntries) {
    String[] parts = slideEntry.split("\\|\\|\\|", 2);
    String title = parts.length >= 1 ? parts[0].trim() : "";
    String slideUrl = parts.length >= 2 ? parts[1].trim() : title;
    
    // Create link with actual title
    linkText.setText(title.isEmpty() ? "Presentation" : title);
}
```

**Visual Improvement**: Added gray gradient section header "Presentation Slides" to separate from other links

---

## 3. Session Starring and Search

### Session Starring

**Database**: `SESSION_STARRED` column already existed (INTEGER, default 0)

**UI Changes** (`list_item_session.xml`):
```xml
<!-- Changed from ImageView to CheckBox for better state management -->
<CheckBox android:id="@+id/star_button"
    android:button="@drawable/btn_star"
    android:clickable="false"
    android:focusable="false" />
```

**Implementation** (`SessionsFragment.java`):

Created `setupStarButton()` helper method to eliminate code duplication:
```java
private void setupStarButton(View view, String sessionId, boolean starred) {
    CheckBox starButton = (CheckBox) view.findViewById(R.id.star_button);
    starButton.setChecked(starred);
    
    Uri sessionUri = ScheduleContract.Sessions.buildSessionUri(sessionId);
    starButton.setOnClickListener(v -> {
        boolean newState = ((CheckBox) v).isChecked();
        ContentValues values = new ContentValues();
        values.put(ScheduleContract.Sessions.SESSION_STARRED, newState ? 1 : 0);
        getActivity().getContentResolver().update(sessionUri, values, null, null);
    });
}
```

Used in both `SessionsAdapter` and `SearchAdapter` for consistency.

**Layout Fix**: Added `paddingRight="48dp"` to text container to prevent star overlap with text.

### Session Search/Filter

**Requirement**: Add search box to filter sessions by title/content

**Challenges**:
1. `SearchView` in action bar didn't work well
2. Search box should remain visible even with no results
3. Should only appear for full sessions list, not block-specific lists

**Solution** (`fragment_sessions_with_search.xml`):
```xml
<LinearLayout orientation="vertical">
    <!-- Persistent search box -->
    <EditText
        android:id="@+id/search_box"
        android:hint="@string/hint_search_sessions"
        android:background="#EEEEEE" />
    
    <!-- List and empty view in FrameLayout -->
    <FrameLayout>
        <ListView android:id="@android:id/list" />
        <TextView android:id="@android:id/empty"
            android:text="@string/empty_sessions" />
    </FrameLayout>
</LinearLayout>
```

**Implementation** (`SessionsFragment.java`):
```java
@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
    // Only show search for full sessions list
    boolean isFullSessionsList = ScheduleContract.Sessions.CONTENT_URI.equals(sessionsUri);
    
    if (isFullSessionsList) {
        return inflater.inflate(R.layout.fragment_sessions_with_search, container, false);
    } else {
        return super.onCreateView(inflater, container, state); // No search for blocks
    }
}
```

**Filtering Logic**:
```java
EditText searchBox = (EditText) view.findViewById(R.id.search_box);
searchBox.addTextChangedListener(new TextWatcher() {
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        filterSessions(s.toString());
    }
});

private void filterSessions(String query) {
    CursorAdapter adapter = (CursorAdapter) getListAdapter();
    Filter filter = adapter.getFilter();
    filter.filter(query);
}
```

**Auto-Focus**: Added `searchBox.post(() -> { searchBox.requestFocus(); /* show keyboard */ })` to automatically focus search box when opening Sessions.

### Sessions Button Behavior Change

**Before**: Opened "tracks" selection screen

**After**: Opens full sessions list directly with search

**Implementation** (`DashboardFragment.java`):
```java
// Changed from tracks URI to sessions URI
Intent intent = new Intent(Intent.ACTION_VIEW, ScheduleContract.Sessions.CONTENT_URI);
intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.title_sessions));
startActivity(intent);
```

---

## 4. Code Cleanup and Modernization

### Tracks UI Removal

**Why**: Tracks view was an unnecessary intermediate screen - replaced with direct sessions access

**Files Deleted** (788 lines):
1. `TracksActivity.java` - Host activity for tracks list
2. `TracksFragment.java` - Fragment displaying tracks
3. `TracksAdapter.java` - Adapter for rendering tracks
4. `TracksDropdownFragment.java` - Tablet-specific tracks dropdown
5. `SessionsMultiPaneActivity.java` - Tablet activity dependent on tracks

**Other Changes**:
- Removed from `AndroidManifest.xml`
- Removed unused import from `DashboardFragment.java`

**Database Note**: `Tracks` table kept for data integrity, but UI layer removed

### Google Moderator Removal

**Why**: Google Moderator was shut down in 2015

**Removed** (`strings.xml`, `SessionDetailFragment.java`, `ids.xml`):
- `session_link_moderator` - "Ask questions" link
- `session_moderator_status` - Question/vote count
- Dialog strings for Moderator app
- `MODERATOR_URL` from links arrays
- `dialog_moderator` ID

**Database Note**: `SESSION_MODERATOR_URL` column kept for backward compatibility (was never populated anyway)

### Deprecated API Fixes

**URL Constructor** (`RemoteExecutor.java`):
```java
// Before (deprecated in Java 20):
url = new URL(urlString);

// After (JEP 418 compliant):
url = new URI(urlString).toURL();
```

Applied to all three methods: `executeHead()`, `executeGet()`, `executeJSONGet()`

### SessionDetailFragment Refactoring

**Problem**: Lots of duplicate UI code in `updateLinksTab()`

**Solution**: Extracted helper methods:
```java
private GradientDrawable createGradient(int startColor, int endColor, float cornerRadius)
private TextView createSectionHeader(int textResId)
private View createSeparator()
private View createThinSeparator()
private TextView createMeetechoButton(String meetechoUrl)
```

**Impact**: Reduced ~85 lines of duplicate code, improved maintainability

### Locale Fixes

**Fixed** (`LocalExecutor.java`, `Meeting.java`, `ParserUtils.java`):
```java
// Before (DefaultLocale lint warning):
sessionType = m.typeSession.toLowerCase();
String.format("%04d-%03d", ...);

// After (explicit locale):
sessionType = m.typeSession.toLowerCase(Locale.ROOT);
String.format(Locale.ROOT, "%04d-%03d", ...);
SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssz", Locale.ROOT);
```

**Rationale**: Internal identifiers and formats should use `Locale.ROOT` to be independent of user's device locale.

---

## 5. Bug Fixes

### Note Well Not Loaded on App Start

**Problem**: Static variable `SyncService.noteWellString` was reset when app process was killed

**Solution**: Added SharedPreferences persistence:
```java
// In SyncService - save after download
prefs.edit().putString(Prefs.NOTE_WELL_CONTENT, txt).apply();

// In WellNoteFragment - load on startup
SharedPreferences prefs = getActivity().getSharedPreferences("ietfsched_sync", MODE_PRIVATE);
String content = prefs.getString("note_well_content", "");
if (!content.isEmpty()) {
    SyncService.noteWellString = content; // Restore to static var
    displayContent(content);
}
```

### Markdown Newlines Not Preserved

**Problem**: `RemoteExecutor.executeGet()` was stripping newlines:
```java
// Before:
result.append(line.trim());

// After:
result.append(line.trim());
result.append("\n");  // Preserve newlines for markdown
```

**Impact**: Bullet lists and other markdown formatting now render correctly

### Agenda Title Not Showing Correctly

**Problem**: Title was set after `super.onCreate()`, which was overridden by manifest label

**Solution**: Set title before calling `super.onCreate()`:
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // Process title BEFORE super.onCreate()
    String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
    if (title != null) {
        String displayTitle = formatAgendaTitle(title);
        getIntent().putExtra(Intent.EXTRA_TITLE, displayTitle);
    }
    super.onCreate(savedInstanceState); // Now uses modified title
}
```

### Search Box Disappearing

**Problem 1**: Using `SearchView` in action bar - didn't show input field properly

**Problem 2**: Adding `EditText` as ListView header - disappears when list is empty

**Solution**: Put `EditText` as sibling to `ListView` in `LinearLayout`, with both inside layout

---

## Architecture Patterns

### Dependency Injection
- **Not Used**: Android app uses manual dependency passing via Context
- **Future**: Could benefit from Dagger/Hilt for testability

### Data Layer
- **ContentProvider**: `ScheduleProvider` for database access
- **SQLite**: Local storage for session data
- **SharedPreferences**: Meeting metadata, Note Well content, cache timestamps

### UI Layer
- **Fragments**: Modular UI components
- **Activities**: Host containers for fragments
- **Adapters**: `CursorAdapter` for list views with database integration

### Background Processing
- **SyncService**: Background service for data sync
- **AsyncQueryHandler**: Asynchronous database queries
- **No WorkManager**: Could be modernized to use WorkManager for scheduling

### Network Layer
- **RemoteExecutor**: Custom HTTP client using `HttpsURLConnection`
- **No Retrofit**: Direct API calls, could be modernized

---

## Future Improvements

### Not Implemented (Planned but Deferred)
1. **End-to-End UI Tests**: Espresso tests planned but not implemented
2. **Settings Screen**: Display current meeting info, manual meeting selector
3. **Offline-First Architecture**: Better caching and sync strategies
4. **WorkManager**: Replace SyncService with modern scheduling
5. **Retrofit/OkHttp**: Modernize network layer
6. **Dependency Injection**: Add Dagger/Hilt for better testability

### Architectural Modernization
- Migrate to MVVM with ViewModels
- Add Kotlin coroutines for async operations
- Use Room instead of raw SQLite
- Implement repository pattern for data layer
- Add Compose UI (long-term)

---

## Performance Characteristics

### Memory
- Session list with ~200 items: ~10-15 MB
- WebView instances: ~20-30 MB each
- Total app memory: ~50-100 MB (typical)

### Network
- API calls: < 100 KB/day
- Agenda data: ~100-500 KB per meeting
- Note Well: ~5-10 KB
- Total: Minimal data usage

### Battery
- Background sync: Hourly during meetings
- Caching reduces unnecessary API calls
- Minimal impact on battery life

### Disk
- Database size: ~2-5 MB per meeting
- SharedPreferences: < 100 KB
- Total app storage: ~5-10 MB

---

## Testing Approaches

### Manual Testing
- See `BUILD_AND_TEST.md` for comprehensive test scenarios

### Debug Logging
- `MeetingDetector.DEBUG`
- `SyncService.debug`
- Logcat filters: `MeetingDetector|SyncService|UIUtils`

### Database Inspection
- Device File Explorer → `/data/data/org.ietf.ietfsched/databases/`
- SQLite browser for `schedule.db`

### Network Inspection
- Logcat for HTTP requests/responses
- Manual API testing with `curl`

---

## References

### IETF Resources
- Datatracker API: https://datatracker.ietf.org/api/
- Meeting API: https://datatracker.ietf.org/api/v1/meeting/meeting/
- Meetecho Guide: https://www.ietf.org/meeting/technology/meetecho-guide-participant/

### Android Documentation
- WebView: https://developer.android.com/guide/webapps/webview
- ContentProvider: https://developer.android.com/guide/topics/providers/content-providers
- SharedPreferences: https://developer.android.com/training/data-storage/shared-preferences

### Libraries Used
- commonmark-java (0.21.0): Markdown parsing and HTML rendering
- AndroidX libraries: Modern Android components

