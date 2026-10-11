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

val appCodeName = "Kyuri"
val appVersionName = System.getenv("VERSION_NAME") ?: "1.0.0-Release"
// Increase this for each distributed APK, regardless of its release channel.
val appVersionCode = 2
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

// Local release signing comes from the environment. Without a configured
// keystore, release builds use the local debug key.
val keystorePath: String? = System.getenv("STORE_FILE")

android {
    namespace = "jp.linkserver.shutter"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.linkserver.shutter"
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = appVersionCode
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
            optimization {
                enable = true
            }
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
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Same release-note renderer as NITTC Scheduler.
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
}
