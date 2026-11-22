plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ocrMode: String = (project.findProperty("ocrMode") as String?) ?: "unbundled"
val abiList: List<String> =
    ((project.findProperty("abis") as String?) ?: "arm64-v8a").split(",").map { it.trim() }

android {
    namespace = "com.example.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += abiList }
        buildConfigField("String", "OCR_MODE", "\"$ocrMode\"")
    }

    signingConfigs {
        val ksPath = System.getenv("KEYSTORE_PATH")
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    androidResources {
        // Vosk model is already compressed data; keep it uncompressed for fast copy
        noCompress += listOf("mdl", "fst", "int", "conf", "stats", "mat", "dubm", "ie", "txt", "bin")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Voice (offline)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")

    // OCR: same API, two distributions
    if (ocrMode == "bundled") {
        implementation("com.google.mlkit:text-recognition:16.0.0")
    } else {
        implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    }

    // Translation (models downloaded on demand, not in APK)
    implementation("com.google.mlkit:translate:17.0.3")

    // API key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
