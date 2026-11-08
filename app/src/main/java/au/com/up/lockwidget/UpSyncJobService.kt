package au.com.up.lockwidget

import android.app.job.JobParameters
import android.app.job.JobService

class UpSyncJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        AutoSyncScheduler.ensureScheduled(applicationContext)
        WidgetUpdateHelper.refreshFromApiAsync(applicationContext) {
            if (params != null) {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Reschedule if interrupted before finishing
        return true
    }
}
