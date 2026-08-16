# Building without Android Studio

ProcWatch builds fine from the command line. The IDE is convenient, not required — what is
required is a JDK, the Android SDK and Gradle, and all three can live in one throwaway folder.

---

## The short way

```powershell
cd C:\Users\<you>\Desktop\apps-procwatch
.\tools\setup-build-env.ps1
```

That downloads JDK 17, the Android SDK command-line tools and Gradle 8.9 into
`%LOCALAPPDATA%\procwatch-build`, installs the SDK packages, accepts the licences, generates the
Gradle wrapper, writes `local.properties`, and builds a debug APK.

Nothing goes system-wide and nothing needs administrator rights. Re-running it is safe: anything
already downloaded is skipped. To undo everything, delete `%LOCALAPPDATA%\procwatch-build` and
remove the `JAVA_HOME` and `ANDROID_HOME` user environment variables.

Expect the first run to take a while — roughly 700 MB of downloads, then a few minutes of
compilation.

Pass `-SkipBuild` to set the toolchain up without building.

---

## What it installs, and why each piece

| Piece                    | Why                                                                                                                                                                                                   |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **JDK 17**               | Gradle 8.9 runs on Java 8–22. A newer JDK fails with a message about class file versions that never names the real cause, so the script pins its own copy rather than trusting whatever is on `PATH`. |
| **cmdline-tools**        | Contains `sdkmanager`, which fetches everything else.                                                                                                                                                 |
| **platforms;android-35** | `compileSdk = 35`.                                                                                                                                                                                    |
| **build-tools;35.0.0**   | aapt2, d8, zipalign, apksigner.                                                                                                                                                                       |
| **platform-tools**       | `adb`, for installing over USB.                                                                                                                                                                       |
| **Gradle 8.9**           | Needed once, only to generate the wrapper. After that `gradlew` fetches its own copy and the folder can be deleted.                                                                                   |

---

## Doing it by hand

If you would rather not run a script, these are the same steps. **Each numbered item is
something to do, not a line to paste** — the downloads are manual.

1. **Install JDK 17** from [adoptium.net](https://adoptium.net/temurin/releases/?version=17).
   Note the real install path; there is no `...` in it.

2. **Download the command-line tools** from
   [developer.android.com](https://developer.android.com/studio#command-line-tools-only) and
   extract them so the layout is exactly:

   ```
   C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat
   ```

   The zip unpacks to a folder called `cmdline-tools`, which gives you
   `cmdline-tools\cmdline-tools\bin` if you extract it in place. Rename the inner folder to
   `latest`. This is the most common setup mistake and `sdkmanager` will not explain it.

3. **Point the shell at both**, using your real paths:

   ```powershell
   $env:JAVA_HOME   = "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
   $env:ANDROID_HOME = "C:\Android\sdk"
   ```

4. **Install the SDK packages:**

   ```powershell
   & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
   & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
       "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```

5. **Download Gradle 8.9** from
   [services.gradle.org](https://services.gradle.org/distributions/gradle-8.9-bin.zip), extract
   it, then from the project folder:

   ```powershell
   & "C:\gradle\gradle-8.9\bin\gradle.bat" wrapper --gradle-version 8.9
   ```

6. **Build:**

   ```powershell
   .\gradlew.bat assembleDebug
   ```

Make `JAVA_HOME` and `ANDROID_HOME` permanent through **System Properties → Environment
Variables**, or they vanish when the terminal closes.

---

## Which APK

`assembleDebug` produces `app\build\outputs\apk\debug\app-debug.apk`, signed with Gradle's debug
key and installable straight away. It arrives as `com.procwatch.debug`.

`assembleRelease` produces `com.procwatch`, not debuggable — the better choice for daily use on
an app that runs privileged shell commands. It needs your own key first:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
    -keystore procwatch-release.jks -alias procwatch `
    -keyalg RSA -keysize 4096 -validity 10000

Copy-Item keystore.properties.example keystore.properties   # then fill it in
.\gradlew.bat assembleRelease
```

Without `keystore.properties` the release build still runs but emits an unsigned APK that
Android refuses to install.

Debug and release are **different package names**, so they share neither Shizuku authorisation
nor app data. Pick one for daily use rather than alternating — the keep-running list does not
follow you across.

---

## When it goes wrong

**`'gradle' is not recognized`, `sdkmanager.bat is not recognized`** — the tool is not installed
yet, or the path is wrong. Nothing is broken; the download step simply has not happened. Check
that `sdkmanager.bat` actually exists at the path in the error.

**`Unsupported class file major version`** — Gradle is running on too new a JDK. Check with
`.\gradlew.bat -version` and read the `JVM:` line; it must say 17. If not, `JAVA_HOME` is unset
or points somewhere else.

**`SDK location not found`** — no `local.properties` and no `ANDROID_HOME`. Either write
`sdk.dir=C\:\\Android\\sdk` into `local.properties` (backslashes doubled, colon escaped) or set
the variable.

**`Failed to install the following Android SDK packages as some licences have not been
accepted`** — run `sdkmanager --licenses` and answer `y` to each.

**Kotlin compile errors** — the toolchain is fine; this is the code. Send the output to Claude.
