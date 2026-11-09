package au.com.up.lockwidget.wear

import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.wearable.Wearable

/**
 * Two (or three, with 2Up) page watch app. Renders straight from locally stored data so it opens
 * instantly, then asks the phone for a fresh sync in the background.
 */
class MainActivity : ComponentActivity() {

    private val detail = mutableStateOf<DetailStore.Detail?>(null)
    private val snapshot = mutableStateOf<SnapshotStore.Snapshot?>(null)

    // "Refreshing…" is derived from these timestamps by the footer's own clock, so it always
    // clears after REFRESH_TIMEOUT_MS even if no reply arrives (no Handler callback to miss).
    private val refreshRequestedAt = mutableLongStateOf(0L)
    private val refreshAnsweredAt = mutableLongStateOf(0L)
    private val phoneMissing = mutableStateOf(false)

    // Held as fields: SharedPreferences keeps only weak references to listeners
    private val detailListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DetailStore.KEY_JSON) {
            detail.value = DetailStore.load(this)
            refreshAnsweredAt.longValue = SystemClock.elapsedRealtime()
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
                syncStatus = SyncState(
                    requestedAt = refreshRequestedAt.longValue,
                    answeredAt = refreshAnsweredAt.longValue,
                    phoneMissing = phoneMissing.value
                )
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
        super.onStop()
    }

    private fun reload() {
        detail.value = DetailStore.load(this)
        snapshot.value = SnapshotStore.load(this)
    }

    /** One-off message; the phone replies by pushing a fresh data item. */
    private fun requestPhoneRefresh() {
        phoneMissing.value = false
        refreshRequestedAt.longValue = SystemClock.elapsedRealtime()
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    phoneMissing.value = true
                    return@addOnSuccessListener
                }
                val messages = Wearable.getMessageClient(this)
                nodes.forEach { messages.sendMessage(it.id, DetailStore.PATH_REFRESH, null) }
            }
            .addOnFailureListener { phoneMissing.value = true }
    }
}

/** Times are [SystemClock.elapsedRealtime] values. */
data class SyncState(val requestedAt: Long, val answeredAt: Long, val phoneMissing: Boolean)

const val REFRESH_TIMEOUT_MS = 15_000L
