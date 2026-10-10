package jp.linkserver.shutter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.linkserver.shutter.ui.ShutterApp
import jp.linkserver.shutter.ui.ShutterTheme
import jp.linkserver.shutter.update.AppUpdateViewModel

class MainActivity : ComponentActivity() {
    private val shutter: ShutterViewModel by viewModels()
    private val updates: AppUpdateViewModel by viewModels()
    private var showUninstallReminder by mutableStateOf(false)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        shutter.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showUninstallReminder = savedInstanceState?.getBoolean(STATE_UNINSTALL_REMINDER)
            ?: (intent.action == ACTION_PREPARE_UNINSTALL)
        enableEdgeToEdge()
        setContent {
            ShutterTheme {
                ShutterApp(shutter, showUninstallReminder, { showUninstallReminder = false }, updates) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    shutter.release()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_PREPARE_UNINSTALL) showUninstallReminder = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_UNINSTALL_REMINDER, showUninstallReminder)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        shutter.refresh()
        jp.linkserver.shutter.maintain.AutoMaintainRuntime.get(this).checkNow()
        updates.checkForUpdates()
    }

    companion object {
        const val ACTION_PREPARE_UNINSTALL = "jp.linkserver.shutter.action.PREPARE_UNINSTALL"
        private const val STATE_UNINSTALL_REMINDER = "show_uninstall_reminder"
    }
}
