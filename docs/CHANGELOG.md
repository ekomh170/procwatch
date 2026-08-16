# Changelog

All notable changes to ProcWatch are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Written in English to match the
commit history and the code comments — `KONSEP.md` stays in Indonesian as the planning doc.

Short hashes point at the commit that carried the change, so an entry here can always be
traced back to its diff.

---

## [Unreleased]

First pass over the findings from a full read of the codebase. No features were added; every
entry below is a correctness or cost fix in code that already shipped in 0.1.0.

### Fixed

- **Per-app memory undercounted multi-process apps.** `dumpsys meminfo <pkg>` prints one block
  per process, so an app running a `:remote` or `:push` process has several `TOTAL PSS` figures.
  The parser took only the first, which disagreed with the app list — that sums every process
  for the package. `parseTotalPss` now sums them. (`71d0b55`)

- **PSS could double-count on some builds.** The fix above assumed exactly one `TOTAL PSS` per
  process. Some Android builds print the figure twice — once in the table footer, once under
  App Summary — which would have doubled the reading. The output is now split on the
  `** MEMINFO in pid` header and only the first figure in each block counts, so the sum is
  right whether a block reports once or twice. (`0dd927f`)

- **A refresh issued while another was running was silently discarded.** `refresh()` returned
  early whenever one was in flight, and the refresh that follows a force stop is exactly the
  one that collided — so a row could keep showing an app as running until something else
  triggered a reload, making a successful stop look like it had failed. A `Mutex` now makes the
  second caller wait its turn. (`5e93f16`)

- **`processMemory` was missing the `isAvailable()` guard** the other privileged calls already
  had. A dead Shizuku binder surfaced as a raw shell error string instead of an honest
  `UnsupportedByController`. (`71d0b55`)

- **A sweep trusted the whitelist snapshot on each row** rather than reading it live, so a
  package protected since the last refresh could still be stopped. (`e63dbcd`)

### Changed

- **Opening an app no longer runs a system-wide `dumpsys meminfo`.** The sheet used to dump the
  whole process table and discard every row but one. The refresh that populated the list already
  had that table, so it is cached and filtered instead — the sheet now opens with its process
  list already filled in, with no shell call at all. (`752ec74`)

- **`AppController.processMemory` and `Capability.PER_APP_MEMORY` are now used.** Both were
  implemented on every controller but had no caller anywhere; `PER_APP_MEMORY` was only
  inflating the capability count on the Setup screen. They are wired through `CapabilityRouter`
  and used to re-read one package's memory when its detail sheet opens, since the row figure can
  be minutes old. `dumpsys meminfo <pkg>` is far cheaper than the global dump it replaced.
  (`752ec74`)

- **The action log is written once per sweep instead of once per app.** Every force stop
  re-encoded the whole log — up to 300 entries — on the caller's thread, so a sweep of thirty
  apps paid that thirty times mid-progress-loop. `forceStop` is split so stopping and logging
  are separate concerns: the single-action path still writes immediately, while `hibernateAll`
  collects records and hands them to `recordAll` for one serialisation at the end. (`e63dbcd`)

- **The standby bucket method handle is cached.** `standbyBucket()` runs once per installed
  package on every refresh — roughly 300 times on this device — and each call did its own
  `Class.getMethod` lookup before invoking. Only the invoke has to be per-package. Failure
  behaviour is unchanged: a hidden method means a null handle and a null result, never a throw.
  (`03224e9`)

- **`bucketColor` is driven by the `Bucket` constants** instead of repeating the raw values
  10/20/30/40/45 that `core/Models.kt` already names. (`aaa2cc1`)

- **The detail sheet's loading hint** named the process table, which is no longer what is being
  waited on. (`752ec74`)

### Removed

- `app/src/main/res/values-night/themes.xml`, byte-identical to `values/themes.xml`. ProcWatch is
  dark-only by design — the palette is hardcoded in `Theme.kt` and never consults the system
  setting — so the qualifier bought nothing and only invited the two files to drift apart.
  (`6e7a7a2`)

- `PackageSource.launchIntentFor`, which had no callers. The one place that needs a launch intent
  asks `PackageManager` directly. (`6e7a7a2`)

### Verification status

**Not yet compiled or tested.** These changes were reviewed by hand on a machine with no Gradle
and no Android SDK. Before trusting them on the phone:

```bash
gradle wrapper --gradle-version 8.9
./gradlew test           # 8 parser tests, 3 of them new
./gradlew assembleDebug
```

The likeliest thing to catch: `refresh()` is now an expression body
(`= refreshLock.withLock { … }`), the only structural change to a function signature.

The `dumpsys` samples in `ShizukuParserTest` are still synthetic. Once Shizuku is connected,
`adb shell dumpsys meminfo <multi-process-pkg>` on the real device is the fastest way to confirm
the format assumptions hold on HyperOS. Capture that output and paste it in as a test case.

---

## [0.1.0] — 2026-08-16

Initial working build. Not tagged in git; the version comes from `versionName` in
`app/build.gradle.kts` and is shown on the Setup screen.

### Added

- **Tiered capability model.** `AppController` is the seam: features ask `CapabilityRouter` for a
  capability and never name a backend, so the app degrades instead of breaking when Shizuku is
  not running. `ShizukuAppController` (shell UID) and `FallbackAppController`
  (`killBackgroundProcesses`, labelled "Soft kill" because that is what it is) are the two
  implementations.
- **Dashboard** — RAM with a segmented meter, battery, storage, live access-level card naming
  which tier is active and what is missing, and the sweep with an explicit consequence warning.
- **App list** — dense `LazyColumn` with search, four filters and four sort keys, a leading rail
  encoding standby bucket, and empty states that explain the cause rather than showing a blank.
- **App detail sheet** — state, package metadata, storage breakdown, live process table, and the
  actions the active tier can actually perform.
- **Setup tab** — Usage Access and Shizuku flows branching on all five connection states,
  keep-running list management, and the action log with verbatim shell errors.
- **Keep-running list**, seeded on first launch from this device's own dialer, SMS app, launcher
  and IME rather than a hardcoded list that would be wrong on another phone. Enforced in the
  repository, not the UI, so it cannot be bypassed by a future caller.
- **Action log**, last 300 privileged actions with the exact shell error on failure — the only
  way to tell whether a force stop that appeared to do nothing actually ran.
- **Parser regression tests** for `dumpsys meminfo` and the `ps` fallback, the one thing here
  that can break on an OS update with no code change on our side.

### Notes

- No `INTERNET` permission. Nothing this app reads can leave the phone.
- Deliberate simplifications, each documented in the README with its reasoning: no Hilt, no Room,
  one ViewModel for three tabs, `dumpsys meminfo` over `ps`.
- Not built yet: automatic sweeps on screen off, usage history and charts, rules engine, widget
  and quick settings tile, per-app network usage.
