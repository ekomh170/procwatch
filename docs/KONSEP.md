# Konsep Lengkap: Android Background App Manager
### Codename: **ProcWatch** (ganti sesuka lo)
Target device: **Poco X6 (HyperOS / Android 14)** · Distribusi: **sideload pribadi, bukan Play Store**

---

## 0. Ringkasan Eksekutif

Aplikasi Android buatan sendiri untuk **memonitor dan mengendalikan aplikasi yang berjalan di background** — gabungan antara Task Manager PC (visibilitas real-time: RAM, proses, aktivitas) dan Greenify (aksi: hibernate/force-stop app rakus).

**Prinsip desain:**
1. **Transparansi > otomatisasi buta.** Tampilkan datanya dulu, aksi belakangan. User (lo sendiri) yang memutuskan.
2. **Berjenjang (tiered).** App tetap berguna tanpa izin apa pun, dan makin kuat kalau izin ditambah.
3. **Reversible.** Semua aksi bisa di-undo dan tercatat di log.
4. **Zero telemetry.** Karena alasan lo bikin sendiri adalah keamanan — nggak ada network call sama sekali di MVP. Manifest bahkan nggak perlu `INTERNET`.

---

## 1. Realita Teknis Android (BACA INI DULUAN)

Ini bagian paling penting. Banyak tutorial "bikin task manager Android" di internet sudah **usang 8+ tahun**. Android modern sengaja menutup API untuk melihat proses aplikasi lain. Konsep lo harus dibangun di atas kenyataan ini, bukan di atas API yang sudah mati.

### 1.1 API yang SUDAH MATI (jangan dipakai)

| API | Status sekarang |
|---|---|
| `ActivityManager.getRunningAppProcesses()` | Cuma balikin proses **aplikasi lo sendiri** (sejak Android 5.1–8) |
| `ActivityManager.getRunningServices()` | Deprecated API 26, cuma service milik sendiri |
| `ActivityManager.getRunningTasks()` | Cuma task milik sendiri sejak Android 5.0 |
| Baca `/proc/<pid>/` app lain | Diblokir sejak Android 7 (`hidepid=2` di mount /proc) |
| `getRecentTasks()` | Diblokir sejak Android 5.0 |

**Konsekuensi:** kalau lo cuma pakai API publik biasa, aplikasi lo **tidak bisa** melihat daftar proses yang benar-benar sedang berjalan. Titik. Semua "RAM cleaner" di Play Store yang ngaku bisa, sebenarnya cuma nampilin daftar app terinstall + tebak-tebakan.

### 1.2 API yang MASIH HIDUP (fondasi MVP)

| API | Izin yang dibutuhkan | Yang lo dapat |
|---|---|---|
| `PackageManager.getInstalledPackages()` | `QUERY_ALL_PACKAGES` (manifest) | Daftar semua app terinstall, flag system/user, versi, install time |
| `UsageStatsManager.queryUsageStats()` | **Usage Access** (special permission) | Total foreground time per app per interval |
| `UsageStatsManager.queryEvents()` | Usage Access | Event stream: MOVE_TO_FOREGROUND / BACKGROUND, screen on/off, notification |
| `UsageStatsManager.getAppStandbyBucket()` | Usage Access | Bucket: ACTIVE / WORKING_SET / FREQUENT / RARE / RESTRICTED — **proksi terbaik untuk "app ini lagi aktif atau nggak"** |
| `StorageStatsManager.queryStatsForPackage()` | Usage Access | Ukuran APK, data, cache per app |
| `NetworkStatsManager.queryDetailsForUid()` | Usage Access | Pemakaian data per app (wifi/mobile, background vs foreground) |
| `ActivityManager.MemoryInfo` | — | RAM total, available, threshold, lowMemory (device-level, bukan per app) |
| `ActivityManager.killBackgroundProcesses()` | `KILL_BACKGROUND_PROCESSES` | Bunuh proses **cached** app lain — tapi lemah (lihat 1.4) |
| `PowerManager.isIgnoringBatteryOptimizations()` | — | Cek app mana yang di-whitelist dari Doze |

