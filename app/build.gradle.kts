
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing details, read from `keystore.properties` at the repository root.
 *
 * That file is **git-ignored** and must never be committed: it holds the password to the key that
 * identifies this app on every device it is ever installed on. Losing it means you cannot publish
 * an update over an existing install; leaking it means anyone can.
 *
 * When the file is absent the release build falls back to unsigned, which is what CI and a fresh
 * clone will see. That is deliberate: `assembleRelease` must not fail just because a machine has no
 * signing key, and an unsigned artifact is obviously unusable rather than silently wrong.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.stockapplication"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // Both signatures, so the APK installs on anything from API 24 up. v1 alone is
                // rejected on modern Android, and v3 alone is rejected by older devices.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.krisoll.crummystock"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * Strip emulator-only architectures from **release** builds.
     *
     * ML Kit's barcode scanner ships one `libbarhopper_v3.so` per ABI and it is the largest thing
     * in the APK — 5.98 MB for x86 and 5.77 MB for x86_64, against 4.83 MB for the arm64 library
     * real phones use. Dropping the two x86 variants therefore takes a universal release APK from
     * 28.7 MB to roughly 19 MB.
     *
     * This is deliberately scoped to `release` only, and the reason matters:
     *
     *  - `debug` keeps all four ABIs, so emulator testing is unaffected. A filter applied globally
     *    would try to install an arm64-only APK on an x86_64 emulator and fail with
     *    INSTALL_FAILED_NO_MATCHING_ABIS, which is a confusing way to lose an afternoon.
     *  - Play users were never downloading the x86 libraries anyway once ABI splits are on; this
     *    only affects the *universal* APK, which is the one shipped by hand for off-Play sales.
     *
     * Chromebooks and some Android-on-x86 devices would be excluded by this. If that matters later,
     * add "x86_64" back to the list rather than dropping to every ABI again.
     */
    buildTypes {
        debug {
        }
        release {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            // Only set when `keystore.properties` exists, so a machine without the key still
            // produces a (necessarily unsigned) artifact instead of failing.
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // The version footer on the Config page reads VERSION_NAME / VERSION_CODE from here, so
        // the number a user reports in a bug report is the one that was actually built.
        buildConfig = true
    }

    /**
     * Split the app by ABI, screen density and language.
     *
     * The APK is dominated by native libraries (ML Kit and Compose pull in ~19.5 MB across four
     * ABIs), so a single universal APK makes every device download all four. Play serves an App
     * Bundle's splits selectively, so an arm64 phone downloads only the arm64 libraries.
     *
     * `language` is enabled because the app ships English and Spanish: without it every user
     * downloads both string tables.
     *
     * These splits only apply to the bundle and to `installDebug`-style APKs. A universal APK can
     * still be built for manual distribution and for tools that expect one file.
     */
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals (FileDescriptor, SharedSecrets); the modules it
        // needs are not exported by default on newer JDKs.
        unitTests.all { test ->
            test.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Per-app language selection (AppCompatDelegate.setApplicationLocales)
    implementation(libs.androidx.appcompat)
    // EXIF orientation, so camera photos are not stored rotated
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera + QR/barcode scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.accompanist.permissions)

    // QR code image generation for printable labels
    implementation(libs.zxing.core)

    // Decoding the startup screen's picked image, including animated GIF / WebP. Android's own
    // BitmapFactory only ever yields the first frame of a GIF, so animation needs a real decoder.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // User-imported sale animation. Only the `-compose` artefact is needed: it wraps the parser
    // and player in composables, so the receipt draws it like any other content.
    implementation(libs.lottie.compose)

    // Persisted user settings (export folder, colours)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // ApplicationProvider, for reading string resources in Robolectric unit tests
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
