import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.jakub_m_arch.voicemator"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jakub_m_arch.voicemator"
        minSdk = 34
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Parakeet is too heavy for 32-bit devices; shipping only arm64 keeps the APK small.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Release signing is configured only when the keystore is provided, as it is in CI.
        val keystoreFile = providers.environmentVariable("VOICEMATOR_KEYSTORE_FILE").orNull
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("VOICEMATOR_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("VOICEMATOR_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("VOICEMATOR_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// sherpa-onnx is not published to Maven Central, so the release AAR is fetched from GitHub
// and verified against a pinned checksum.
val sherpaOnnxVersion = "2.0.1"
val sherpaOnnxSha256 = "07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575"
val sherpaOnnxAar = layout.projectDirectory
    .file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar")
    .asFile

val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    description = "Downloads the sherpa-onnx Android library."
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/" +
        "sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar"
    val expectedSha256 = sherpaOnnxSha256
    val target = sherpaOnnxAar
    inputs.property("sha256", expectedSha256)
    outputs.file(target)

    doLast {
        target.parentFile.mkdirs()
        val bytes = URI(url).toURL().openStream().use { it.readBytes() }
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(actualSha256 == expectedSha256) {
            "Checksum mismatch for $url: expected $expectedSha256, got $actualSha256"
        }
        target.writeBytes(bytes)
    }
}

// Every library in the APK must be credited in ThirdPartyNotices.kt. This task fails when a
// dependency from an uncredited group appears or the sherpa-onnx version drifts from the notices.
val creditedMavenGroups = setOf(
    "org.jetbrains",
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
    "org.jspecify",
    "com.google.guava",
)
val thirdPartyNoticesSource = layout.projectDirectory
    .file("src/main/java/io/github/jakub_m_arch/voicemator/licenses/ThirdPartyNotices.kt")
    .asFile

val checkThirdPartyNotices = tasks.register("checkThirdPartyNotices") {
    description = "Verifies that all runtime dependencies are credited in the licences screen."
    group = "verification"
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    val noticesSource = thirdPartyNoticesSource
    val expectedSherpaOnnxEntry = "name = \"sherpa-onnx\",\n            version = \"$sherpaOnnxVersion\""
    val creditedGroups = creditedMavenGroups
    inputs.file(noticesSource)

    doLast {
        val uncredited = runtimeClasspath.get().incoming.resolutionResult.allComponents
            .filterNot { it.id is ProjectComponentIdentifier }
            .mapNotNull { it.moduleVersion?.group }
            .filterNot { it.startsWith("androidx.") || it in creditedGroups }
            .toSortedSet()
        check(uncredited.isEmpty()) {
            "Add these dependency groups to ThirdPartyNotices.kt and creditedMavenGroups: $uncredited"
        }
        check(expectedSherpaOnnxEntry in noticesSource.readText()) {
            "ThirdPartyNotices.kt does not list sherpa-onnx $sherpaOnnxVersion; update the native notices."
        }
    }
}

tasks.named("preBuild") { dependsOn(checkThirdPartyNotices) }

dependencies {
    implementation(files(sherpaOnnxAar).builtBy(downloadSherpaOnnx))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
