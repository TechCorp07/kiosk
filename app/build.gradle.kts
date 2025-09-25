plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.blitztech.pudokiosk"

    // Perfect for Android 7.1.2 (API 25) - no compatibility issues
    compileSdk = 31        // Stable, well-tested, works great with API 25

    defaultConfig {
        applicationId = "com.blitztech.pudokiosk"
        minSdk = 25           // Your hardware API level
        targetSdk = 28        // Android 9 - good balance, stable features
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optimize for single-device deployment
        vectorDrawables.useSupportLibrary = true

        // Add resource configurations to reduce APK size (optional)
        resourceConfigurations += setOf("en", "sn", "nd") // Your supported languages only
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Keep false for easier debugging on hardware
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false

            // Optimize for your single device
            ndk {
                // Only include architectures your device needs
                //noinspection ChromeOsAbiSupport
                abiFilters += setOf("arm64-v8a") // RK3399 is ARM64
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        // Java 8 is perfect for API 25 - no need for Java 11 complexity
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"  // Matches Java 8

        // Kotlin compiler optimizations for older devices
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }

    // Disable unnecessary checks for faster builds
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += setOf("MissingTranslation", "ExtraTranslation", "VectorPath")
    }

    // Optimize for single device
    splits {
        abi {
            isEnable = false  // No need to split - single device deployment
        }
        density {
            isEnable = false  // No need to split - single screen density
        }
    }
}

dependencies {
    // AndroidX Core - All compatible with API 25 and compileSdk 31
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Custom printer API
    implementation(files("libs/customandroidapi.aar"))

    // QR Code generation for receipt labels
    implementation(libs.core)

    // Database - SQLCipher for encryption
    implementation(libs.android.database.sqlcipher)
    implementation(libs.okio)

    // Security Crypto - Fallback version (our Prefs.kt handles failures gracefully)
    implementation(libs.androidx.security.crypto)

    // Room Database - Compatible version
    implementation(libs.androidx.room.ktx)
    implementation(libs.filament.android)
    kapt(libs.androidx.room.compiler)

    // JSON parsing with Moshi
    implementation(libs.moshi.kotlin)

    // WorkManager - This version works without compileSdk 35 requirement!
    implementation(libs.androidx.work.runtime.ktx)

    // Networking stack
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // USB Serial communication (for hardware peripherals)
    implementation(libs.usb.serial.for1.android)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Optimize Kapt for faster builds
kapt {
    correctErrorTypes = true
    useBuildCache = true

    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}