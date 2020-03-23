package io.github.gmathi.novellibrary.service.util

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.gmathi.novellibrary.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SEND_NOTIFICATION) {
            val notification = intent.getParcelableExtra<Notification>(EXTRA_NOTIFICATION)
            val id = intent.getIntExtra(EXTRA_ID, Constants.nextNotificationId)
            if (notification == null)
                Log.e(TAG, "EXTRA_NOTIFICATION ($EXTRA_NOTIFICATION) was not provided!")
            else {
                val delay = intent.getLongExtra(EXTRA_INITIAL_DELAY, 0L)
                if (delay != 0L) {
                    CoroutineScope(Main).launch {
                        delay(delay)
                        NotificationManagerCompat.from(context).notify(id, notification)
                    }
                } else
                    NotificationManagerCompat.from(context).notify(id, notification)
            }
        }
    }

    companion object {
        const val TAG = "NotificationReceiver"

        const val ACTION_SEND_NOTIFICATION = "intent.action.SEND_NOTIFICATION"

        const val EXTRA_NOTIFICATION = "notification_parcel"
        const val EXTRA_ID = "notification_id"
        const val EXTRA_INITIAL_DELAY = "notification_initial_delay"
    }

}