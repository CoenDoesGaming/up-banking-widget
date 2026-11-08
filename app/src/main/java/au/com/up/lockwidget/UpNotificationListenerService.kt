package au.com.up.lockwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Runs in the background even while the phone is locked:
 * 1. Triggers an immediate sync whenever the Lock Screen turns on (ACTION_SCREEN_ON).
 * 2. Runs a 60-second (1-minute) periodic sync timer.
 * 3. Triggers an instant sync 1.5s after any Up app notification is posted.
 */
class UpNotificationListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    private val instantSyncRunnable = Runnable {
        WidgetUpdateHelper.refreshFromApiAsync(applicationContext)
    }

    private val minuteTickerRunnable = object : Runnable {
        override fun run() {
            WidgetUpdateHelper.refreshFromApiAsync(applicationContext)
            handler.postDelayed(this, AutoSyncScheduler.ALARM_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    WidgetUpdateHelper.refreshFromApiAsync(applicationContext)
                }
            }
        }
    }

    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        registerScreenWakeReceiver()
        handler.post(minuteTickerRunnable)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoSyncScheduler.ensureScheduled(applicationContext)
        registerScreenWakeReceiver()
        handler.removeCallbacks(minuteTickerRunnable)
        handler.post(minuteTickerRunnable)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == LaunchUpActivity.UP_PACKAGE_NAME) {
            handler.removeCallbacks(instantSyncRunnable)
            handler.postDelayed(instantSyncRunnable, 1500L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun registerScreenWakeReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
    }
}
