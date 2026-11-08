package au.com.up.lockwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object AutoSyncScheduler {
    private const val JOB_ID_PERIODIC_SYNC = 1001
    private const val ALARM_REQUEST_CODE = 2002

    // 1 minute (60,000 ms) automatic wakeup sync interval (works while locked/screen off)
    const val ALARM_INTERVAL_MS = 60 * 1000L

    // 15 minutes fallback JobScheduler interval
    private const val JOB_INTERVAL_MS = 15 * 60 * 1000L

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        scheduleNextOneMinuteWakeupAlarm(appContext)
        schedulePeriodicJob(appContext)
    }

    fun scheduleNextOneMinuteWakeupAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, UpAutoSyncReceiver::class.java).apply {
            action = UpAutoSyncReceiver.ACTION_AUTO_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun schedulePeriodicJob(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val existing = scheduler.getPendingJob(JOB_ID_PERIODIC_SYNC)
        if (existing != null) return

        val component = ComponentName(context, UpSyncJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID_PERIODIC_SYNC, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(JOB_INTERVAL_MS)
            .build()

        scheduler.schedule(jobInfo)
    }
}
