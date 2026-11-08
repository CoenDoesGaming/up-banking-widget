package au.com.up.lockwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.PowerManager
import android.widget.RemoteViews
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object WidgetUpdateHelper {
    const val ACTION_REFRESH_WIDGET = "au.com.up.lockwidget.ACTION_REFRESH_WIDGET"
    private val executor = Executors.newSingleThreadExecutor()
    private val isSyncInFlight = AtomicBoolean(false)

    fun renderAllWidgetsFromCache(context: Context) {
        AutoSyncScheduler.ensureScheduled(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Activity (Spending) Widgets
        val activitySmallIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpLockSmallWidgetProvider::class.java)
        )
        for (id in activitySmallIds) {
            updateSmallWidget(context, appWidgetManager, id)
        }

        val activityTinyIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpLockTinyWidgetProvider::class.java)
        )
        for (id in activityTinyIds) {
            updateTinyWidget(context, appWidgetManager, id)
        }

        // Savers Widgets
        val saversSmallIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpSaversSmallWidgetProvider::class.java)
        )
        for (id in saversSmallIds) {
            updateSaversSmallWidget(context, appWidgetManager, id)
        }

        val saversTinyIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpSaversTinyWidgetProvider::class.java)
        )
        for (id in saversTinyIds) {
            updateSaversTinyWidget(context, appWidgetManager, id)
        }

        // Category Tracker Widgets
        val categorySmallIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpCategorySmallWidgetProvider::class.java)
        )
        for (id in categorySmallIds) {
            updateCategorySmallWidget(context, appWidgetManager, id)
        }

        val categoryTinyIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, UpCategoryTinyWidgetProvider::class.java)
        )
        for (id in categoryTinyIds) {
            updateCategoryTinyWidget(context, appWidgetManager, id)
        }
    }

    fun refreshFromApiAsync(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult? = null,
        onComplete: ((Result<UpApiClient.SyncSummary>) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        renderAllWidgetsFromCache(appContext)

        if (!isSyncInFlight.compareAndSet(false, true) && onComplete == null) {
            pendingResult?.finish()
            return
        }

        executor.execute {
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UpLockWidget:ApiSyncWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12_000L)
            }
            try {
                val result = UpApiClient.syncAll(appContext)
                result.onSuccess {
                    renderAllWidgetsFromCache(appContext)
                }
                onComplete?.invoke(result)
            } finally {
                isSyncInFlight.set(false)
                AutoSyncScheduler.ensureScheduled(appContext)
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                pendingResult?.finish()
            }
        }
    }

    // --- 1. Activity (Spending + Recent Transaction) ---
    fun updateSmallWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val balanceFull = UpPreferences.getBalanceFull(context)
        val txDesc = UpPreferences.getActivityTxDesc(context)
        val txAmount = UpPreferences.getActivityTxAmount(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_lock_small)
        views.setTextViewText(R.id.widget_balance, balanceFull)
        views.setTextViewText(R.id.widget_tx_desc, txDesc)
        views.setTextViewText(R.id.widget_tx_amount, txAmount)
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_balance, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_tx_desc, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_tx_amount, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun updateTinyWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val balanceCompact = UpPreferences.getBalanceCompact(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_lock_tiny)
        views.setTextViewText(R.id.widget_balance, balanceCompact)
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_up_badge, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_balance, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 2. Savers (Balance + Recent Saver Transaction) ---
    fun updateSaversSmallWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val title = UpPreferences.getSaverTitle(context)
        val balance = UpPreferences.getSaverBalanceFull(context)
        val txDesc = UpPreferences.getSaverTxDesc(context)
        val txAmount = UpPreferences.getSaverTxAmount(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_savers_small)
        views.setTextViewText(R.id.widget_saver_title, title)
        views.setTextViewText(R.id.widget_saver_balance, balance)
        views.setTextViewText(R.id.widget_saver_tx_desc, txDesc)
        views.setTextViewText(R.id.widget_saver_tx_amount, txAmount)
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_title, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_balance, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_tx_desc, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_tx_amount, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun updateSaversTinyWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val title = UpPreferences.getSaverTitle(context)
        val balance = UpPreferences.getSaverBalanceWhole(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_savers_tiny)
        views.setTextViewText(R.id.widget_saver_title, title)
        views.setTextViewText(R.id.widget_saver_balance, balance)
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_title, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_saver_balance, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 3. Category Tracker ---
    fun updateCategorySmallWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val categoryName = UpPreferences.getCategoryName(context)
        val leftLabel = UpPreferences.getCategoryLeftLabel(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_category_small)
        views.setTextViewText(R.id.widget_category_name, categoryName)
        views.setTextViewText(R.id.widget_category_left, leftLabel)
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_category_name, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_category_left, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun updateCategoryTinyWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val code = UpPreferences.getCategoryCode(context)
        val leftWhole = UpPreferences.getCategoryLeftWhole(context)
        val progress = UpPreferences.getCategoryProgress(context)
        val clickIntent = createOpenUpAppPendingIntent(context, appWidgetId)

        val views = RemoteViews(context.packageName, R.layout.widget_category_tiny)
        views.setTextViewText(R.id.widget_category_code, code)
        views.setTextViewText(R.id.widget_category_amount, leftWhole)
        views.setImageViewBitmap(R.id.widget_category_ring, createRingBitmap(progress))
        views.setOnClickPendingIntent(android.R.id.background, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_category_ring, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_category_code, clickIntent)
        views.setOnClickPendingIntent(R.id.widget_category_amount, clickIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun createRingBitmap(progress: Float): Bitmap {
        val sizePx = 192
        val strokePx = 18f
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inset = strokePx / 2f + 6f
        val oval = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = 0x4DFFFFFF
        }
        canvas.drawOval(oval, trackPaint)

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0.01f) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawArc(oval, -90f, 360f * clamped, false, arcPaint)
        }

        return bitmap
    }

    private fun createOpenUpAppPendingIntent(
        context: Context,
        appWidgetId: Int
    ): PendingIntent {
        val intent = Intent(context, LaunchUpActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
