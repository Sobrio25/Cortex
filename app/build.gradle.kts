import java.security.MessageDigest
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val cortexDebugKeystore = file("${System.getProperty("user.home")}/.android/cortex-debug.keystore")

android {
    namespace = "com.aiagents.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiagents.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.2"
        buildConfigField("boolean", "EXTERNAL_VOICE_PACK", "false")
        buildConfigField("String", "VOICE_PACK_MANIFEST_URL", "\"\"")

        // Cortex targets modern physical Android devices only. Keeping a single ABI avoids
        // packaging native copies for legacy 32-bit phones and Intel emulators.
        ndk {
            abiFilters += "arm64-v8a"
        }

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
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
        create("sideload") {
            initWith(getByName("release"))
            signingConfigs.findByName("cortexDebug")?.let { signingConfig = it }
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "EXTERNAL_VOICE_PACK", "true")
            buildConfigField(
                "String",
                "VOICE_PACK_MANIFEST_URL",
                "\"https://cortex-agents-voice-pack.web.app/cortex-voice-pack.json\""
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
            pickFirsts += "**/libonnxruntime.so"
        }
    }
    dynamicFeatures += setOf(":voice")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

val releaseApkBudgetBytes = 240L * 1024L * 1024L

tasks.register("reportReleaseApkSize") {
    group = "verification"
    description = "Builds the release APK, reports its reproducible size breakdown and checks its budget."
    dependsOn("assembleRelease")

    doLast {
        val outputDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = outputDirectory.listFiles { file -> file.extension == "apk" }.orEmpty()
        val apk = apks.maxByOrNull { it.lastModified() }
            ?: error("No release APK found in ${outputDirectory.absolutePath}")
        val messageDigest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                messageDigest.update(buffer, 0, count)
            }
        }
        val digest = messageDigest.digest()
            .joinToString("") { byte: Byte -> "%02x".format(byte) }
        val entries: List<Pair<String, Long>> = ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { entry -> entry.name to entry.size.coerceAtLeast(0L) }
                .toList()
        }
        val groups = entries.groupBy { (name, _) ->
            when {
                name.startsWith("lib/") -> "native libraries"
                name.startsWith("classes") && name.endsWith(".dex") -> "dex"
                name.startsWith("assets/") -> "assets"
                name.startsWith("res/") || name == "resources.arsc" -> "resources"
                else -> "other"
            }
        }.mapValues { (_, values) -> values.sumOf { it.second } }
        val mib = 1024.0 * 1024.0

        logger.lifecycle("Release APK: ${apk.absolutePath}")
        logger.lifecycle("Compressed: %.2f MiB (budget %.0f MiB)".format(apk.length() / mib, releaseApkBudgetBytes / mib))
        logger.lifecycle("Uncompressed: %.2f MiB".format(entries.sumOf { it.second } / mib))
        groups.entries.sortedByDescending { it.value }.forEach { (name, bytes) ->
            logger.lifecycle("  %-18s %8.2f MiB".format(name, bytes / mib))
        }
        logger.lifecycle("Largest entries:")
        entries.sortedByDescending { it.second }.take(10).forEach { (name, bytes) ->
            logger.lifecycle("  %8.2f MiB  %s".format(bytes / mib, name))
        }
        logger.lifecycle("SHA-256: $digest")

        check(apk.length() <= releaseApkBudgetBytes) {
            "Release APK is %.2f MiB; budget is %.0f MiB".format(
                apk.length() / mib,
                releaseApkBudgetBytes / mib
            )
        }
    }
}

