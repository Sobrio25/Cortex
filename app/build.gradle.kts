plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val cortexDebugKeystore = file("${System.getProperty("user.home")}/.android/cortex-debug.keystore")

android {
    namespace = "com.aiagents.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiagents.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (cortexDebugKeystore.exists()) {
            create("cortexDebug") {
                storeFile = cortexDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        debug {
            signingConfigs.findByName("cortexDebug")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // sherpa-onnx bundles its own ONNX Runtime; avoid duplicates
            pickFirsts += "**/*.so"
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3-android:1.5.0-alpha11")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    
    // Location services (FusedLocationProvider)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Google Identity Services authorization for Workspace APIs
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    // Managed subscriptions: Firebase identity, Google sign-in + Google Play purchases.
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Local LLM inference via MediaPipe (Google)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    
    // LiteRT-LM for Gemma 4 and newer .litertlm models
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.9.0")

    // Speech-to-Text engines
    // sherpa-onnx: Whisper ONNX models with NNAPI support (Snapdragon NPU + Google Tensor)
    // Note: Using JitPack repository. Group ID is 'com.github.k2-fsa' not 'com.k2fsa.sherpa'
    implementation("com.github.k2-fsa:sherpa-onnx:1.10.38")
    // Vosk: lightweight offline STT for low-end devices
    implementation("com.alphacephei:vosk-android:0.3.47")
    // Needed for .tar.bz2 extraction of sherpa-onnx model archives from GitHub releases
    implementation("org.apache.commons:commons-compress:1.26.1")

    // Embedded HTTP server for serving multi-file web projects in WebView
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
