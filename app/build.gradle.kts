plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.blitztech.pudokiosk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blitztech.pudokiosk"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0", "META-INF/LGPL2.1"
        )
    }
}

dependencies {
    // AndroidX core/UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // QR code generator for labels
    implementation(libs.core)

    // SQLCipher for encrypted Room
    implementation(libs.android.database.sqlcipher)
    implementation(libs.okio) // SHA-256/streams helpers
    // AndroidX Security Crypto (to protect the DB passphrase)
    implementation(libs.androidx.security.crypto)

    // Room (DB)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Moshi (JSON for payloads)
    implementation(libs.moshi.kotlin)

    // WorkManager (we’ll wire later)
    implementation(libs.androidx.work.runtime.ktx)

    // Networking (we’ll wire later)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // USB-Serial (for later phases)
    implementation(libs.usb.serial.for1.android)
    implementation("com.github.mik3y:usb-serial-for-android:3.5.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}