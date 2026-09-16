package app.orariunimi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {
    private val openSavedRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(OPEN_SAVED, false)) openSavedRequest.intValue++
        enableEdgeToEdge()
        setContent {
            OrariTheme {
                OrariApp(
                    initialTab = if (intent.getBooleanExtra(OPEN_SAVED, false)) 1 else 0,
                    openSavedRequest = openSavedRequest.intValue
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OPEN_SAVED, false)) openSavedRequest.intValue++
    }

    companion object {
        const val OPEN_SAVED = "open_saved"
    }
}
