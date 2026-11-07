pluginManagement {
    val trickuildVersion = "2926f49060e58e2701f97de3c92322be26ed9811"
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-gradle-plugin")
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-core")
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-android-adapter")
            }
            metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.trickuild") {
                useModule("com.github.Link2011-Act2.Trickuild:trickuild-gradle-plugin:" + (requested.version ?: trickuildVersion))
            }
        }
    }
    plugins { id("dev.trickuild") version trickuildVersion }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.Link2011-Act2.Goldy") }
        }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.Link2011-Act2") }
        }
    }
}
rootProject.name = "ShutUp"
include(":app")
