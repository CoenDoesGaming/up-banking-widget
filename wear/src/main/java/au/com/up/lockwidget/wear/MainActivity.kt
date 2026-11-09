package au.com.up.lockwidget.wear

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.wearable.Wearable

/**
 * Two (or three, with 2Up) page watch app. Renders straight from locally stored data so it opens
 * instantly, then asks the phone for a fresh sync in the background.
 */
class MainActivity : ComponentActivity() {

    private val detail = mutableStateOf<DetailStore.Detail?>(null)
    private val snapshot = mutableStateOf<SnapshotStore.Snapshot?>(null)
    private val syncStatus = mutableStateOf(SyncStatus.IDLE)
    private val handler = Handler(Looper.getMainLooper())
    private val stopRefreshing = Runnable {
        if (syncStatus.value == SyncStatus.REFRESHING) syncStatus.value = SyncStatus.IDLE
    }

    // Held as a field: SharedPreferences keeps only a weak reference to listeners
    private val detailListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DetailStore.KEY_JSON) {
            reload()
            syncStatus.value = SyncStatus.IDLE
            handler.removeCallbacks(stopRefreshing)
        }
    }
    private val snapshotListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        snapshot.value = SnapshotStore.load(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reload()
        setContent {
            UpWearApp(
                detail = detail.value,
                snapshot = snapshot.value,
                syncStatus = syncStatus.value
            )
        }
        SnapshotListenerService.requestAllComplicationUpdates(this)
    }

    override fun onStart() {
        super.onStart()
        DetailStore.prefs(this).registerOnSharedPreferenceChangeListener(detailListener)
        SnapshotStore.prefs(this).registerOnSharedPreferenceChangeListener(snapshotListener)
        reload()
        requestPhoneRefresh()
    }

    override fun onStop() {
        DetailStore.prefs(this).unregisterOnSharedPreferenceChangeListener(detailListener)
        SnapshotStore.prefs(this).unregisterOnSharedPreferenceChangeListener(snapshotListener)
        handler.removeCallbacks(stopRefreshing)
        super.onStop()
    }

    private fun reload() {
        detail.value = DetailStore.load(this)
        snapshot.value = SnapshotStore.load(this)
    }

    /** One-off message; the phone replies by pushing a fresh data item. */
    private fun requestPhoneRefresh() {
        syncStatus.value = SyncStatus.REFRESHING
        handler.removeCallbacks(stopRefreshing)
        handler.postDelayed(stopRefreshing, 20_000L)
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    syncStatus.value = SyncStatus.NO_PHONE
                    return@addOnSuccessListener
                }
                val messages = Wearable.getMessageClient(this)
                nodes.forEach { messages.sendMessage(it.id, DetailStore.PATH_REFRESH, null) }
            }
            .addOnFailureListener { syncStatus.value = SyncStatus.NO_PHONE }
    }
}

enum class SyncStatus { IDLE, REFRESHING, NO_PHONE }
