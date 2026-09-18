package app.orariunimi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {
    private val openSavedRequest = mutableIntStateOf(0)
    private val openNotificationsRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(OPEN_SAVED, false)) openSavedRequest.intValue++
        if (intent.getBooleanExtra(OPEN_NOTIFICATIONS, false)) openNotificationsRequest.intValue++
        enableEdgeToEdge()
        setContent {
            OrariTheme {
                OrariApp(
                    initialTab = if (intent.getBooleanExtra(OPEN_SAVED, false)) 1 else 0,
                    openSavedRequest = openSavedRequest.intValue,
                    openNotificationsRequest = openNotificationsRequest.intValue
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.enterForeground()
    }

    override fun onResume() {
        super.onResume()
        NotificationScheduler.configure(applicationContext, runNow = true)
    }

    override fun onStop() {
        AppVisibility.enterBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OPEN_SAVED, false)) openSavedRequest.intValue++
        if (intent.getBooleanExtra(OPEN_NOTIFICATIONS, false)) openNotificationsRequest.intValue++
    }

    companion object {
        const val OPEN_SAVED = "open_saved"
        const val OPEN_NOTIFICATIONS = "open_notifications"
    }
}
