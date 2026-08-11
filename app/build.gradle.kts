plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.sushem.ledger"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sushem.ledger"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Committed debug.keystore -> every build (local or CI), debug or release, is signed
    // with the SAME certificate, so the SHA-1 you register with Firebase for Google
    // Sign-In stays valid for both variants. See README "Firebase setup" for the
    // fingerprint. (For a real Play Store release you'd want a separate, private release
    // key — see the note in README.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false // keep off: WebView JS-interface methods rely on
            // reflection (@JavascriptInterface), and R8/ProGuard can strip or rename them
            // without careful keep rules — not worth the risk for this app's size.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")

    // Firebase (BoM manages compatible versions for all firebase-* libraries below)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics") // basic usage tracking

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
