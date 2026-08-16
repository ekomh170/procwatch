import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing is read from keystore.properties in the project root, which is gitignored
 * along with the keystore itself. Nothing secret ever reaches the repository.
 *
 * When the file is absent — a fresh clone, or a machine that only builds debug — the release
 * signing config is simply not created and `assembleRelease` produces an unsigned APK, exactly
 * as it did before. Debug builds are unaffected; Gradle signs those with its own debug key.
 *
 * See keystore.properties.example for the four keys this expects.
 */
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystore.getProperty("storeFile") != null

android {
    namespace = "com.procwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.procwatch"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off. The privileged paths reach Shizuku.newProcess and
            // getAppStandbyBucket by reflection, which static analysis cannot follow, and a
            // sideloaded personal build gains nothing from a smaller APK that is worth the
            // risk of a silently stripped method.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null when keystore.properties is absent, which leaves the APK unsigned rather
            // than failing the build with a confusing message about a missing store file.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Keeps a debug install alongside a release one. Note that this makes it a
            // different package: Shizuku authorises per package, and the whitelist and action
            // log live in that package's SharedPreferences, so the two builds do not share
            // either. Pick one for daily use.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Lint would fail the build on QUERY_ALL_PACKAGES; this app is sideloaded, not published.
    lint {
        disable += listOf("QueryAllPackagesPermission", "ProtectedPermissions")
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Shizuku: ADB-level privileges without root.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Lets us call @hide framework APIs (getAppStandbyBucket) on Android 9+.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
}
