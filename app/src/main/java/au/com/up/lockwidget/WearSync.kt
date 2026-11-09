package au.com.up.lockwidget

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Pushes the already-synced widget data (never the Personal Access Token) to a paired
 * Wear OS watch over the Wear Data Layer so watch complications can display it.
 */
object WearSync {
    const val PATH_SNAPSHOT = "/up/snapshot"

    fun pushSnapshot(context: Context) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_SNAPSHOT)
            val map = request.dataMap
            map.putString("spendingName", UpPreferences.getAccountName(context))
            map.putString("spendingBalance", UpPreferences.getBalanceFull(context))
            map.putString("activityTxDesc", UpPreferences.getActivityTxDesc(context))
            map.putString("activityTxAmount", UpPreferences.getActivityTxAmount(context))
            map.putString("saverTitle", UpPreferences.getSaverTitle(context))
            map.putString("saverBalance", UpPreferences.getSaverBalanceWhole(context))
            map.putString("saverTxDesc", UpPreferences.getSaverTxDesc(context))
            map.putString("saverTxAmount", UpPreferences.getSaverTxAmount(context))
            map.putString("categoryName", UpPreferences.getCategoryName(context))
            map.putString("categoryCode", UpPreferences.getCategoryCode(context))
            map.putString("categoryLeftWhole", UpPreferences.getCategoryLeftWhole(context))
            map.putString("categoryLeftLabel", UpPreferences.getCategoryLeftLabel(context))
            map.putFloat("categoryProgress", UpPreferences.getCategoryProgress(context))
            // Watch app data (accounts, savers, recent transactions), gzipped JSON
            WearDetailSync.cachedPayload(context)?.let { map.putByteArray("detail", it) }
            // Changes every push so the watch is always notified, even if values are identical
            map.putLong("updatedAt", System.currentTimeMillis())

            Wearable.getDataClient(context.applicationContext)
                .putDataItem(request.asPutDataRequest().setUrgent())
        }
    }
}
