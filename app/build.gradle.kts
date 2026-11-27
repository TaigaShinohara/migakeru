import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Persistent signing key in the project's .local/ (never the default debug
// keystore, which is regenerated when the build machine is replaced).
val signingPropsFile = rootProject.file(".local/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}
val hasPersistentSigning = signingPropsFile.exists() &&
    rootProject.file(".local/migakeru.keystore").exists()

android {
    namespace = "com.taigashinohara.migakeru"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.taigashinohara.migakeru"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.11.2"
    }

    signingConfigs {
        if (hasPersistentSigning) {
            create("persistent") {
                storeFile = rootProject.file(".local/migakeru.keystore")
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
        release {
            isMinifyEnabled = false
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

android {
    testOptions {
        // Robolectric needs the merged resources to launch the activity.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // v0.11.0: Health Connect (read-only) for the auto-check shelf.
    // beta01 is the newest release whose AAR metadata still accepts this
    // project's AGP 8.7.3 / compileSdk 35 (1.1.0 stable demands AGP 8.9.1).
    implementation("androidx.health.connect:connect-client:1.1.0-beta01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    // Pre-delivery UI verification on the JVM (no emulator in the build VM):
    // Robolectric launches the real activity; Roborazzi screenshots it.
    // See docs/UI_VERIFICATION.md.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.0")
}

// In this build VM, loopback TCP via IPv6 is intercepted and breaks the
// Gradle test worker; force IPv4 for the test JVM.
tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    maxHeapSize = "1536m"
    // Robolectric resolves its android-all runtime at test time; the test
    // JVM has no usable network here, so point it at the pre-downloaded
    // jar in .local/robolectric-deps/ (offline mode).
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        rootProject.file(".local/robolectric-deps").absolutePath,
    )
    // Deterministic screenshots: pin the timezone (the sandbox clock is
    // fixed, so the date line and greeting render identically each run).
    systemProperty("user.timezone", "Asia/Tokyo")
    // Roborazzi: verify against goldens by default; re-record with
    // ROBORAZZI_RECORD=1 in the environment.
    val record = System.getenv("ROBORAZZI_RECORD") == "1"
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
}