**Kesimpulan Tier A:** tanpa izin istimewa, lo bisa bikin dashboard *usage & behavior analytics* yang bagus banget — tapi belum bisa "task manager real-time" dan belum bisa aksi.

### 1.3 Cara Naik Level: 3 Jalur Akses Istimewa

#### Jalur B — Accessibility Service (cara Greenify no-root)
App bikin `AccessibilityService`, lalu **secara otomatis membuka Settings → App Info → klik tombol "Force stop" → klik "OK"**. Robotik, tapi berhasil dan tanpa root.

- ✅ Nggak butuh PC, nggak butuh root
- ❌ Rapuh: label tombol beda per bahasa & per OEM. Di HyperOS layout App Info berbeda dari AOSP
- ❌ Layar berkedip-kedip saat proses (Greenify juga gitu)
- ❌ Lambat (~1–2 detik per app)
- ⚠️ Solusi anti-rapuh: jangan cari teks "Force stop", tapi cari `viewIdResourceName` (mis. `com.android.settings:id/right_button`) + fallback ke pencarian teks multi-bahasa

#### Jalur C — Shizuku ⭐ **REKOMENDASI UTAMA**
[Shizuku](https://shizuku.rikka.app/) menjalankan sebuah service dengan **UID shell (2000)** — sama persis dengan level ADB. Aplikasi lo lalu memanggil hidden API lewat service itu.

Cara start di Poco X6 tanpa PC: **Wireless Debugging** (Android 11+) → pairing di device → Shizuku jalan. Perlu diulang tiap reboot.

Yang jadi bisa dilakukan:

| Kemampuan | Cara |
|---|---|
| **Lihat proses yang BENAR-BENAR berjalan** | `dumpsys activity processes` → parsing oom_adj, proses, service aktif |
| **RAM per proses (PSS/RSS asli)** | `dumpsys meminfo` |
| **Force stop beneran** | `IActivityManager.forceStopPackage()` / `am force-stop <pkg>` |
| **Freeze/unfreeze app** | `pm disable-user --user 0 <pkg>` / `pm enable <pkg>` |
| **Suspend app** (lebih halus dari disable) | `pm suspend <pkg>` (cek ketersediaan di HyperOS) |
| **Cabut hak background** | `appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` |
| **Statistik baterai per app** | `dumpsys batterystats` |
| **Cabut dari whitelist Doze** | `cmd deviceidle whitelist -<pkg>` |
| **Lihat wakelock** | `dumpsys power` |

- ✅ Hampir sekuat root untuk use case ini
- ✅ Aman: nggak ubah partisi sistem, nggak batalin garansi, nggak trip Play Integrity
- ❌ Harus di-start ulang tiap reboot (kecuali rooted)
- ❌ Butuh library tambahan: `dev.rikka.shizuku:api` + `HiddenApiBypass`

#### Jalur D — Root
Paling kuat (baca `/proc` semua, `cgroup` freezer, `SIGSTOP` per proses), tapi di Poco X6 dengan HyperOS: **unlock bootloader Xiaomi sekarang ribet** (kuota unlock, verifikasi akun, masa tunggu). Dan kalau alasan lo bikin sendiri itu *keamanan*, unlock bootloader justru menurunkan postur keamanan device (Verified Boot mati, data at-rest lebih rentan).

> **Rekomendasi gue: Tier A sebagai fondasi + Shizuku sebagai power mode + Accessibility sebagai fallback. Skip root.**

### 1.4 Kenapa "kill background process" itu Menipu

`killBackgroundProcesses()` hanya membunuh proses yang statusnya *cached*. Sistem akan **menghidupkan ulang app itu dalam hitungan detik** kalau ada JobScheduler/Alarm/BroadcastReceiver yang terdaftar. Alarm dan job-nya tidak ikut dibatalkan.

`force-stop` (via Shizuku/Accessibility) berbeda: app masuk ke **stopped state** — semua alarm dibatalkan, job dibatalkan, dan app **tidak akan berjalan lagi sampai user membukanya secara manual** atau ada explicit intent. Ini yang bikin Greenify efektif.

**Implikasi UX penting:** app yang di-force-stop **tidak akan mengirim notifikasi**. Jadi WhatsApp, alarm clock, dan email harus masuk whitelist default.

---

## 2. Matriks Kemampuan per Tier

| Fitur | A: Tanpa izin | A+: Usage Access | B: Accessibility | C: Shizuku |
|---|:---:|:---:|:---:|:---:|
| List app terinstall | ✅ | ✅ | ✅ | ✅ |
| Ukuran storage/cache | ❌ | ✅ | ✅ | ✅ |
| Waktu pakai & histori | ❌ | ✅ | ✅ | ✅ |
| Standby bucket | ❌ | ✅ | ✅ | ✅ |
| Pemakaian data per app | ❌ | ✅ | ✅ | ✅ |
| **Daftar proses real-time** | ❌ | ❌ | ❌ | ✅ |
| **RAM per app (PSS asli)** | ❌ | ❌ | ❌ | ✅ |
| Service aktif per app | ❌ | ❌ | ❌ | ✅ |
| Wakelock | ❌ | ❌ | ❌ | ✅ |
| Force stop | ❌ | ❌ | ✅ (lambat) | ✅ (instan) |
| Freeze/disable app | ❌ | ❌ | ❌ | ✅ |
| Cabut izin background | ❌ | ❌ | ❌ | ✅ |

---

## 3. Ruang Lingkup Fitur

### 3.1 MVP (v0.1) — "Lihat dulu"

**F1. Dashboard**
- RAM: total / terpakai / tersedia + gauge, status `lowMemory`
- Baterai: level, suhu, status charging, health (`BatteryManager` + `ACTION_BATTERY_CHANGED`)
- Storage: total/free
- Ringkasan: jumlah app user, jumlah app di bucket RARE/RESTRICTED, top 3 app paling boros hari ini
- Kartu status izin: tier mana yang aktif sekarang

**F2. Daftar Aplikasi** (layar inti)
Tiap baris: ikon · label · package name · badge status.
Kolom data (adaptif tier):
- Terakhir dipakai (relative time: "3 jam lalu")
- Foreground time hari ini
- Standby bucket (chip berwarna: ACTIVE hijau → RESTRICTED merah)
- RAM (kalau Shizuku aktif)
- Jumlah proses & service aktif (kalau Shizuku aktif)

**F3. Sort & Filter**
- Sort: RAM ↓, foreground time ↓, terakhir dipakai ↑, nama A-Z, ukuran ↓
- Filter: user app / system app / semua · sedang berjalan / idle · di whitelist / tidak
- Search bar dengan fuzzy match ke label + package name

**F4. Detail Aplikasi** (bottom sheet / halaman)
- Header: ikon, label, package, versi, install & update time
- Metrik: storage breakdown (apk/data/cache), data usage 30 hari, grafik pemakaian 7 hari
- Daftar izin berbahaya yang di-grant
- Status battery optimization (whitelisted dari Doze atau nggak)
- Kalau Shizuku: daftar proses (pid, nama, PSS, oom_adj) + service yang jalan
- Tombol aksi: Force Stop · Buka App Info · Buka App Store page · Tambah ke whitelist

**F5. Aksi Dasar**
- Force Stop (routing otomatis: Shizuku → Accessibility → fallback ke buka App Info manual)
- Konfirmasi dialog dengan peringatan "app ini nggak akan kirim notifikasi sampai dibuka lagi"
- Semua aksi masuk ActionLog

**F6. Onboarding & Permission Wizard**
Wizard bertahap yang menjelaskan tiap izin: apa gunanya, apa yang lo dapat, dan tombol "Lewati" yang jelas. Bukan permission-wall.

### 3.2 v0.2 — "Greenify Mode"

**F7. Hibernation Set**
Daftar app yang lo tandai untuk dibekukan. Satu tombol "Hibernate All" → force-stop berurutan dengan progress indicator.

**F8. Auto-Hibernate**
Trigger: `ACTION_SCREEN_OFF` + delay (default 5 menit, konfigurable). Dijalankan oleh foreground service ringan.
Guard rails: skip kalau sedang charging (opsional), skip kalau app sedang di foreground, skip app whitelist.

**F9. Whitelist & Blacklist**
- Whitelist default yang pre-seeded: dialer, SMS, alarm/clock, messaging, launcher, IME, app dengan izin `POST_NOTIFICATIONS` yang aktif dipakai
- Deteksi otomatis + warning kalau lo mau force-stop app kategori kritikal

**F10. Riwayat & Grafik**
Snapshot periodik (WorkManager, tiap 15–30 menit) → Room → grafik 7/30 hari: RAM trend, top consumer, jumlah force-stop.

**F11. Log Aksi**
Timestamp · package · aksi · metode (Shizuku/Accessibility) · hasil (sukses/gagal + error). Bisa di-clear, bisa di-export.

### 3.3 v0.3+ — "Power User"

**F12. Rules Engine**
`IF <kondisi> THEN <aksi>`
- Kondisi: waktu (jam/hari), screen off > N menit, baterai < N%, app tidak dipakai > N hari, bucket = RARE, sedang di WiFi tertentu
- Aksi: force stop, freeze, cabut background, notifikasi saja

**F13. Deteksi App Nakal (heuristik)**
Skoring: `(waktu background tinggi) + (foreground time rendah) + (data background tinggi) + (wakelock sering) + (bucket masih ACTIVE padahal jarang dipakai)` → daftar "Kandidat Hibernasi" dengan penjelasan alasannya.

**F14. Freeze / Disable App** (Shizuku only)
Alternatif lebih permanen dari force-stop: app di-disable dan hilang dari launcher sampai di-enable lagi. Cocok buat bloatware Xiaomi.

**F15. Quick Settings Tile + Widget**
Tile "Hibernate Now". Widget home screen: RAM meter + jumlah app aktif.

**F16. Export/Import**
Config (whitelist, rules) → JSON. Data historis → CSV.

### 3.4 Non-Goals (yang sengaja TIDAK dibuat)
- ❌ "RAM Booster" one-tap yang bunuh semua app — kontraproduktif, Android sudah mengelola RAM lebih baik dari heuristik manual. Cached ≠ boros.
- ❌ Cloud sync / akun
- ❌ Iklan / analytics
- ❌ Rilis ke Play Store (kebijakan Accessibility & `QUERY_ALL_PACKAGES` bikin ini mustahil disetujui)

---

## 4. Arsitektur Teknis

### 4.1 Stack

| Layer | Pilihan |
|---|---|
| Bahasa | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 (dynamic color, cocok sama HyperOS) |
| Arsitektur | MVVM + UDF (unidirectional data flow), StateFlow |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore (Proto atau Preferences) |
| Background | WorkManager (periodic) + ForegroundService (trigger real-time) |
| Async | Coroutines + Flow |
| Chart | Vico (Compose-native) |
| Privileged | `dev.rikka.shizuku:api`, `dev.rikka.shizuku:provider`, `org.lsposed.hiddenapibypass:hiddenapibypass` |
| Logging | Timber (debug only) |

**SDK:** `minSdk = 29`, `compileSdk = 35`, `targetSdk = 34`
(minSdk 29 karena device lo Android 14 — nggak perlu bawa beban kompatibilitas lama. Naikkan ke 31 kalau mau `getAppStandbyBucket` + `BatteryStatsManager` tanpa guard.)

### 4.2 Struktur Modul

```
:app                    → entry point, navigation, DI wiring
:core:model             → data class murni (AppInfo, ProcessInfo, UsageSnapshot…)
:core:common            → util, Result wrapper, dispatchers
:core:database          → Room (entity, dao, migration)
:core:datastore         → settings & whitelist
:core:data              → repository, mapper, orchestration
:core:ui                → design system, komponen Compose reusable
:domain:privileged      → INTERFACE AppController + capability detection  ← kunci
:privileged:shizuku     → implementasi Shizuku
:privileged:a11y        → implementasi Accessibility Service
:privileged:none        → implementasi no-op (fallback)
:feature:dashboard
:feature:applist
:feature:appdetail
:feature:hibernate
:feature:rules
:feature:log
:feature:settings
```

### 4.3 Abstraksi Kunci — Strategy Pattern

Ini jantung arsitekturnya. Semua fitur bicara ke **interface**, bukan ke Shizuku/Accessibility langsung. Jadi kalau salah satu jalur mati (Shizuku belum di-start, Accessibility dimatikan MIUI), aplikasi tetap jalan dengan graceful degradation.

```kotlin
enum class Capability {
    LIST_INSTALLED, USAGE_STATS, STORAGE_STATS, NETWORK_STATS,
    LIST_PROCESSES, PER_APP_MEMORY, WAKELOCKS,
    FORCE_STOP, FREEZE_APP, REVOKE_BACKGROUND
}

interface AppController {
    val name: String
    suspend fun capabilities(): Set<Capability>
    suspend fun isAvailable(): Boolean

    suspend fun listProcesses(): Result<List<ProcessInfo>>
    suspend fun memoryOf(pkg: String): Result<MemoryInfo>
    suspend fun forceStop(pkg: String): Result<Unit>
    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit>
    suspend fun revokeBackground(pkg: String): Result<Unit>
}

// Router: pilih controller terbaik yang tersedia untuk sebuah kemampuan
class CapabilityRouter @Inject constructor(
    private val controllers: List<AppController>  // urut prioritas: Shizuku > A11y > None
) {
    suspend fun best(for: Capability): AppController? =
        controllers.firstOrNull { it.isAvailable() && for in it.capabilities() }
}
```

Keuntungan: unit test gampang (inject `FakeAppController`), dan nanti kalau lo mau tambah jalur root, tinggal bikin modul `:privileged:root` tanpa nyentuh feature module.

### 4.4 Aliran Data

```
┌─ PackageManager ─────┐
├─ UsageStatsManager ──┤
├─ StorageStatsManager ┼─→ Repository ─→ Room ─→ Flow ─→ ViewModel ─→ Compose
├─ NetworkStatsManager ┤       ↑
└─ AppController ──────┘       │
   (Shizuku shell)      WorkManager (snapshot periodik)
```

Repository menggabungkan data statis (PackageManager, di-cache lama) dengan data dinamis (usage/proses, polling saat layar aktif). **Jangan polling terus-menerus** — hentikan saat app di background, pakai `Lifecycle.repeatOnLifecycle(STARTED)`.

### 4.5 Skema Database (Room)

```kotlin
@Entity data class AppEntity(
    @PrimaryKey val packageName: String,
    val label: String, val isSystem: Boolean, val uid: Int,
    val versionName: String?, val installTime: Long, val updateTime: Long,
    val iconHash: String?   // ikon disimpan di file cache, bukan di DB
)

@Entity data class UsageSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val timestamp: Long,
    val foregroundMs: Long, val lastUsed: Long, val standbyBucket: Int,
    val pssKb: Long?, val processCount: Int?, val serviceCount: Int?,
    val rxBytes: Long?, val txBytes: Long?
)   // index composite: (packageName, timestamp)

@Entity data class WhitelistEntry(
    @PrimaryKey val packageName: String,
    val reason: String,        // "kritikal-sistem" | "manual" | "auto-deteksi"
    val addedAt: Long
)

@Entity data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val enabled: Boolean,
    val triggerJson: String, val actionJson: String,
    val targetsJson: String    // list package atau "ALL_EXCEPT_WHITELIST"
)

@Entity data class ActionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val action: String, val method: String,
    val success: Boolean, val errorMessage: String?, val timestamp: Long
)
```

**Retention:** hapus `UsageSnapshot` > 30 hari lewat WorkManager harian, biar DB nggak membengkak.

### 4.6 AndroidManifest — Izin

```xml
<!-- Normal / manifest -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission" />
<uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Special access: harus lewat Settings, bukan runtime dialog -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />

<!-- Shizuku -->
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />

<!-- SENGAJA TIDAK ADA: android.permission.INTERNET -->
```

**Cara minta Usage Access:**
`startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))` — nggak bisa lewat dialog runtime. Cek statusnya via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, packageName)`.

**Foreground service (API 34):**
```xml
<service android:name=".service.HibernateService"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Monitoring and managing background app activity on user request" />
</service>
```

---

## 5. Blueprint UI/UX

### 5.1 Peta Navigasi

```
Onboarding (sekali)
   └─ Welcome → Penjelasan Tier → Grant Usage Access → Setup Shizuku (opsional)
        → Setup Accessibility (opsional) → Pilih whitelist awal → Selesai

