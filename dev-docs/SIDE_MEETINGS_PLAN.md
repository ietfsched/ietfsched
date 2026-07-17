# Plan: Side Meetings in Schedule + Sessions

Status: **Draft for review** (feedback incorporated; not yet implemented)  
Target meeting: IETF 126 (Vienna, 2026-07-20 … 2026-07-24)  
Data: [https://sidemeetings.ietf.org/](https://sidemeetings.ietf.org/) · informal API [https://sidemeetings.ietf.org/_data](https://sidemeetings.ietf.org/_data)

Local Android cmdline tools (this machine):

```text
C:\Users\yaron\Applications\android-cmdline-tools
```

---

## Goal

Show IETF **side meetings** in the app’s **Sessions** list and on the **Schedule** grid by **repurposing the green column** for them.

Side meetings differ from WG sessions:

- Start/end times are **arbitrary** (not aligned with “Mon Session I/II/III”).
- There are only **two rooms**, so at most **two** side meetings run in parallel.
- Remote join info is usually a Webex/Zoom URL, not Meetecho.

Rationale for reclaiming green: other than Sunday, the green column is **mostly blank** today, so it is a natural home for side meetings during the week.

---

## Constraints from data + existing UI

| Fact | Implication |
|------|-------------|
| Bookings have arbitrary `start` / `end` (UTC ISO) | Each side meeting is its own time block, not folded into official session slots |
| Exactly two rooms (e.g. Park Suite 4, Grand Klimt Hall 3); ≤2 in parallel | Green column must support **two overlapping** blocks without painting over each other |
| Green today = `officehours` (registration, staff office hours, education/newcomer, …) | **Drop registration**; relocate remaining items out of green → **yellow** (see §6) |
| Schedule grid = 4 **semantic** columns (not rooms/tracks) | Side meetings map to column 2 (green); room only matters for stacking within that column |
| API `location` is usually a **Webex/Zoom URL**, not the room name | Detail “Join” opens that URL externally; physical room comes from `roomName` |
| `/_data` is an **informal** API | Fail gracefully: short timeout, no blocking wait, continue agenda sync if side-meetings fetch fails or returns junk |

### Current schedule columns (today)

| Col | Color | `block_type` | Typical content |
|-----|-------|--------------|-----------------|
| 0 Blue | `#0fabff` | `food` | Breaks, plenary, reception/social |
| 1 Red | `#df1831` | `session`, `hackathon` | Numbered WG sessions, hackathon |
| 2 Green | `#009939` | `officehours` | Registration, staff OH, education/newcomer/tools/… (sparse Mon–Fri) |
| 3 Yellow | `#F4BE36` | `nocHelpdesk` | NOC/helpdesk, AD office hours, IEPG |

Green is column index 2 in `ScheduleFragment.buildTypeColumnMap()`, colored via `block_column_3`.

### API shape (`/_data`)

```json
{
  "meeting": {
    "meetingNumber": "126",
    "meetingLocation": "…",
    "startDate": "YYYY-MM-DD",
    "endDate": "YYYY-MM-DD",
    "timezone": "Europe/Vienna"
  },
  "rooms": [
    { "id": …, "title": "…", "description": "…", "slug": "…" }
  ],
  "bookings": [
    {
      "id": …,
      "roomId": …,
      "roomName": "Park Suite 4",
      "title": "…",
      "description": "…",
      "start": "2026-07-20T07:00:00.000Z",
      "end": "2026-07-20T08:00:00.000Z",
      "location": "https://ietf.webex.com/meet/…",
      "organizerName": "…",
      "organizerEmail": "…",
      "areas": ["RTG"]
    }
  ]
}
```

Notes:

- Endpoint appears to always expose the **current** side-meetings meeting (no `{number}` in the URL).
- Times are UTC; the app stores/displays times in the **meeting timezone**.
- Empty, mismatched, broken, or slow responses must not break or delay the rest of sync.

---

## Recommended design

### 1. Sync: fetch side meetings with agenda

Use the **same meeting association** as the rest of the app: whatever `MeetingDetector` selects as the active meeting (ongoing / upcoming / previous fallback). Side meetings are imported only when they belong to that meeting.

In `SyncService`, after (or alongside) agenda sync:

1. GET `https://sidemeetings.ietf.org/_data` with a **short timeout**; never block the main agenda path on this call
2. On network/parse/timeout errors → log, skip side meetings, leave existing agenda data intact
3. If `meeting.meetingNumber` ≠ detected meeting → **skip import** and log (same “wrong week” safety as agenda association)
4. Parse `rooms` + `bookings` into the existing SQLite model (blocks + rooms + sessions)
5. Use the same sync `UPDATED` timestamp so stale side meetings are purged like agenda rows
6. Preserve `SESSION_STARRED` across replace (same as agenda sessions)

Prefer a small dedicated importer (e.g. `SideMeetingImporter`) rather than stretching agenda `Meeting` / `LocalExecutor` JSON parsing.

**Relevant existing files:**

- `app/src/main/java/org/ietf/ietfsched/service/SyncService.java`
- `app/src/main/java/org/ietf/ietfsched/io/RemoteExecutor.java`
- `app/src/main/java/org/ietf/ietfsched/io/LocalExecutor.java`
- `app/src/main/java/org/ietf/ietfsched/io/MeetingDetector.java`

### 2. Data mapping (bookings → sessions)

| API field | App field |
|-----------|-----------|
| `id` | Stable session key, e.g. `side-{id}` |
| `title` | Session title (**no** `SIDE -` text prefix; use a badge — see §4) |
| `start` / `end` | Block + session times (UTC → meeting TZ) |
| `roomName` | Room |
| `description` | Detail body (Content tab) |
| `location` | Join / remote URL (opened externally) |
| `organizerName` (+ email) | Subtitle / detail metadata |
| `areas[]` | Tags / search text |

Each booking → **one block + one session** (1:1).

Introduce a dedicated block type, e.g. `sidemeeting` (cleaner than overloading `officehours`).

Constants live near other block types in `ParserUtils` (`BLOCK_TYPE_OFFICE_HOURS`, etc.).

### 3. Schedule: green column = side meetings

Keep **four** columns; repurpose column 2:

| Col | Color | Content after change |
|-----|-------|----------------------|
| 0 Blue | food / breaks / plenary / social | Unchanged |
| 1 Red | WG sessions / hackathon | Unchanged |
| 2 Green | **Side meetings only** (`sidemeeting`) | New |
| 3 Yellow | NOC / helpdesk / AD OH / IEPG + **former green leftovers** (staff OH, education/newcomer/tools, hackathon results, …) | Expanded; registration omitted |

#### Parallel layout (required)

`BlocksLayout` currently lays out each `BlockView` as full column width:

```text
left = headerWidth + column * columnWidth
right = left + columnWidth
```

Two overlapping green blocks would cover each other.

**Fix:**

- Assign each side meeting a **sub-column 0 or 1** by room (stable sort of the two room titles/slugs from `_data.rooms`).
- For `sidemeeting` blocks:
  - **Full width** when alone in that time range
  - **Half width** when two overlap:
    - `left = headerWidth + column*columnWidth + sub*half`
    - `width = columnWidth / 2`

Block chip title: truncated booking title (not “Mon Session II”).  
Tap → existing `SessionsFragment` for that `block_id` (usually one session).

**Relevant files:**

- `app/src/main/java/org/ietf/ietfsched/ui/ScheduleFragment.java`
- `app/src/main/java/org/ietf/ietfsched/ui/widget/BlocksLayout.java`
- `app/src/main/java/org/ietf/ietfsched/ui/widget/BlockView.java`
- `app/src/main/res/values/colors.xml` (`block_column_3` green)

### 4. Sessions list — badge like BoF (not a title prefix)

Side meetings are normal `sessions` rows (list, search/FTS, starred).

**Labeling:** mirror the existing **BoF** treatment — a small badge on the list row and detail header — not a `SIDE - …` title prefix.

Existing pattern:

- DB: `SESSION_IS_BOF`
- UI: `session_bof_label` in `list_item_session.xml` / `fragment_session_detail.xml`
- String: `bof_label` (“BoF”)

For side meetings, use the same approach:

- Detect via session id prefix `side-…` **or** a small `SESSION_IS_SIDE_MEETING` flag (prefix is enough for v1; flag optional if badge wiring is cleaner)
- Add a **Side** (or **SIDE**) badge next to / analogous to the BoF badge
- Keep the session title as the booking title (areas can stay in subtitle/search text)

**Relevant files:**

- `app/src/main/java/org/ietf/ietfsched/ui/SessionsFragment.java`
- `app/src/main/res/layout/list_item_session.xml`
- `app/src/main/res/layout/fragment_session_detail.xml`
- `app/src/main/res/values/strings.xml`
- (optional) drawable similar to `bof_label_bg.xml`

### 5. Session detail (side-meeting-aware)

Reuse `SessionDetailFragment` with light branching when the session is a side meeting:

| Tab | Behavior for side meetings |
|-----|----------------------------|
| Content | Show `description` + organizer; skip slides/drafts when empty |
| Notes | **Grayed out** (visible but disabled / non-interactive) |
| Agenda | **Grayed out** (same) |
| Join | Open API `location` as an **external** URL (browser / apps that handle the link) — **not** in-app WebView, and **not** Meetecho constructed from a group acronym |

**Relevant files:**

- `app/src/main/java/org/ietf/ietfsched/ui/SessionDetailFragment.java`
- `app/src/main/java/org/ietf/ietfsched/ui/SessionJoinTabManager.java`
- `app/src/main/java/org/ietf/ietfsched/ui/SessionNotesTabManager.java`
- `app/src/main/java/org/ietf/ietfsched/ui/SessionContentTabBuilder.java`

### 6. Drop registration; relocate remaining green items → **yellow**

**Omit registration entirely** — do not create a block/session for registration (or purge if already present). It is a long, low-value schedule chip attendees rarely need.

For everything else that currently uses `BLOCK_TYPE_OFFICE_HOURS`, remap → `BLOCK_TYPE_NOC_HELPDESK` (**yellow**):

- Staff office hours (non-AD; AD already yellow)
- Education / newcomer / tools / chairs / forum / etc.
- Hackathon results / presentations (today forced into green to avoid red overlap)

Green is reserved for `sidemeeting`. Blue stays food/breaks/social only.

**Trade-off:** yellow gets a bit busier (service/admin cluster), but dropping registration removes the worst long-running collision source, and blue stays visually clean for meals/breaks.

---

## Implementation outline

Suggested order:

1. **Parser + sync** — short-timeout fetch of `_data`, map bookings → ContentProvider ops, purge, meeting-number guard aligned with `MeetingDetector`, fail soft
2. **Block type + column map** — `sidemeeting` → column 2; drop registration; relocate remaining `officehours` → **yellow** (`nocHelpdesk`)
3. **Overlapping layout** — room-based half-width when two side meetings overlap; full-width when alone
4. **List / detail UX** — Side badge (BoF-style); description; grayed Notes/Agenda; external Join URL
5. **Docs + tests** — update overview/notes; add Espresso cases (green chips, badge, detail, external join)
6. **Manual check** on device/emulator with live IETF 126 data before the meeting

### Build / test reminder (this workstation)

Android cmdline tools:

```text
C:\Users\yaron\Applications\android-cmdline-tools
```

Also see `BUILD_AND_TEST.md` and `REGRESSION_TEST_PLAN.md` for project-specific Gradle / Espresso flows.

---

## Decisions (resolved from review)

1. **Registration** — **Drop** from schedule/sessions (do not import).
2. **Where remaining former green items go** — **Yellow** (`nocHelpdesk`). Blue stays food/breaks/social.
3. **Half-width policy** — Full-width when alone; half-width when two overlap.
4. **Meeting-number mismatch** — Skip import if `_data` ≠ detected meeting.
5. **Schema** — Session-id prefix `side-{id}` for v1 (optional explicit flag later if useful for the badge).
6. **Sessions list labeling** — **BoF-style badge** (“Side” / “SIDE”), not a title prefix.
7. **Informal API resilience** — Short timeout, no wait on failure, agenda sync unaffected.
8. **Meeting association** — Same `MeetingDetector` previous/next/ongoing logic as agenda.
9. **Notes / Agenda tabs** — Grayed out (not hidden).
10. **Join** — External URL only.

---

## Out of scope (for this pass)

- Editing / requesting side meetings in-app
- Calendar `.ics` export from the app (site already offers ICS)
- Fifth schedule column
- Offline-first redesign beyond normal sync caching
- Showing historical side meetings for past IETF numbers (API is current-meeting-oriented)

---

## Success criteria

- Green column shows side meetings for the active IETF meeting, with correct local times and rooms.
- Two overlapping side meetings are both visible/tappable (half-width by room); single ones use full green width.
- Side meetings appear in Sessions + search with a Side badge; can be starred.
- Detail shows description; Notes/Agenda grayed; Join opens the remote URL outside the app.
- Registration does **not** appear in schedule or sessions.
- Remaining former green agenda items still appear (in **yellow**), not dropped.
- Sync remains resilient if `_data` is empty, unreachable, slow, or for another meeting number — without delaying agenda sync.

---

## Doc index update

After implementation, also update:

- `OVERVIEW.md` — feature list + API endpoints
- `IMPLEMENTATION_NOTES.md` — technical section
- `REGRESSION_TEST_PLAN.md` — new Espresso scenarios

(`OVERVIEW.md` still links to `CHANGES_LOG.md`, which is missing; optional cleanup when touching docs.)
