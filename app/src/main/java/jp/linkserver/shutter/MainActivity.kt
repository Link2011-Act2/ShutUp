package jp.linkserver.shutter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import jp.linkserver.shutter.ui.ShutterApp
import jp.linkserver.shutter.ui.ShutterTheme

class MainActivity : ComponentActivity() {
    private val shutter: ShutterViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        shutter.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShutterTheme {
                ShutterApp(shutter) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    shutter.apply(ShutterTileService.released())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shutter.refresh()
    }
}
