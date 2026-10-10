import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same content hash, source timestamp, and channel prefix as glyphvisualizer.
val buildNumberFiles = (
    fileTree("src") {
        exclude("**/build/**")
    }.files + listOf(
        project.file("build.gradle.kts"),
        rootProject.file("build.gradle.kts"),
        rootProject.file("settings.gradle.kts"),
        rootProject.file("gradle/libs.versions.toml")
    ).filter { it.isFile }
).sortedBy { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }

val appCodeName = "ShutUp"
val appVersionName = System.getenv("VERSION_NAME") ?: "1.0.0-IntDev"
val buildContentHash = MessageDigest.getInstance("SHA-256").run {
    buildNumberFiles.forEach { file ->
        update(file.relativeTo(rootProject.projectDir).invariantSeparatorsPath.toByteArray())
        update(0)
        update(file.readBytes())
        update(0)
    }
    digest().joinToString("") { "%02x".format(it) }
}
val buildTimestamp = DateTimeFormatter.ofPattern("yyMMdd-HHmm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(buildNumberFiles.maxOf { it.lastModified() }))
val buildNumberVersionPrefix = if (
    appVersionName.substringAfter('-', missingDelimiterValue = "")
        .startsWith("IntDev", ignoreCase = true)
) {
    "d"
} else {
    "v"
}
val generatedBuildNumber =
    "$appCodeName-$buildNumberVersionPrefix${appVersionName.substringBefore('-')}-$buildTimestamp-${buildContentHash.take(3)}"

// Signing comes from the environment: CI points STORE_FILE at the public AOSP
// testkey.jks, a tagged release at the key restored from secrets. Without it
// the release build falls back to the local debug key so that it still
// installs.
val keystorePath: String? = System.getenv("STORE_FILE")

android {
    namespace = "jp.linkserver.shutter"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.linkserver.shutter"
        // MediaActionSound.mustPlayShutterSound() tells what to restore to.
        minSdk = 33
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = appVersionName
        buildConfigField("String", "BUILD_NUMBER", "\"$generatedBuildNumber\"")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
}
