# OAM Android application

The Android client uses Kotlin, Jetpack Compose, OkHttp SSE, kotlinx.serialization, and DataStore.

Build an installable development APK:

```powershell
.\gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The app targets Android 10 and newer. It requires `INTERNET`, network-state access, and notification permission. Cleartext HTTP is intentionally permitted because transport is restricted to the private encrypted Tailscale interface and authenticated again with a paired-device token.

The mobile data model mirrors OAC's serializable conversation and `AgentEvent` shapes. Providers remain desktop-only. Battery data is authoritative only while the SSE connection is live and the sample is no older than 90 seconds.
