# Obsidian Agent Mobile

Native Android remote client for the Obsidian Agent Client desktop plugin.

OAM connects directly to the bridge hosted by OAC inside Obsidian Desktop. It does not scrape an IDE, run a Node server, expose a public tunnel, or contain provider-specific logic.

## Features

- One-time six-digit device pairing with a per-device token
- Tailscale-only laptop connection
- Live SSE event stream with reconnect support
- Conversation list and switching
- Send, steer through the parent, stop, approve, and reject
- Provider-neutral tool and sub-agent activity, including reported timelines and final output
- Laptop battery percentage, charging state, AC state, timestamp freshness, and unavailable degradation
- Android notifications for approvals, completion, and errors
- Desktop-provided Obsidian theme token transport with a neutral local fallback

Direct sub-agent steering is shown only when an upstream provider exposes it. Current OAC transports steer delegated work through the parent session.

## Build and install

Requirements: JDK 17 and Android SDK platform 36 with build tools 35.

```powershell
cd C:\XyourP\AntigravityMobile\android
.\gradlew.bat assembleDebug
```

Installable APK:

`android/app/build/outputs/apk/debug/app-debug.apk`

The debug APK is signed with the Android debug key and can be sideloaded directly. A durable production update channel requires a separately managed release keystore.

## Connect

1. Open OAC settings in Obsidian Desktop.
2. Enable the private mobile bridge. OAC refuses a non-Tailscale bind.
3. Note the displayed `100.x.x.x` address, port, and six-digit pairing code.
4. Open OAM, enter the address and port, test the bridge, then pair the phone.

Obsidian Desktop and OAC must remain running while the phone is connected.

## Architecture

The accepted decisions are maintained in the vault:

- `Machine/Products/obsidian-agent-client/docs/ADR/0006-remote-access-and-the-mobile-client.md`
- `Machine/Products/obsidian-agent-client/docs/ADR/0007-remote-trust-boundary-and-device-pairing.md`

The former CDP scraper, browser dashboard, Git/file/quota screens, public tunnel, Telegram integration, and Node bridge were removed during the OAM conversion. The unrelated Rust desktop-control helper was extracted to `C:\XyourP\desktop-helper`.
