# GeckoView OAuth Lab

Debug-only activity for iterating on Meetecho Datatracker popup login without the full Schedule UI.

## Launch

```powershell
adb shell am start -n org.ietf.ietfsched/.ui.DebugOAuthActivity
```

Optional URL:

```powershell
adb shell am start -n org.ietf.ietfsched/.ui.DebugOAuthActivity --es url "https://meetings.conf.meetecho.com/onsite126/?group=gaia"
```

## Design under test

- **Main** `GeckoView` + session: Meetecho (stays attached for `window.opener`)
- **Popup** `GeckoView` + session: Datatracker OAuth (`onNewSession`)
- Both use **`BACKEND_TEXTURE_VIEW`** (same activity window → Paste toolbar + z-order)
- Dismiss only on page `window.close` / Back — **no** javascript: probe, **no** Meetecho reload

## Logs

```powershell
adb logcat -s DebugOAuth:D GeckoRuntimeHelper:D
```

## Credentials

Do **not** commit Datatracker credentials. For agent-driven tests, pass them in-chat for that session only; use `adb shell input text` / uiautomator, then clear app data afterward.
