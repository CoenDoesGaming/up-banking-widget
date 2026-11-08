package au.com.up.lockwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class UpSaversTinyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            WidgetUpdateHelper.updateSaversTinyWidget(context, appWidgetManager, appWidgetId)
        }
        val pendingResult = goAsync()
        WidgetUpdateHelper.refreshFromApiAsync(context, pendingResult)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetUpdateHelper.updateSaversTinyWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdateHelper.ACTION_REFRESH_WIDGET) {
            val pendingResult = goAsync()
            WidgetUpdateHelper.refreshFromApiAsync(context, pendingResult)
        }
    }
}
