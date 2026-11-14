package com.remindly.app.data

/** One date + time a reminder should fire at. */
data class Occurrence(
    val id: String,
    var at: Long,
    var done: Boolean = false,
    var notified: Boolean = false
)

/** A task with one or more scheduled occurrences. */
data class Reminder(
    val id: String,
    var title: String,
    val createdAt: Long = System.currentTimeMillis(),
    var occurrences: MutableList<Occurrence> = mutableListOf(),
    /** Notification sound: null = system default, [SILENT_SOUND] = silent, anything else = ringtone URI. */
    var soundUri: String? = null
) {
    /** Closest occurrence that is still pending. */
    fun next(): Occurrence? = occurrences.filter { !it.done }.minByOrNull { it.at }

    val isDone: Boolean
        get() = occurrences.isNotEmpty() && occurrences.all { it.done }

    val pendingCount: Int
        get() = occurrences.count { !it.done }

    companion object {
        const val SILENT_SOUND = "silent"
    }
}
