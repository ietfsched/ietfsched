# Side Meetings — Design (as built)

Status: **Implemented** (IETF 126)  
Branch: `ys-side-meetings`  
Data: [https://sidemeetings.ietf.org/](https://sidemeetings.ietf.org/) · informal API [https://sidemeetings.ietf.org/_data](https://sidemeetings.ietf.org/_data)

Local tooling (this workstation):

```text
C:\Users\yaron\Applications\android-cmdline-tools
C:\Users\yaron\Applications\Android\Sdk
Microsoft OpenJDK 17 (JAVA_HOME)
```

Emulator is **not** available on Windows ARM64; use a physical device (Pixel 8a) + optional scrcpy.

---

## Goal

Show IETF **side meetings** in **Sessions** and on the **Schedule** grid by **repurposing the green column**.

---

## Coverage checklist (plan → reality)

| Plan item | Status | Notes |
|-----------|--------|-------|
| Soft-fetch `/_data` with short timeout | Done | 4s connect / 6s read; agenda sync continues on failure |
| Meeting-number guard vs `MeetingDetector` | Done | Skip import if `_data` ≠ active meeting |
| Same `UPDATED` purge stamp as agenda | Done | `SideMeetingImporter` ops merged into `LocalExecutor` batch |
| Preserve stars | Done | Same `querySessionStarred` pattern |
| `side-{id}` session/block ids | Done | Prefix detection; no schema migration |
| Block type `sidemeeting` (+ room digit) | Done | `sidemeeting0` / `sidemeeting1` |
| Green column = side meetings only | Done | |
| Drop registration | Done | Skipped in `LocalExecutor.transform` |
| Former green → yellow | Done | Remapped to `nocHelpdesk` |
| Half-width when two overlap | Done | Full width when alone |
| Side badge (BoF-style) | Done | List + detail; green `#009939` |
| Join = external URL (not Meetecho) | Done | `ACTION_VIEW` on `SESSION_URL` |
| Informal API never blocks agenda | Done | Soft-fail smoke-tested on Pixel 8a (see below) |
| Schedule tap → session list | **Changed** | Direct to **session detail** (1:1) |
| Description in Content; Notes+Agenda grayed | **Changed** | Description in **Agenda**; **Content + Notes** grayed |
| Docs (`OVERVIEW`, `IMPLEMENTATION_NOTES`) | **Not done** | Still pending |
| Espresso regression cases | **Skipped** | Informal API; revisit when official |

---

## As-built behavior

### Schedule columns

| Col | Color | Content |
|-----|-------|---------|
| 0 Blue | food / breaks / plenary / social | Unchanged |
| 1 Red | WG sessions / hackathon | Unchanged |
| 2 Green | **Side meetings** (`sidemeeting` / `sidemeeting0` / `sidemeeting1`) | New |
| 3 Yellow | NOC / helpdesk / AD OH / IEPG + former green leftovers (staff OH, education/newcomer/tools, hackathon results, …) | Registration omitted |

### Sync

1. After agenda JSON fetch, soft-GET `https://sidemeetings.ietf.org/_data`.
2. `SideMeetingImporter.buildOperations(...)` only if meeting numbers match.
3. Ops applied in the **same** batch / purge as agenda (`LocalExecutor.execute(agenda, number, sideData)`).

**Key files:** `SyncService.java`, `RemoteExecutor.java` (timeouts), `SideMeetingImporter.java`, `LocalExecutor.java`.

### Data mapping

| API field | App field |
|-----------|-----------|
| `id` | Session + block id `side-{id}` (sanitized) |
| `title` | Session / block title (no `SIDE -` prefix) |
| `start` / `end` | Absolute millis via `Instant.parse` (UTC ISO) |
| `roomName` | Room |
| `description` + organizer + areas | `SESSION_ABSTRACT` (shown in **Agenda** tab) |
| `location` | `SESSION_URL` (Join external link) |
| `areas[]` | `SESSION_KEYWORDS` |

Room sub-column: rooms from `_data` sorted by title → index 0/1 → block type suffix.

### Schedule UX

- Chip title = booking title.
- Overlap → half-width by room sub-column; alone → full green width.
- **Tap green chip → session detail directly** (skips one-item list).

### Sessions list

- Appears in Sessions / search / starred.
- Green **Side** badge (like BoF).

### Session detail (side meetings only)

Existing tab order unchanged: **Agenda · Join · Content · Notes**.

| Tab | Behavior |
|-----|----------|
| Agenda | Description + organizer + areas (plain text in WebView) |
| Join | Opens `SESSION_URL` externally (`ACTION_VIEW`). Webex may itself redirect to Play Store if the app is not installed — left as-is |
| Content | Grayed |
| Notes | Grayed |

TabHost setup can fire spurious changes; Join action is gated until `mTabHostReady`.

### Registration / former green

- Registration: not imported.
- Former `officehours` content → `nocHelpdesk` (yellow).

---

## Known limitations / non-goals

- No in-app Join WebView / no blocking of Webex→Play Store redirects (explicitly declined).
- No historical side meetings for other IETF numbers (`/_data` is current-meeting-oriented).
- No ICS export, no request/edit flow.
- No Espresso coverage yet (deferred while `/_data` is informal); no updates yet to `OVERVIEW.md` / `IMPLEMENTATION_NOTES.md`.
- Windows ARM64: no Google Emulator package; device + scrcpy for UI work.

---

## Success criteria (current)

- [x] Green column shows side meetings for the active meeting
- [x] Overlapping side meetings half-width; alone full-width
- [x] Sessions list + search + Side badge + starring
- [x] Agenda shows description; Content/Notes grayed; Join opens remote URL
- [x] Schedule chip opens detail directly
- [x] Registration omitted; former green items in yellow
- [x] Soft-fail `/_data` without blocking agenda sync (device smoke tests below)
- [ ] Dev-docs follow-up (`OVERVIEW`, `IMPLEMENTATION_NOTES`)
- [ ] Espresso cases — **skipped for now** (informal `/_data` API; revisit when official)

---

## Device smoke tests (soft-fail)

Run on Pixel 8a (`adb` + scrcpy). Method: temporary code change → `assembleDebug` → `adb install -r` → `pm clear` → launch → check logcat → restore production URL/code and reinstall.

| Case | How forced | Expected log | Observed |
|------|------------|--------------|----------|
| Meeting-number mismatch | Pass `meetingNumber + 999` into `SideMeetingImporter.buildOperations` | `Skipping side meetings: data is for IETF 126 but app meeting is …` then `remote sync finished` | Pass |
| Bad / empty `/_data` | Point `SIDE_MEETINGS_URL` at a non-existent path | `Side meetings fetch returned empty data` then `remote sync finished` | Pass |
| Connect timeout | Point URL at `https://192.0.2.1/_data` (TEST-NET blackhole) | `Side meetings fetch failed (continuing without them): … after 4000ms` then `remote sync finished` | Pass (~4s connect timeout) |
| Happy path (restore) | Real `https://sidemeetings.ietf.org/_data` | `Prepared N ops for M side bookings` then `remote sync finished` | Pass (`117` ops / `39` bookings for IETF 126) |

Also verified earlier by hand on device: green column / half-width overlap, Side badge, detail tabs (Agenda text, Content+Notes grayed, Join external), schedule chip → detail directly.

**Notes**

- Soft-fail paths must not prevent `remote sync finished` or leave the UI hung.
- After any forced-URL test, restore `SIDE_MEETINGS_URL` and reinstall before leaving the phone.
- Cache TTL can skip sync on relaunch; use `pm clear` (or bump `VERSION_CURRENT`) when forcing a fresh fetch.

---

## Follow-ups

1. Update `OVERVIEW.md` and `IMPLEMENTATION_NOTES.md`.
2. Espresso for side meetings: deferred until the side-meetings API is official.
3. Optional: force a sync path when `VERSION_CURRENT` bumps so side meetings refresh without waiting for cache TTL / `pm clear`.
