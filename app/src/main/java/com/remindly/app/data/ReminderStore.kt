package com.remindly.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Tiny, dependency-free persistence layer. The whole list is a few KB of JSON,
 * so every write is a single small file replace (fast, no database service).
 */
object ReminderStore {

    private const val TAG = "ReminderStore"
    private const val FILE_NAME = "reminders.json"

    private var appContext: Context? = null
    private val items = ArrayList<Reminder>()
    private val listeners = ArrayList<() -> Unit>()
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        load()
        loaded = true
    }

    @Synchronized
    fun all(): List<Reminder> = items.toList()

    @Synchronized
    fun get(id: String?): Reminder? {
        if (id == null) return null
        return items.firstOrNull { it.id == id }
    }

    @Synchronized
    fun upsert(reminder: Reminder) {
        val index = items.indexOfFirst { it.id == reminder.id }
        if (index >= 0) items[index] = reminder else items.add(reminder)
        commit()
    }

    @Synchronized
    fun delete(id: String) {
        items.removeAll { it.id == id }
        commit()
    }

    @Synchronized
    fun setOccurrenceDone(reminderId: String, occurrenceId: String, done: Boolean) {
        val reminder = items.firstOrNull { it.id == reminderId } ?: return
        val occurrence = reminder.occurrences.firstOrNull { it.id == occurrenceId } ?: return
        occurrence.done = done
        if (!done) occurrence.notified = false
        commit()
    }

    @Synchronized
    fun setAllDone(reminderId: String, done: Boolean) {
        val reminder = items.firstOrNull { it.id == reminderId } ?: return
        reminder.occurrences.forEach {
            it.done = done
            if (!done) it.notified = false
        }
        commit()
    }

    @Synchronized
    fun markNotified(reminderId: String, occurrenceId: String) {
        val reminder = items.firstOrNull { it.id == reminderId } ?: return
        val occurrence = reminder.occurrences.firstOrNull { it.id == occurrenceId } ?: return
        if (occurrence.notified) return
        occurrence.notified = true
        commit()
    }

    @Synchronized
    fun markNotified(pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return
        var changed = false
        pairs.forEach { (reminderId, occurrenceId) ->
            val reminder = items.firstOrNull { it.id == reminderId } ?: return@forEach
            val occurrence = reminder.occurrences.firstOrNull { it.id == occurrenceId } ?: return@forEach
            if (!occurrence.notified) {
                occurrence.notified = true
                changed = true
            }
        }
        if (changed) commit()
    }

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun commit() {
        save()
        listeners.toList().forEach { runCatching { it() } }
    }

    private fun load() {
        val context = appContext ?: return
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val array = root.optJSONArray("reminders") ?: return
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val occurrenceArray = json.optJSONArray("occurrences") ?: JSONArray()
                val occurrences = ArrayList<Occurrence>(occurrenceArray.length())
                for (j in 0 until occurrenceArray.length()) {
                    val occurrenceJson = occurrenceArray.optJSONObject(j) ?: continue
                    val at = occurrenceJson.optLong("at")
                    if (at <= 0L) continue
                    occurrences.add(
                        Occurrence(
                            id = occurrenceJson.optString("id").ifBlank { UUID.randomUUID().toString() },
                            at = at,
                            done = occurrenceJson.optBoolean("done", false),
                            notified = occurrenceJson.optBoolean("notified", false)
                        )
                    )
                }
                items.add(
                    Reminder(
                        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = json.optString("title"),
                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                        occurrences = occurrences,
                        soundUri = json.optString("soundUri").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not read reminders", t)
        }
    }

    private fun save() {
        val context = appContext ?: return
        try {
            val array = JSONArray()
            items.forEach { reminder ->
                val occurrenceArray = JSONArray()
                reminder.occurrences.forEach { occurrence ->
                    occurrenceArray.put(
                        JSONObject()
                            .put("id", occurrence.id)
                            .put("at", occurrence.at)
                            .put("done", occurrence.done)
                            .put("notified", occurrence.notified)
                    )
                }
                array.put(
                    JSONObject()
                        .put("id", reminder.id)
                        .put("title", reminder.title)
                        .put("createdAt", reminder.createdAt)
                        .put("soundUri", reminder.soundUri ?: "")
                        .put("occurrences", occurrenceArray)
                )
            }
            val text = JSONObject()
                .put("version", 1)
                .put("reminders", array)
                .toString()

            val directory = context.filesDir
            val temp = File(directory, "$FILE_NAME.tmp")
            val target = File(directory, FILE_NAME)
            temp.writeText(text, Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not save reminders", t)
        }
    }
}
