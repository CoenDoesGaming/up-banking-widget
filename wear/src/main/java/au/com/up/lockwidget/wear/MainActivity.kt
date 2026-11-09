package au.com.up.lockwidget.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Minimal status screen: shows when the phone last synced data to the watch. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val snap = SnapshotStore.load(this)
        val status = if (snap.updatedAt > 0L) {
            "Last sync from phone:\n" +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snap.updatedAt))
        } else {
            "Waiting for phone...\nOpen the Up widget app on your phone and tap Save & Sync."
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(40, 40, 40, 40)
        }
        layout.addView(TextView(this).apply {
            text = "Up"
            textSize = 22f
            setTextColor(0xFFFF7A64.toInt())
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "Add the Up complications from your watch face editor.\n\n$status"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        setContentView(layout)

        SnapshotListenerService.requestAllComplicationUpdates(this)
    }
}