tasks.register("checkReleaseNativeAlignment") {
    group = "verification"
    description = "Fails when an arm64 release library has an ELF load segment aligned below 16 KiB."
    dependsOn("mergeReleaseNativeLibs")

    doLast {
        val nativeDirectory = layout.buildDirectory
            .dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a")
            .get().asFile
        val libraries = nativeDirectory.listFiles { file -> file.extension == "so" }
            .orEmpty()
            .sortedBy { it.name }
        check(libraries.isNotEmpty()) { "No arm64 release libraries found in ${nativeDirectory.absolutePath}" }
        check(libraries.none { it.name == "libvosk.so" }) {
            "libvosk.so is not 16 KiB compatible and must not be packaged in release"
        }

        fun minimumLoadAlignment(file: File): Long = RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(64)
            input.readFully(header)
            check(header[0] == 0x7f.toByte() && header[1].toInt().toChar() == 'E' &&
                header[2].toInt().toChar() == 'L' && header[3].toInt().toChar() == 'F') {
                "${file.name} is not an ELF binary"
            }
            val is64Bit = header[4].toInt() == 2
            val order = when (header[5].toInt()) {
                1 -> ByteOrder.LITTLE_ENDIAN
                2 -> ByteOrder.BIG_ENDIAN
                else -> error("Unknown ELF byte order in ${file.name}")
            }
            val buffer = ByteBuffer.wrap(header).order(order)
            val programOffset = if (is64Bit) buffer.getLong(32) else buffer.getInt(28).toLong() and 0xffffffffL
            val entrySize = (buffer.getShort(if (is64Bit) 54 else 42).toInt() and 0xffff)
            val entryCount = (buffer.getShort(if (is64Bit) 56 else 44).toInt() and 0xffff)
            check(entrySize > 0 && entryCount > 0) { "${file.name} has no ELF program headers" }
            val programHeaders = ByteArray(entrySize * entryCount)
            input.seek(programOffset)
            input.readFully(programHeaders)
            val entries = ByteBuffer.wrap(programHeaders).order(order)
            buildList {
                repeat(entryCount) { index ->
                    val entryOffset = index * entrySize
                    if (entries.getInt(entryOffset) == 1) {
                        val alignment = if (is64Bit) {
                            entries.getLong(entryOffset + 48)
                        } else {
                            entries.getInt(entryOffset + 28).toLong() and 0xffffffffL
                        }
                        add(alignment)
                    }
                }
            }.minOrNull() ?: error("${file.name} has no ELF LOAD segments")
        }

        val incompatible = libraries.map { it to minimumLoadAlignment(it) }
            .filter { (_, alignment) -> alignment < 16L * 1024L }
        libraries.forEach { library ->
            logger.lifecycle("%-42s alignment 0x%x".format(library.name, minimumLoadAlignment(library)))
        }
        check(incompatible.isEmpty()) {
            incompatible.joinToString(
                prefix = "Release contains arm64 libraries below 16 KiB alignment:\n",
                separator = "\n"
            ) { (file, alignment) -> "${file.name}: 0x${alignment.toString(16)}" }
        }
    }
}

tasks.register("prepareSideloadVoicePack") {
    group = "distribution"
    description = "Builds the signed sideload split and stages Cortex Voice Pack for Firebase Hosting."
    dependsOn("assembleSideload", ":voice:assembleSideload")

    doLast {
        val outputDirectory = rootProject.file("voice/build/outputs/apk/sideload")
        val sourceApk = outputDirectory.listFiles { file -> file.extension == "apk" }
            .orEmpty()
            .singleOrNull()
            ?: error("Expected one sideload voice APK in ${outputDirectory.absolutePath}")
        val hostingDirectory = rootProject.file("dashboard/downloads").apply { mkdirs() }
        val hostedApk = hostingDirectory.resolve("cortex-voice-pack.apk")
        sourceApk.copyTo(hostedApk, overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256")
        hostedApk.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val versionCode = android.defaultConfig.versionCode
            ?: error("The app versionCode is required for Cortex Voice Pack")
        hostingDirectory.resolve("cortex-voice-pack.json").writeText(
            """
            {
              "versionCode": $versionCode,
              "splitName": "voice",
              "url": "https://cortex-agents-voice-pack.web.app/cortex-voice-pack.apk",
              "sha256": "$sha256",
              "bytes": ${hostedApk.length()}
            }
            """.trimIndent() + "\n"
        )
        logger.lifecycle("Cortex Voice Pack: ${hostedApk.absolutePath}")
        logger.lifecycle("SHA-256: $sha256")
    }
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
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Local LLM inference via MediaPipe (Google)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    
    // LiteRT-LM for Gemma 4 and newer .litertlm models
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.9.0")

    // The optional offline STT engine lives in the :voice dynamic feature. The base app uses
    // Android's built-in recognizer and downloads no speech native code until it is requested.
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")

    // Embedded HTTP server for serving multi-file web projects in WebView
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
