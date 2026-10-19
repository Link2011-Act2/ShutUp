package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R

private data class Entry(val title: String, val coordinate: String, val license: String, val url: String, val asset: String)

@Composable
internal fun OssLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = listOf(
            Entry("Project Pyrite", "com.github.Link2011-Act2:Pyrite:43557c8", "MIT License", "https://github.com/Link2011-Act2/Pyrite", "Pyrite-MIT.txt"),
            Entry("ShutterSoundToggle", stringResource(R.string.license_upstream_description), "NYSL 0.9982", "https://github.com/soralis0912/ShutterSoundToggle", "NYSL.txt"),
            Entry("AndroidX", "Jetpack Compose / Activity / Lifecycle / WorkManager / androidx.*", "Apache License 2.0", "https://source.android.com/license", "Apache-2.0.txt"),
            Entry("Markwon", "io.noties.markwon:core / ext-tables:4.6.2", "Apache License 2.0", "https://github.com/noties/Markwon", "Apache-2.0.txt"),
            Entry("commonmark-java", "com.atlassian.commonmark:commonmark / commonmark-ext-gfm-tables:0.13.0", "BSD 2-Clause License", "https://github.com/commonmark/commonmark-java", "commonmark-BSD-2-Clause.txt"),
            Entry("Material Design icons / Material Symbols", "Google Material icons and Symbols", "Apache License 2.0", "https://github.com/google/material-design-icons", "Apache-2.0.txt"),
            Entry("Shizuku API and Provider", "dev.rikka.shizuku:api / provider:13.1.5", "Apache License 2.0", "https://github.com/RikkaApps/Shizuku-API", "Apache-2.0.txt"),
            Entry("HiddenApiBypass", "org.lsposed.hiddenapibypass:hiddenapibypass:6.1", "Apache License 2.0", "https://github.com/LSPosed/AndroidHiddenApiBypass", "Apache-2.0.txt"),
            Entry("Kotlin", "org.jetbrains.kotlin:kotlin-stdlib", "Apache License 2.0", "https://github.com/JetBrains/kotlin", "Apache-2.0.txt"),
            Entry("Kotlin Coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-*", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines", "Apache-2.0.txt"),
            Entry("Guava ListenableFuture", "com.google.guava:listenablefuture:1.0", "Apache License 2.0", "https://github.com/google/guava", "Apache-2.0.txt"),
            Entry("Error Prone Annotations", "com.google.errorprone:error_prone_annotations", "Apache License 2.0", "https://github.com/google/error-prone", "Apache-2.0.txt"),
            Entry("JetBrains Annotations", "org.jetbrains:annotations", "Apache License 2.0", "https://github.com/JetBrains/java-annotations", "Apache-2.0.txt"),
            Entry("JSpecify", "org.jspecify:jspecify:1.0.0", "Apache License 2.0", "https://github.com/jspecify/jspecify", "Apache-2.0.txt")
        )
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    ScreenScaffold(stringResource(R.string.about_licenses_title), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.title }) { index, entry ->
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(entry.license, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.coordinate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.license_show_text))
                        }
                        TextButton(onClick = { openUrl(context, entry.url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.license_open_project))
                        }
                    }
                }
            }
        }
    }
    selected?.let { index ->
        val entry = entries[index]
        val loadFailed = stringResource(R.string.license_load_failed)
        val text = remember(entry.asset, context, loadFailed) {
            runCatching { context.assets.open("licenses/${entry.asset}").bufferedReader().use { it.readText() } }
                .getOrElse { loadFailed }
        }
        TextDialog(entry.title, text, { selected = null })
    }
}

