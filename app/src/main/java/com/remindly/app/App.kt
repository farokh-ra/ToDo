package com.remindly.app

import android.app.Application
import com.remindly.app.alarm.AlarmScheduler
import com.remindly.app.data.ReminderStore
import com.remindly.app.notify.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderStore.init(this)
        Notifications.ensureChannels(this)
        AlarmScheduler.syncAll(this)
    }
}
