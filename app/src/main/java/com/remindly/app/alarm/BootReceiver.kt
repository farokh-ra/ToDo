package com.remindly.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remindly.app.data.ReminderStore
import com.remindly.app.notify.Notifications

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        ReminderStore.init(app)
        Notifications.ensureChannels(app)
        AlarmScheduler.syncAll(app)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Notifications.notifyMissedSince(app)
        }
    }
}
