package au.com.up.lockwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object AutoSyncScheduler {
    private const val JOB_ID_PERIODIC_SYNC = 1001
    private const val ALARM_REQUEST_CODE = 2002

    // 5 minutes interval while device is awake
    private const val ALARM_INTERVAL_MS = 5 * 60 * 1000L

    // 15 minutes periodic JobScheduler interval (Android minimum periodic interval)
    private const val JOB_INTERVAL_MS = 15 * 60 * 1000L

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        schedulePeriodicJob(appContext)
        scheduleRepeatingAlarm(appContext)
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

    private fun scheduleRepeatingAlarm(context: Context) {
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

        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
            ALARM_INTERVAL_MS,
            pendingIntent
        )
    }
}
