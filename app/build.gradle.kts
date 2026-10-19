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

val appCodeName = "Lethe" // トリッカル、定期。
val appVersionName = System.getenv("VERSION_NAME") ?: "1.1.1-IntDev"
// Increase this for each distributed APK, regardless of its release channel.
val appVersionCode = 5
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

// Accept Android Studio's signed-build wizard or the existing environment setup.
// Debug builds and analysis do not need credentials; release packaging must never
// silently fall back to a debug key.
val signingPropertyNames = mapOf(
    "STORE_FILE" to "android.injected.signing.store.file",
    "STORE_PASSWORD" to "android.injected.signing.store.password",
    "KEY_ALIAS" to "android.injected.signing.key.alias",
    "KEY_PASSWORD" to "android.injected.signing.key.password"
)
val useInjectedSigning = providers.gradleProperty(signingPropertyNames.getValue("STORE_FILE"))
    .orNull?.isNotBlank() == true
val releaseSigning = signingPropertyNames.mapValues { (environmentName, propertyName) ->
    (if (useInjectedSigning) providers.gradleProperty(propertyName).orNull else System.getenv(environmentName))
        ?.takeIf(String::isNotBlank)
}
val keystorePath = releaseSigning["STORE_FILE"]
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        val missing = releaseSigning.filterValues { it == null }.keys
        check(missing.isEmpty()) {
            "Release signing is required. Configure signing in Android Studio or set: ${missing.joinToString()}. Debug signing is not allowed for release APKs or bundles."
        }
        check(file(checkNotNull(keystorePath)).isFile) { "The configured release keystore does not exist." }
    }
}
tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle")) {
        dependsOn(validateReleaseSigning)
    }
}

android {
    namespace = "jp.linkserver.shutter"
    compileSdk = 37

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
                storePassword = releaseSigning["STORE_PASSWORD"]
                keyAlias = releaseSigning["KEY_ALIAS"]
                keyPassword = releaseSigning["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Fixed, published commit; no adjacent checkout or local Maven repository is required.
    implementation("com.github.Link2011-Act2:Pyrite:43557c8")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.animation:animation:1.12.0")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Same release-note renderer as NITTC Scheduler.
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
}
