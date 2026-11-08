package au.com.up.lockwidget

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

class LaunchUpActivity : Activity() {

    companion object {
        const val UP_PACKAGE_NAME = "au.com.up.money"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Trigger a background widget refresh whenever the user taps a widget
        WidgetUpdateHelper.refreshFromApiAsync(applicationContext)

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        openUpAppAndFinish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        openUpAppAndFinish()
                    }
                }
            )
        } else {
            openUpAppAndFinish()
        }
    }

    private fun openUpAppAndFinish() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(UP_PACKAGE_NAME)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
                finish()
                return
            }

            // Fallback to Up deep link URI if package launch intent wasn't resolved
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("up://")).apply {
                setPackage(UP_PACKAGE_NAME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (deepLinkIntent.resolveActivity(packageManager) != null) {
                startActivity(deepLinkIntent)
                finish()
                return
            }

            // Final fallback: open our companion configuration screen
            Toast.makeText(this, "Up app not found, opening widget settings", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } catch (_: Exception) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } finally {
            finish()
        }
    }
}
