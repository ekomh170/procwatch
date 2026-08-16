# Changelog

All notable changes to ProcWatch are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Written in English to match the
commit history and the code comments — `KONSEP.md` stays in Indonesian as the planning doc.

Short hashes point at the commit that carried the change, so an entry here can always be
traced back to its diff.

---

## [Unreleased]

Two review passes over 0.1.0, no new features. The first was a full read of the codebase for
correctness and cost; the second checked the UI against Material 3 and the Android
accessibility guidelines.

On that second pass, the deliberate deviations from Material 3 were left alone. Dark-only, no
dynamic colour, monospace throughout, 4dp radii and flat surfaces are a stated design position
— `Theme.kt` calls the app an instrument, not a settings screen — and conformance for its own
sake would cost the identity without making anything easier to use. What was fixed is the part
that hurts regardless of aesthetic: contrast, text size, touch targets, screen reader support —
and the one place where the phone's own light/dark setting still leaked into a dark-only app.

### Added

- **Release builds can be signed, and therefore installed.** `assembleRelease` had no
  `signingConfig`, so it produced an unsigned APK that Android refuses to install — the only
  installable output was the debug build, which is `debuggable`, a poor default for an app whose
  purpose is running privileged shell commands. Signing now reads from `keystore.properties` in
  the project root, gitignored along with `*.jks` and `*.keystore`. When that file is absent the
  config is not created and the build behaves exactly as before, so a fresh clone still builds
  debug with no setup. `keystore.properties.example` documents the keys and the `keytool`
  invocation. (`b227e76`)

- **The memory meter is announced to screen readers.** `SegmentMeter` is a bare `Canvas`, so
  TalkBack had nothing to say about the headline reading on the dashboard. It now takes an
  optional label and exposes `"<label>, N percent"`. Optional, so a caller that already prints
  the figure beside the meter does not make a screen reader read it twice. (`f371f76`)

### Fixed

- **The launcher icon was clipped by circular masks.** The old mark's bottom bar reached
  `x32,y81`, which is 34.8dp from the centre of the 108dp canvas — outside the 33dp radius every
  launcher mask preserves, so a circular mask cut its corner. Artwork also sat low, centred on
  y59.5 rather than 54, and 7dp bars thinned out at small render sizes. Replaced with the app's
  own `SegmentMeter` turned upright: three columns of three 12dp cells on a 16dp pitch, spanning
  32–76 on both axes, furthest corner 31.1dp from centre. Lit cells use `Panel.Signal` and unlit
  use `Panel.BucketUnknown`, the same pair the app uses for a running versus an idle process.
  Themed icons get their own drawable carrying only the lit cells — sharing the colour artwork
  would have flattened the reading into one solid block under the system tint. (`99ca8b7`)

- **The phone's light mode made the status bar icons disappear.** `enableEdgeToEdge()` with no
  arguments defaults to `SystemBarStyle.auto`, which reads `Configuration.UI_MODE_NIGHT_MASK` —
  the system dark mode setting, not the app's theme. ProcWatch is always dark, so launching it
  with the phone in light mode made the library assume a light background and draw _dark_ status
  bar icons over #0E1116; the clock, battery and signal were all but invisible. Because `uiMode`
  is in the activity's `configChanges`, nothing recreated the activity, so the state was sticky:
  toggling the system theme while the app was open did not help, only closing and reopening it.
  `SystemBarStyle.dark` now states the fact instead of inferring it. (`87250e1`)

- **The dimmest text failed WCAG AA contrast.** `TextFaint` #5C6673 measured 3.0:1 against
  Surface and 3.2:1 against Background, where AA asks 4.5:1 for body text — and it carries the
  app list's second line, package names, log timestamps and empty-state copy. #7A8593 measures
  4.6:1 and 5.0:1 with the same cool grey cast, so nothing about the look changes. (`9996a56`)

- **Three controls were below the 48dp minimum touch target.** "CLEAR" in the activity log was
  roughly 14dp — a plain `Text` with a clickable modifier, for an action that wipes the entire
  log, which is dangerous in both directions. "Show all apps" in the empty state was about 34dp
  and styled as a button without being one. The keep-running rows measured about 44dp. All
  three now carry a real target and, for the two that became buttons, the button role for
  screen readers. (`673a362`)

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

- **The eyebrow style was doing two jobs and was too small for one of them.** `EyebrowStyle` at
  10sp covered both short upper-case labels, where it works, and sentence-length secondary
  content, where it sat below the readable floor and below the smallest role in the M3 type
  scale. It moves to 11sp and keeps the label job. A new `MetaStyle` at 12sp takes the content
  job — app list second line, package names, process table, log entries — and drops the letter
  spacing, which helps one word and hurts a line of text. (`9996a56`, `14b8d6b`)

- **Detail sheet button labels moved to `DataStyle`.** They were 10sp eyebrow text inside an
  `OutlinedButton`, well under the M3 button label size and visibly smaller than the primary
  buttons directly above them. (`14b8d6b`)

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

- `Panel.RowHeight`, a constant with no callers — `AppListRow` sizes itself from its content.
  (`9996a56`)

### Verification status

**Compiles clean, all tests pass.** Everything above was written and reviewed on a machine with
no toolchain at all, so it went a long stretch unverified. That is now resolved:

```
./gradlew test           →  8 tests, 0 failures, 0 errors
./gradlew assembleDebug  →  BUILD SUCCESSFUL, no warnings
```

Nothing in the batch failed to compile — not `refresh()` as an expression body over
`Mutex.withLock`, not the new `SystemBarStyle` or `MetaStyle` imports, not the signing config.
The three new parser cases all pass, including the multi-process PSS sum and the
repeated-summary guard.

One warning did surface and was fixed: `Icons.Filled.List` is deprecated in favour of the
auto-mirrored variant, which matters because the manifest sets `supportsRtl="true"`.

**Still unverified: how any of it looks and behaves on the phone.** A clean compile says nothing
about the visual result. Specifically outstanding —

- Layout consequences of 12sp `MetaStyle` and the 48dp touch-target floors. Rows are taller than
  they were; that has not been seen on a real screen.
- The status bar icon fix, which only shows itself when the phone is in light mode.
- The launcher icon under HyperOS's actual mask and anti-aliasing. The published preview renders
  the same path data as SVG, so the geometry is right, but it is not a screenshot from Android.
- Contrast figures were computed from the WCAG relative-luminance formula rather than sampled
  from a screenshot. They hold for text on `Surface` and `Background`; disabled controls draw
  `TextFaint` on `SurfaceRaised` at 4.1:1, exempt from the requirement but still better than the
  2.6:1 it was.

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
