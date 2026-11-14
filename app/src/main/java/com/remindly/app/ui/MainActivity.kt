package com.remindly.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.remindly.app.R
import com.remindly.app.alarm.AlarmScheduler
import com.remindly.app.data.Reminder
import com.remindly.app.data.ReminderStore
import com.remindly.app.databinding.ActivityMainBinding
import com.remindly.app.notify.Notifications
import com.remindly.app.util.TimeFmt
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val PREFS = "remindly_prefs"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ReminderAdapter
    private val storeListener: () -> Unit = { refresh() }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Remindly)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_reliability) {
                openReliability()
                true
            } else {
                false
            }
        }

        adapter = ReminderAdapter(
            onOpen = { openEditor(it.id) },
            onToggle = { toggleNext(it) },
            onMenu = { reminder, anchor -> showItemMenu(reminder, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = null

        binding.fab.setOnClickListener { openEditor(null) }
        binding.exactBanner.setOnClickListener { openReliability() }
        binding.bannerAction.setOnClickListener { openReliability() }

        maybeRequestNotifications()
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ReminderStore.addListener(storeListener)
    }

    override fun onResume() {
        super.onResume()
        ReminderStore.init(this)
        refresh()
        updateBanner()
    }

    override fun onStop() {
        ReminderStore.removeListener(storeListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_REMINDER_ID) ?: return
        intent.removeExtra(EXTRA_REMINDER_ID)
        if (ReminderStore.get(id) != null) openEditor(id)
    }

    private fun refresh() {
        val all = ReminderStore.all()
        val active = all
            .filter { !it.isDone && it.next() != null }
            .sortedBy { it.next()!!.at }
        val done = all
            .filter { it.isDone }
            .sortedByDescending { it.occurrences.maxOfOrNull { occurrence -> occurrence.at } ?: 0L }

        val rows = ArrayList<ReminderAdapter.Row>()
        if (active.isNotEmpty()) {
            rows.add(
                ReminderAdapter.Row.Header(
                    "${getString(R.string.upcoming).uppercase(Locale.ENGLISH)} · ${active.size}"
                )
            )
            active.forEach { rows.add(ReminderAdapter.Row.Item(it)) }
        }
        if (done.isNotEmpty()) {
            rows.add(
                ReminderAdapter.Row.Header(
                    "${getString(R.string.done).uppercase(Locale.ENGLISH)} · ${done.size}"
                )
            )
            done.forEach { rows.add(ReminderAdapter.Row.Item(it)) }
        }
        adapter.submit(rows)

        val empty = all.isEmpty()
        binding.emptyState.isVisible = empty
        binding.recycler.isVisible = !empty
        binding.summary.isVisible = !empty

        val next = active.firstOrNull()?.next()
        binding.summary.text = if (next == null) {
            getString(R.string.all_clear)
        } else {
            getString(R.string.next_label, TimeFmt.dateTime(next.at), TimeFmt.countdown(next.at))
        }
    }

    private fun toggleNext(reminder: Reminder) {
        val next = reminder.next()
        if (next != null) {
            ReminderStore.setOccurrenceDone(reminder.id, next.id, true)
            AlarmScheduler.cancelOccurrence(this, next.id)
            Notifications.cancel(this, reminder.id, next.id)
        } else {
            ReminderStore.setAllDone(reminder.id, false)
            AlarmScheduler.syncAll(this)
        }
    }

    private fun showItemMenu(reminder: Reminder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_item, popup.menu)
        popup.menu.findItem(R.id.action_mark_all).title =
            getString(if (reminder.isDone) R.string.undo_all else R.string.mark_all_done)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    openEditor(reminder.id)
                    true
                }
                R.id.action_mark_all -> {
                    setAllDone(reminder, !reminder.isDone)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(reminder)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setAllDone(reminder: Reminder, done: Boolean) {
        if (done) {
            reminder.occurrences.forEach {
                AlarmScheduler.cancelOccurrence(this, it.id)
                Notifications.cancel(this, reminder.id, it.id)
            }
        }
        ReminderStore.setAllDone(reminder.id, done)
        AlarmScheduler.syncAll(this)
    }

    private fun confirmDelete(reminder: Reminder) {
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
            }
            .show()
    }

    private fun openEditor(reminderId: String?) {
        startActivity(
            Intent(this, EditActivity::class.java).apply {
                if (reminderId != null) putExtra(EXTRA_REMINDER_ID, reminderId)
            }
        )
    }

    private fun openReliability() {
        startActivity(Intent(this, ReliabilityActivity::class.java))
    }

    private fun updateBanner() {
        val exactOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        binding.exactBanner.isVisible = exactOff
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
