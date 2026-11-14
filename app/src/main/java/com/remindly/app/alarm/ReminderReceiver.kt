package com.remindly.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remindly.app.data.ReminderStore
import com.remindly.app.notify.Notifications

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.remindly.app.action.FIRE"
        const val ACTION_DONE = "com.remindly.app.action.DONE"
        const val ACTION_SNOOZE = "com.remindly.app.action.SNOOZE"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
        const val EXTRA_SNOOZE = "extra_snooze"
        const val EXTRA_TEST = "extra_test"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        ReminderStore.init(ctx)

        when (intent.action) {
            ACTION_FIRE -> handleFire(ctx, intent)
            ACTION_DONE -> handleDone(ctx, intent)
            ACTION_SNOOZE -> handleSnooze(ctx, intent)
        }
    }

    private fun handleFire(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            Notifications.notifyTest(ctx)
            return
        }
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        val reminder = ReminderStore.get(reminderId) ?: return
        val occurrence = reminder.occurrences.firstOrNull { it.id == occurrenceId } ?: return
        if (occurrence.done) return

        val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        if (!isSnooze) {
            if (occurrence.notified) return
            ReminderStore.markNotified(reminderId, occurrenceId)
        }
        Notifications.notifyReminder(ctx, reminder, occurrence)
        // Keep the next occurrences armed (cheap, and heals gaps after reboots).
        AlarmScheduler.syncAll(ctx)
    }

    private fun handleDone(ctx: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        ReminderStore.setOccurrenceDone(reminderId, occurrenceId, true)
        AlarmScheduler.cancelOccurrence(ctx, occurrenceId)
        Notifications.cancel(ctx, reminderId, occurrenceId)
        AlarmScheduler.syncAll(ctx)
    }

    private fun handleSnooze(ctx: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        Notifications.cancel(ctx, reminderId, occurrenceId)
        AlarmScheduler.snooze(ctx, reminderId, occurrenceId, 10)
    }
}
