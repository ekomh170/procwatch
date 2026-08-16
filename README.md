# ProcWatch

Background app monitor and manager for Android. Built to be sideloaded onto one phone — yours — not published.

Target device: **Poco X6 (HyperOS / Android 14)**. `minSdk 29`, `targetSdk 35`.

---

## What it actually does

Android does not let a normal app see other apps' processes. `getRunningAppProcesses()`, `getRunningServices()` and `/proc` were all closed off years ago. So ProcWatch is built in tiers, and it tells you which tier you are on instead of pretending.

|                            | No permissions | + Usage Access | + Shizuku |
| -------------------------- | :------------: | :------------: | :-------: |
| Installed app list         |       ✅       |       ✅       |    ✅     |
| Screen time, last used     |       ❌       |       ✅       |    ✅     |
| Standby bucket             |       ❌       |       ✅       |    ✅     |
| Storage breakdown          |       ❌       |       ✅       |    ✅     |
| **Live process list**      |       ❌       |       ❌       |    ✅     |
| **Per-app memory (PSS)**   |       ❌       |       ❌       |    ✅     |
| **Real force stop**        |       ❌       |       ❌       |    ✅     |
| Freeze / unfreeze app      |       ❌       |       ❌       |    ✅     |
| Block background execution |       ❌       |       ❌       |    ✅     |

Without Shizuku the only kill available is `killBackgroundProcesses()`, which reclaims cached processes and nothing else — the app comes straight back. The UI calls that controller "Soft kill" rather than dressing it up as a force stop.

**No network.** `android.permission.INTERNET` is deliberately absent from the manifest. Nothing this app reads can leave the phone.

---

## Build

Needs **JDK 17** and the **Android 15 (API 35)** platform. Gradle 8.9 does not run on Java 23 or newer, so a very recent JDK on `PATH` will fail the build with an unhelpful message about class file versions — Android Studio sidesteps this by using its own bundled JDK.

The Gradle wrapper is checked in, so no separate Gradle install is needed — `gradlew` fetches its own. Open the folder in Android Studio (Ladybug or newer) and let it sync, which writes the `local.properties` that is not checked in. After that:

```bash
./gradlew test            # parser tests
./gradlew assembleDebug   # app/build/outputs/apk/debug/
./gradlew installDebug
```

**No Android Studio?** `.\tools\setup-build-env.ps1` downloads JDK 17, the SDK command-line tools and Gradle into one folder under `%LOCALAPPDATA%`, wires them up and builds. Nothing system-wide, no administrator rights. See [docs/BUILD-CLI.md](docs/BUILD-CLI.md) for that route and the manual equivalent.

### Signing a release build

Debug builds are signed automatically with Gradle's debug key and install as `com.procwatch.debug`. A release build needs your own key, or `assembleRelease` produces an unsigned APK that Android refuses to install.

```bash
keytool -genkeypair -v -keystore procwatch-release.jks \
  -alias procwatch -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties   # then fill it in
./gradlew assembleRelease
```

`keystore.properties` and `*.jks` are gitignored. Back both up: Android identifies an app by its signature, so losing the key means you can no longer update an installed ProcWatch in place — you would have to uninstall first, discarding the keep-running list and action log.

Debug and release are **different packages**, so they do not share Shizuku authorisation or app data. Pick one for daily use rather than switching back and forth.

R8 is deliberately off in release: the privileged paths reach `Shizuku.newProcess` and `getAppStandbyBucket` by reflection, which static analysis cannot follow, and a personal sideload gains nothing from a smaller APK worth that risk.

---

## Setup on the phone

**1. Usage Access** — Setup tab → Open Settings → find ProcWatch → allow. This one is worth doing even if you skip Shizuku.

**2. Shizuku** — install it, then:

- Settings → About phone → tap Build number 7 times
- Developer options → enable **USB debugging** _and_ **USB debugging (Security settings)** (Xiaomi splits these)
- Developer options → enable **Wireless debugging**
- Open Shizuku → Start via Wireless debugging → pair with the code
- Back in ProcWatch: Setup tab → Authorise ProcWatch

Shizuku's service dies on every reboot unless the phone is rooted. ProcWatch detects this and drops to the lower tier instead of breaking; the Setup tab tells you what to do.

If pairing fails on HyperOS, check Settings → Security → **Secure app spawning** and turn it off.

---

## Layout

```
core/         Models and formatting. No Android dependencies beyond the SDK.
privileged/   AppController interface + Shizuku and fallback implementations.
              CapabilityRouter picks the strongest one that is alive right now.
data/         Data sources (packages, usage, system stats), whitelist, action log,
              and the repository that joins them.
ui/           Compose screens. One MainViewModel backs all three tabs.
docs/         KONSEP.md is the design document this was built from (Indonesian).
              CHANGELOG.md records what changed since, and why.
              BUILD-CLI.md covers building without the IDE.
tools/        setup-build-env.ps1 fetches the whole toolchain into one folder.
```

The important seam is `privileged/AppController.kt`. Nothing outside that package knows Shizuku exists. Adding a root backend later means writing one more implementation and adding it to the list in `AppContainer` — nothing else changes.

`ShizukuShell` reaches `Shizuku.newProcess` by reflection because the client library marks it `@RestrictTo`. If a future Shizuku release renames it, that one file is the only thing to fix.

---

## Deliberate simplifications

These are choices, not oversights — each one is a place to grow into.

- **No Hilt.** One object graph, no variants. `AppContainer` is 30 lines instead of an annotation processor.
- **No Room.** The persistent state is a whitelist and a capped action log. SharedPreferences plus JSON, no KSP in the build.
- **One ViewModel for three tabs.** They read the same repository and the same privilege state. The tab boundary is the seam to cut along if this grows.
- **`dumpsys meminfo`, not `ps`.** PSS reflects an app's actual memory cost; RSS double-counts shared pages across every process that maps them.

## Not built yet

- Automatic sweeps on screen off (needs a foreground service with `specialUse` type and an OEM battery-exemption dance)
- Usage history and charts (needs periodic snapshots via WorkManager and somewhere to put them — that is where Room earns its place)
- Rules engine, widget, quick settings tile
- Per-app network usage via `NetworkStatsManager`

## Known rough edges

- `dumpsys` output format is not a stable API. `ShizukuParserTest` pins the current shape; when an OS update breaks it, capture real output with `adb shell dumpsys meminfo` and fix the regex against it.
- HyperOS runs its own aggressive background management and may kill ProcWatch itself. Set its battery restriction to "No restrictions" and enable Autostart in the Security app.
- Force-stopped apps send no notifications until opened by hand. The keep-running list is seeded on first launch with your dialer, SMS app, keyboard, launcher and clock — check it before running a sweep.