Bottom Navigation (4 tab):
├─ 🏠 Dashboard      → kartu RAM/baterai/storage, top consumer, status tier
├─ 📋 Apps           → list + search + sort/filter → App Detail (bottom sheet)
├─ ❄️  Hibernate      → hibernation set, tombol besar "Hibernate Now", rules
└─ ⚙️  Settings       → izin, auto-hibernate, whitelist, log, tema, about
```

### 5.2 Prinsip Visual
- **Material 3 + dynamic color** — nyatu sama HyperOS
- **Chip status berwarna** untuk standby bucket: ACTIVE (hijau) · WORKING_SET (biru) · FREQUENT (kuning) · RARE (oranye) · RESTRICTED (merah)
- **Dense list** — pakai `LazyColumn` dengan `key = packageName`, ikon di-load lewat Coil dengan custom fetcher untuk `Drawable`
- **Skeleton loading**, bukan spinner kosong
- **Empty state yang mendidik**: kalau Usage Access belum di-grant, jangan tampilkan list kosong — tampilkan kartu "Aktifkan Usage Access buat lihat data pemakaian" + tombol langsung ke Settings

### 5.3 Micro-interaction Penting
- Swipe kiri di baris app → force stop cepat (dengan undo snackbar 5 detik)
- Long-press → multi-select mode → aksi batal massal
- Pull-to-refresh di Apps
- Haptic feedback saat aksi destruktif

### 5.4 Copywriting Aksi Destruktif
Jangan cuma "Yakin?". Contoh yang benar:

> **Force stop WhatsApp?**
> App akan berhenti total. Lo **nggak akan terima notifikasi** dari app ini sampai lo membukanya lagi secara manual.
> [Batal] [Tambah ke whitelist] [Force stop]

---

## 6. Roadmap Implementasi

| Milestone | Isi | Output yang bisa dites |
|---|---|---|
| **M0** Setup | Project skeleton, modul, Hilt, Compose, tema, navigation | App kosong yang jalan |
| **M1** List statis | PackageManager → Room → LazyColumn + ikon + search | Bisa lihat semua app terinstall |
| **M2** Usage Access | Permission flow, UsageStatsManager, StorageStats, NetworkStats | List sudah ada data pakai, sort/filter jalan |
| **M3** Dashboard | RAM/baterai/storage cards, top consumer | Dashboard hidup |
| **M4** Shizuku | Integrasi Shizuku, `AppController` + router, parsing `dumpsys` | **Daftar proses asli + RAM per app** |
| **M5** Aksi | Force stop via Shizuku, ActionLog, undo, konfirmasi | Bisa benar-benar matiin app |
| **M6** Accessibility | Fallback controller, node detection | Force stop tanpa Shizuku |
| **M7** Hibernate | Hibernation set, screen-off trigger, foreground service, whitelist | Greenify mode jalan |
| **M8** Histori | WorkManager snapshot, retention, grafik Vico | Grafik 7 hari |
| **M9** Rules | Rules engine, evaluator, scheduler | Otomatisasi kondisional |
| **M10** Polish | Widget, QS tile, export, dark theme, animasi | Rilis v1.0 pribadi |

**Saran kuat:** M1–M3 dulu sampai benar-benar solid. Godaan terbesar adalah loncat ke Shizuku duluan karena itu bagian yang seru — tapi kalau fondasi data & UI belum rapi, integrasi privileged akan jadi spaghetti.

---

## 7. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| **HyperOS agresif membunuh app lo sendiri** | Foreground service mati, auto-hibernate nggak jalan | Set Battery saver app lo ke "No restrictions", aktifkan Autostart di Security app, minta `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| **HyperOS punya manajemen background sendiri** | Konflik / hasil nggak konsisten | Dokumentasikan overlap; pertimbangkan matikan MIUI optimization di Developer options saat testing |
| **Shizuku mati setelah reboot** | Fitur premium hilang mendadak | Deteksi di startup → banner persisten "Shizuku nggak aktif, restart dulu" + deep link ke app Shizuku + degradasi otomatis ke tier bawah |
| **Format `dumpsys` berubah antar versi Android** | Parser pecah | Jangan parsing regex rapuh — bikin parser bertingkat dengan fallback, plus unit test pakai sample output yang di-capture. Prefer hidden API (`IActivityManager`) daripada parsing teks kalau bisa |
| **Accessibility node beda per bahasa/OEM** | Force stop gagal diam-diam | Cari via `viewIdResourceName` dulu, fallback ke teks multi-bahasa, timeout + log kegagalan eksplisit |
| **Force-stop app kritikal** | Alarm nggak bunyi, notif hilang, panik jam 7 pagi | Whitelist default agresif + deteksi kategori app + konfirmasi eksplisit |
| **Hidden API blacklist (Android 9+)** | `NoSuchMethodException` saat panggil `IActivityManager` | `HiddenApiBypass.addHiddenApiExemptions("")` di `Application.onCreate()` |
| **DB membengkak** | Storage & performa | Retention 30 hari + agregasi harian untuk data lama |
| **Polling boros baterai** | Ironis: app hemat baterai malah boros | Polling hanya saat UI visible; snapshot background max tiap 15 menit; hormati Doze |

