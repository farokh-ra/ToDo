package com.remindly.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.remindly.app.R
import com.remindly.app.alarm.ReminderReceiver
import com.remindly.app.data.Occurrence
import com.remindly.app.data.Reminder
import com.remindly.app.data.ReminderStore
import com.remindly.app.ui.MainActivity
import com.remindly.app.util.TimeFmt

object Notifications {

    private const val CHANNEL_ID = "reminders_v1"
    private const val SILENT_CHANNEL_ID = "reminders_silent"
    private const val CUSTOM_CHANNEL_PREFIX = "reminders_sound_"
    private const val TEST_NOTIFICATION_ID = 900_001
    private const val MISSED_NOTIFICATION_ID = 900_002

    /**
     * Channels are immutable once created, so every distinct custom sound gets
     * its own channel. The default channel stays "reminders_v1"; silent alerts
     * use a dedicated silent channel.
     */
    fun ensureChannels(context: Context) {
        ensureDefaultChannel(context)
        ReminderStore.init(context)
        ReminderStore.all().forEach { reminder ->
            if (reminder.soundUri != null) ensureSoundChannel(context, reminder.soundUri)
        }
    }

    private fun ensureDefaultChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val manager = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_ID
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return CHANNEL_ID

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = baseChannel(context, CHANNEL_ID, "ToDo", "Alerts for scheduled reminders")
            .apply { setSound(sound, audioAttributes()) }
        manager.createNotificationChannel(channel)
        return CHANNEL_ID
    }

    private fun ensureSoundChannel(context: Context, soundUri: String?): String {
        if (soundUri == null) return ensureDefaultChannel(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID

        val manager = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_ID
        val silent = soundUri == Reminder.SILENT_SOUND
        val channelId = if (silent) {
            SILENT_CHANNEL_ID
        } else {
            CUSTOM_CHANNEL_PREFIX + Integer.toHexString(soundUri.hashCode())
        }
        if (manager.getNotificationChannel(channelId) != null) return channelId

        if (silent) {
            val created = runCatching {
                val channel = baseChannel(
                    context,
                    channelId,
                    "ToDo · ${context.getString(R.string.sound_silent)}",
                    "Silent alerts for scheduled reminders"
                )
                channel.setSound(null, null)
                manager.createNotificationChannel(channel)
            }.isSuccess
            return if (created) channelId else ensureDefaultChannel(context)
        }

        val uri = runCatching { Uri.parse(soundUri) }.getOrNull()
            ?: return ensureDefaultChannel(context)
        val soundName = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.sound_custom)

        val created = runCatching {
            val channel = baseChannel(
                context,
                channelId,
                "ToDo · $soundName",
                "Alerts for scheduled reminders using a custom sound"
            )
            channel.setSound(uri, audioAttributes())
            manager.createNotificationChannel(channel)
        }.isSuccess

        return if (created) channelId else ensureDefaultChannel(context)
    }

    private fun channelFor(context: Context, soundUri: String?): String =
        if (soundUri == null) ensureDefaultChannel(context) else ensureSoundChannel(context, soundUri)

    private fun baseChannel(
        context: Context,
        id: String,
        name: String,
        description: String
    ): NotificationChannel = NotificationChannel(
        id,
        name,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        this.description = description
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 150, 250)
        enableLights(true)
        lightColor = ContextCompat.getColor(context, R.color.primary)
        setShowBadge(true)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    }

    private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun notifyReminder(context: Context, reminder: Reminder, occurrence: Occurrence) {
        val id = notificationId(occurrence.id)
        val count = reminder.occurrences.count { !it.done }
        val body = TimeFmt.dateTime(occurrence.at)
        val extra = if (count > 1) "$count scheduled times remaining" else ""
        val channelId = channelFor(context, reminder.soundUri)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (extra.isEmpty()) body else "$body\n$extra"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setAutoCancel(true)
            .setWhen(occurrence.at)
            .setShowWhen(true)
            .setContentIntent(openIntent(context, reminder.id))
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.mark_done),
                actionIntent(context, ReminderReceiver.ACTION_DONE, reminder.id, occurrence.id, 11)
            )
            .addAction(
                R.drawable.ic_clock,
                context.getString(R.string.snooze_10),
                actionIntent(context, ReminderReceiver.ACTION_SNOOZE, reminder.id, occurrence.id, 12)
            )

        post(context, id, builder.build())
    }

    fun notifyTest(context: Context) {
        val channelId = ensureDefaultChannel(context)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
        post(context, TEST_NOTIFICATION_ID, builder.build())
    }

    /** Reminders that fell inside a powered-off window get one summary notification. */
    fun notifyMissedSince(context: Context) {
        val now = System.currentTimeMillis()
        val cutoff = now - 7L * 24L * 60L * 60L * 1000L
        val missed = ArrayList<Pair<Reminder, Occurrence>>()
        ReminderStore.all().forEach { reminder ->
            reminder.occurrences.forEach { occurrence ->
                if (!occurrence.done && !occurrence.notified && occurrence.at in cutoff until now) {
                    missed.add(reminder to occurrence)
                }
            }
        }
        if (missed.isEmpty()) return
        missed.sortBy { it.second.at }

        val shown = missed.takeLast(6)
        ReminderStore.markNotified(missed.map { it.first.id to it.second.id })

        ensureDefaultChannel(context)
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.missed_title, missed.size))
        shown.forEach { (reminder, occurrence) ->
            style.addLine("${TimeFmt.dateTime(occurrence.at)} · ${reminder.title}")
        }
        if (missed.size > shown.size) {
            style.setSummaryText("+${missed.size - shown.size} more")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.missed_title, missed.size))
            .setContentText(context.getString(R.string.missed_body))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
        post(context, MISSED_NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context, reminderId: String, occurrenceId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(notificationId(occurrenceId))
    }

    private fun notificationId(occurrenceId: String): Int =
        occurrenceId.hashCode() and 0x7fffffff

    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications are blocked until the user grants POST_NOTIFICATIONS.
        }
    }

    private fun openIntent(context: Context, reminderId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (reminderId != null) putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getActivity(
            context,
            if (reminderId == null) 1 else reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        reminderId: String,
        occurrenceId: String,
        salt: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            .putExtra(ReminderReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        return PendingIntent.getBroadcast(
            context,
            occurrenceId.hashCode() * 31 + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
