package com.remindly.app.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.remindly.app.R
import com.remindly.app.alarm.AlarmScheduler
import com.remindly.app.data.Occurrence
import com.remindly.app.data.Reminder
import com.remindly.app.data.ReminderStore
import com.remindly.app.databinding.ActivityEditBinding
import com.remindly.app.databinding.DialogAddWhenBinding
import com.remindly.app.databinding.DialogTimeInputBinding
import com.remindly.app.databinding.ItemOccurrenceBinding
import com.remindly.app.notify.Notifications
import com.remindly.app.util.TimeFmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private var existing: Reminder? = null
    private var soundUri: String? = null
    private val working = ArrayList<Occurrence>()

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val picked = IntentCompat.getParcelableExtra(
                data,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            soundUri = when {
                picked == null -> Reminder.SILENT_SOUND
                picked == default -> null
                else -> picked.toString()
            }
            updateSoundLabel()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) {
                save()
                true
            } else {
                false
            }
        }

        existing = ReminderStore.get(intent.getStringExtra(MainActivity.EXTRA_REMINDER_ID))
        binding.toolbar.title =
            getString(if (existing == null) R.string.new_reminder else R.string.edit_reminder)

        existing?.let { reminder ->
            binding.titleEdit.setText(reminder.title)
            working.addAll(reminder.occurrences.map { it.copy() })
            binding.deleteButton.isVisible = true
            soundUri = reminder.soundUri
        }

        binding.addWhenButton.setOnClickListener { showAddDialog() }
        binding.soundButton.setOnClickListener { chooseSound() }
        binding.deleteButton.setOnClickListener { confirmDelete() }

        renderOccurrences()
        updateSoundLabel()
        if (existing == null) {
            binding.titleEdit.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun renderOccurrences() {
        working.sortBy { it.at }
        binding.occurrencesContainer.removeAllViews()
        binding.emptyDates.isVisible = working.isEmpty()

        val now = System.currentTimeMillis()
        working.forEach { occurrence ->
            val row = ItemOccurrenceBinding.inflate(
                layoutInflater,
                binding.occurrencesContainer,
                false
            )
            row.dateLabel.text = TimeFmt.day(occurrence.at)
            row.timeLabel.text = TimeFmt.time(occurrence.at)
            row.passed.isVisible = occurrence.at < now
            row.root.setOnClickListener { changeTime(occurrence) }
            row.remove.setOnClickListener {
                working.remove(occurrence)
                renderOccurrences()
            }
            binding.occurrencesContainer.addView(row.root)
        }
    }

    private fun changeTime(occurrence: Occurrence) {
        val zoned = Instant.ofEpochMilli(occurrence.at).atZone(ZoneId.systemDefault())
        showTimeInput(
            title = getString(R.string.change_time),
            initial = zoned.toLocalTime(),
            positive = R.string.time_set
        ) { time ->
            val index = working.indexOf(occurrence)
            if (index >= 0) {
                val at = zoned.toLocalDate()
                    .atTime(time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                working[index] = occurrence.copy(at = at, notified = false)
                renderOccurrences()
            }
        }
    }

    private fun showAddDialog() {
        val view = DialogAddWhenBinding.inflate(layoutInflater)
        var date = LocalDate.now()
        val times = mutableListOf(defaultTime())

        view.dateButton.text = TimeFmt.dateLong(date)
        view.dateButton.setOnClickListener {
            JalaliDatePicker.show(this, date) { picked ->
                date = picked
                view.dateButton.text = TimeFmt.dateLong(date)
            }
        }

        view.addTimeButton.setOnClickListener {
            val base = times.lastOrNull() ?: defaultTime()
            showTimeInput(
                title = getString(R.string.add_time),
                initial = base,
                positive = R.string.add
            ) { time ->
                if (!times.contains(time)) times.add(time)
                renderTimeChips(view.timeChips, times)
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_date_time)
            .setView(view.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val added = addOccurrences(date, times)
                if (added == 0) {
                    Toast.makeText(this, R.string.no_times, Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        renderTimeChips(view.timeChips, times)
    }

    private fun renderTimeChips(group: ChipGroup, times: MutableList<LocalTime>) {
        group.removeAllViews()
        times.sorted().forEach { time ->
            val chip = Chip(this).apply {
                text = TimeFmt.shortTime(time.hour, time.minute)
                isCloseIconVisible = true
                isCheckable = false
                setOnClickListener {
                    showTimeInput(
                        title = getString(R.string.change_time),
                        initial = time,
                        positive = R.string.time_set
                    ) { updated ->
                        val index = times.indexOf(time)
                        if (index >= 0) {
                            times.removeAt(index)
                            if (!times.contains(updated)) times.add(updated)
                            times.sort()
                        }
                        renderTimeChips(group, times)
                    }
                }
                setOnCloseIconClickListener {
                    times.remove(time)
                    renderTimeChips(group, times)
                }
            }
            group.addView(chip)
        }
    }

    private fun addOccurrences(date: LocalDate, times: List<LocalTime>): Int {
        if (times.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        var added = 0
        times.forEach { time ->
            val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (working.none { it.at == at }) {
                working.add(Occurrence(id = UUID.randomUUID().toString(), at = at))
                added++
            }
        }
        renderOccurrences()
        return added
    }

    private fun save() {
        val title = binding.titleEdit.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.titleInput.error = getString(R.string.title_required)
            binding.titleEdit.requestFocus()
            return
        }
        binding.titleInput.error = null
        if (working.isEmpty()) {
            Toast.makeText(this, R.string.no_times, Toast.LENGTH_SHORT).show()
            return
        }

        val reminder = existing ?: Reminder(id = UUID.randomUUID().toString(), title = title)
        val removed = reminder.occurrences.filter { old -> working.none { it.id == old.id } }
        reminder.title = title
        reminder.soundUri = soundUri
        reminder.occurrences = working.sortedBy { it.at }.toMutableList()

        ReminderStore.upsert(reminder)
        removed.forEach {
            AlarmScheduler.cancelOccurrence(this, it.id)
            Notifications.cancel(this, reminder.id, it.id)
        }
        AlarmScheduler.syncAll(this)
        Toast.makeText(this, R.string.reminder_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val reminder = existing ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_reminder)
            .setMessage(R.string.delete_reminder_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                reminder.occurrences.forEach {
                    AlarmScheduler.cancelOccurrence(this, it.id)
                    Notifications.cancel(this, reminder.id, it.id)
                }
                ReminderStore.delete(reminder.id)
                finish()
            }
            .show()
    }

    private fun defaultTime(): LocalTime {
        val now = LocalTime.now()
        val next = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        return if (next.isAfter(now)) next else LocalTime.of(23, 59)
    }

    private fun showTimeInput(
        title: String,
        initial: LocalTime,
        positive: Int,
        onSet: (LocalTime) -> Unit
    ) {
        val view = DialogTimeInputBinding.inflate(layoutInflater)
        view.timeInput.setText(TimeFmt.shortTime(initial.hour, initial.minute))
        view.timeInput.setSelection(0, view.timeInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(view.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positive, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val parsed = parseTime(view.timeInput.text?.toString())
                if (parsed == null) {
                    view.timeLayout.error = getString(R.string.time_format_error)
                    view.timeInput.requestFocus()
                } else {
                    view.timeLayout.error = null
                    onSet(parsed)
                    dialog.dismiss()
                }
            }
            view.timeInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    positiveButton.performClick()
                    true
                } else {
                    false
                }
            }
            view.timeInput.requestFocus()
            view.timeInput.post {
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(view.timeInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun parseTime(raw: String?): LocalTime? {
        val text = raw?.trim()?.uppercase(Locale.US) ?: return null
        val match = TIME_PATTERN.matchEntire(text) ?: return null
        val hour12 = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val suffix = match.groupValues[3]
        if (hour12 !in 0..12 || minute !in 0..59) return null
        var hour = hour12 % 12
        if (suffix == "P") hour += 12
        return LocalTime.of(hour, minute)
    }

    private fun chooseSound() {
        val options = arrayOf(
            getString(R.string.sound_default),
            getString(R.string.sound_silent),
            getString(R.string.sound_choose)
        )
        val current = when {
            soundUri == null -> 0
            soundUri == Reminder.SILENT_SOUND -> 1
            else -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sound)
            .setSingleChoiceItems(options, current) { dialog, which ->
                when (which) {
                    0 -> {
                        soundUri = null
                        updateSoundLabel()
                        dialog.dismiss()
                    }
                    1 -> {
                        soundUri = Reminder.SILENT_SOUND
                        updateSoundLabel()
                        dialog.dismiss()
                    }
                    2 -> {
                        dialog.dismiss()
                        openRingtonePicker()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openRingtonePicker() {
        val existing = when (val current = soundUri) {
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Reminder.SILENT_SOUND -> null
            else -> Uri.parse(current)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_choose))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        runCatching { ringtonePicker.launch(intent) }
            .onFailure {
                Toast.makeText(this, R.string.sound_picker_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSoundLabel() {
        binding.soundButton.text = soundLabel()
    }

    private fun soundLabel(): String = when (val current = soundUri) {
        null -> getString(R.string.sound_default)
        Reminder.SILENT_SOUND -> getString(R.string.sound_silent)
        else -> runCatching {
            RingtoneManager.getRingtone(this, Uri.parse(current))?.getTitle(this)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: getString(R.string.sound_custom)
    }

    companion object {
        private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{1,2})\s*([AP])\.?M\.?$""")
    }
}
