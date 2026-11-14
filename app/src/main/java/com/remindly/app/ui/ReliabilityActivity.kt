package com.remindly.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.remindly.app.R
import com.remindly.app.alarm.AlarmScheduler
import com.remindly.app.databinding.ActivityReliabilityBinding

class ReliabilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReliabilityBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { update() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReliabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.notifAction.setOnClickListener { requestNotifications() }
        binding.exactAction.setOnClickListener { requestExactAlarms() }
        binding.batteryAction.setOnClickListener { requestBatteryExemption() }
        binding.testButton.setOnClickListener {
            AlarmScheduler.scheduleTest(this, 10)
            Toast.makeText(this, R.string.test_arriving, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    private fun update() {
        val notificationsOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.notifStatus.text =
            getString(if (notificationsOn) R.string.notifications_on else R.string.notifications_off)
        binding.notifStatus.setTextColor(
            ContextCompat.getColor(this, if (notificationsOn) R.color.success else R.color.error)
        )
        binding.notifAction.text =
            getString(if (notificationsOn) R.string.open_settings else R.string.allow)

        val exactOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }
        binding.exactStatus.text = getString(if (exactOn) R.string.exact_on else R.string.exact_off)
        binding.exactStatus.setTextColor(
            ContextCompat.getColor(this, if (exactOn) R.color.success else R.color.error)
        )
        binding.exactAction.text = getString(if (exactOn) R.string.open_settings else R.string.allow)
        binding.exactAction.isVisible = true

        val batteryOk = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        binding.batteryStatus.text = getString(if (batteryOk) R.string.battery_on else R.string.battery_off)
        binding.batteryStatus.setTextColor(
            ContextCompat.getColor(this, if (batteryOk) R.color.success else R.color.amber)
        )
        binding.batteryAction.text =
            getString(if (batteryOk) R.string.open_settings else R.string.disable)
    }

    private fun requestNotifications() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val direct = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        }
    }

    private fun requestBatteryExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            return
        }
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
