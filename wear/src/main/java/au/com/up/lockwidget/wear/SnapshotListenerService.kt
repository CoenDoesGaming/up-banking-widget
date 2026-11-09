package au.com.up.lockwidget.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives snapshots from the phone, caches them, and refreshes all active complications. */
class SnapshotListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path == DetailStore.PATH_DETAIL) {
                val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload")
                if (bytes != null) runCatching { DetailStore.saveCompressed(this, bytes) }
                continue
            }
            if (event.dataItem.uri.path != "/up/snapshot") continue

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            SnapshotStore.save(
                this,
                SnapshotStore.Snapshot(
                    spendingName = map.getString("spendingName", "Spending"),
                    spendingBalance = map.getString("spendingBalance", "--"),
                    activityTxDesc = map.getString("activityTxDesc", ""),
                    activityTxAmount = map.getString("activityTxAmount", ""),
                    saverTitle = map.getString("saverTitle", "Savers"),
                    saverBalance = map.getString("saverBalance", "--"),
                    saverTxDesc = map.getString("saverTxDesc", ""),
                    saverTxAmount = map.getString("saverTxAmount", ""),
                    categoryName = map.getString("categoryName", "Category"),
                    categoryCode = map.getString("categoryCode", "CAT"),
                    categoryLeftWhole = map.getString("categoryLeftWhole", "--"),
                    categoryLeftLabel = map.getString("categoryLeftLabel", "-- left"),
                    categoryProgress = map.getFloat("categoryProgress", 0f),
                    updatedAt = map.getLong("updatedAt", 0L)
                )
            )
            requestAllComplicationUpdates(this)
        }
    }

    companion object {
        fun requestAllComplicationUpdates(context: Context) {
            val services = listOf(
                ActivityComplicationService::class.java,
                SaversComplicationService::class.java,
                CategoryComplicationService::class.java
            )
            for (service in services) {
                runCatching {
                    ComplicationDataSourceUpdateRequester
                        .create(context, ComponentName(context, service))
                        .requestUpdateAll()
                }
            }
        }
    }
}
