package au.com.up.lockwidget

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Handles "refresh now" requests sent by the watch app when it opens. */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDetailSync.PATH_REFRESH) return
        // Runs on a background thread, so a blocking fetch is fine here
        WearDetailSync.sync(applicationContext, accountsData = null, force = true)
    }
}
