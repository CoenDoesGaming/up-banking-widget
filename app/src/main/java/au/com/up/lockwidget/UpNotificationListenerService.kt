package au.com.up.lockwidget

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional real-time trigger: whenever the official Up app (au.com.up.money) posts a
 * transaction or transfer notification, automatically refreshes all Lock Screen widgets.
 */
class UpNotificationListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable {
        WidgetUpdateHelper.refreshFromApiAsync(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoSyncScheduler.ensureScheduled(applicationContext)
        WidgetUpdateHelper.refreshFromApiAsync(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == LaunchUpActivity.UP_PACKAGE_NAME) {
            handler.removeCallbacks(syncRunnable)
            // Wait 1.5s so the new transaction is committed on the Up API before fetching
            handler.postDelayed(syncRunnable, 1500L)
        }
    }
}