---

## 8. Strategi Testing

**Unit test**
- `CapabilityRouter` dengan fake controller (semua kombinasi tier)
- Parser `dumpsys` dengan file sample yang di-capture dari device
- Rules evaluator: matriks kondisi/aksi
- Mapper & repository dengan fake data source

**Instrumented test**
- Room migration test (wajib, biar data lo nggak hilang tiap update schema)
- Compose UI test untuk state kosong/loading/error tiap layar

**Manual test matrix (di Poco X6)**
- Tanpa izin apa pun → app harus tetap jalan & informatif
- Usage Access saja
- Usage Access + Shizuku
- Usage Access + Accessibility (Shizuku mati)
- Setelah reboot (Shizuku belum di-start)
- Saat Battery Saver aktif
- Saat Doze aktif (`adb shell dumpsys deviceidle force-idle`)

**Debugging tools:** `adb shell dumpsys activity processes | grep <pkg>`, `adb shell dumpsys usagestats`, LeakCanary di debug build.

---

## 9. Struktur Kerja Harian (biar nggak stuck)

1. Tiap milestone → bikin branch sendiri
2. Tiap fitur → tulis interface & data class dulu, baru implementasi
3. Selalu punya build yang bisa di-install di HP lo — dogfood tiap hari, karena bug UX cuma ketahuan kalau dipakai beneran
4. Catat setiap perilaku aneh HyperOS di file `HYPEROS-QUIRKS.md` — ini akan jadi dokumen paling berharga di repo lo

---

## 10. Keputusan yang Perlu Lo Ambil Sebelum Ngoding

| # | Pertanyaan | Rekomendasi gue |
|---|---|---|
| 1 | Root, Shizuku, atau Accessibility? | **Shizuku primary + Accessibility fallback.** Skip root — mengurangi keamanan device, dan itu justru alasan lo bikin sendiri |
| 2 | minSdk berapa? | **29** (atau 31 kalau mau lebih simpel). Device lo Android 14, nggak perlu kompatibilitas jadul |
| 3 | Compose atau XML? | **Compose.** Proyek baru, solo dev, iterasi cepat |
| 4 | Modular atau single-module? | Mulai **single-module tapi package-nya dirapikan seperti struktur modul di §4.2**, pecah jadi modul asli di M5+ kalau build time mulai lambat |
| 5 | Fitur mana yang paling lo butuhin sehari-hari? | Ini yang menentukan urutan M4 vs M7 — kalau tujuannya hemat baterai, kejar Hibernate duluan; kalau tujuannya diagnosa, kejar Proses duluan |

---

*Dokumen ini adalah konsep, bukan spesifikasi beku. Update terus seiring lo nemu quirk HyperOS.*
