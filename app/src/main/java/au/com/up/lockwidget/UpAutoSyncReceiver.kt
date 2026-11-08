package au.com.up.lockwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpAutoSyncReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AUTO_SYNC = "au.com.up.lockwidget.ACTION_AUTO_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        AutoSyncScheduler.ensureScheduled(context)
        val pendingResult = goAsync()
        WidgetUpdateHelper.refreshFromApiAsync(context, pendingResult)
    }
}
