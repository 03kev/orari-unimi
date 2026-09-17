package app.orariunimi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdater.cleanupDownloads(context)
        }
    }
}
