package com.remindly.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.remindly.app.data.ReminderStore
import com.remindly.app.ui.MainActivity

/**
 * Every reminder is handled by the system alarm clock (AlarmManager).
 * There is no service, no polling and no wakelock: the app costs nothing until a
 * reminder is due, and alarms wake the CPU only for that moment.
 */
object AlarmScheduler {

    private const val MAX_ALARMS = 400
    private const val TAG = "AlarmScheduler"

    fun syncAll(context: Context) {
        val ctx = context.applicationContext
        ReminderStore.init(ctx)
        val now = System.currentTimeMillis()
        val pending = ArrayList<Triple<String, String, Long>>()
        ReminderStore.all().forEach { reminder ->
            reminder.occurrences.forEach { occurrence ->
                if (!occurrence.done && occurrence.at > now) {
                    pending.add(Triple(reminder.id, occurrence.id, occurrence.at))
                }
            }
        }
        pending.sortBy { it.third }
        pending.take(MAX_ALARMS).forEach { (reminderId, occurrenceId, at) ->
            schedule(ctx, reminderId, occurrenceId, at, snooze = false)
        }
    }

    fun schedule(
        context: Context,
        reminderId: String,
        occurrenceId: String,
        at: Long,
        snooze: Boolean
    ) {
        val ctx = context.applicationContext
        val requestCode = if (snooze) ("snooze:$occurrenceId").hashCode() else occurrenceId.hashCode()
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            .putExtra(ReminderReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
            .putExtra(ReminderReceiver.EXTRA_SNOOZE, snooze)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(ctx, at, pendingIntent, reminderId)
    }

    fun snooze(context: Context, reminderId: String, occurrenceId: String, minutes: Int = 10) {
        schedule(
            context,
            reminderId,
            occurrenceId,
            System.currentTimeMillis() + minutes * 60_000L,
            snooze = true
        )
    }

    fun cancelOccurrence(context: Context, occurrenceId: String) {
        val ctx = context.applicationContext
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            occurrenceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ctx.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun scheduleTest(context: Context, seconds: Long = 10) {
        val ctx = context.applicationContext
        val at = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TEST, true)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            987_654,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(ctx, at, pendingIntent, null)
    }

    private fun setExact(context: Context, at: Long, pendingIntent: PendingIntent, reminderId: String?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (at <= System.currentTimeMillis()) return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (!canExact) {
                // Exact alarms are not allowed: the alarm clock API is still exact and
                // always permitted, so reminders stay on time.
                alarms.setAlarmClock(alarmClockInfo(context, at, reminderId), pendingIntent)
                Log.i(TAG, "scheduled via alarm clock, at=$at")
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
                Log.i(TAG, "scheduled exact, at=$at")
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "exact scheduling rejected (canExact=$canExact), falling back", security)
            runCatching {
                alarms.setAlarmClock(alarmClockInfo(context, at, reminderId), pendingIntent)
                Log.i(TAG, "scheduled via alarm clock fallback, at=$at")
            }.onFailure {
                runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent) }
                    .onSuccess { Log.w(TAG, "scheduled inexact fallback, at=$at") }
            }
        }
    }

    private fun alarmClockInfo(context: Context, at: Long, reminderId: String?): AlarmManager.AlarmClockInfo {
        val show = Intent(context, MainActivity::class.java).apply {
            if (reminderId != null) {
                putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showIntent = PendingIntent.getActivity(
            context,
            987_655,
            show,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return AlarmManager.AlarmClockInfo(at, showIntent)
    }
}
