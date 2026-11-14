package com.remindly.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.remindly.app.R
import com.remindly.app.databinding.DialogJalaliPickerBinding
import com.remindly.app.util.Jalali
import java.time.LocalDate

/**
 * Shamsi (Jalali) calendar picker: Saturday-first week, Persian month names in
 * English transliteration, month arrows and a tappable title for year jumps.
 */
object JalaliDatePicker {

    fun show(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
        val binding = DialogJalaliPickerBinding.inflate(LayoutInflater.from(context))
        val today = LocalDate.now()
        val todayJalali = Jalali.from(today)
        var selected = Jalali.from(initial)
        var shownYear = selected.year
        var shownMonth = selected.month

        buildWeekHeader(context, binding.weekHeader)

        fun render() {
            binding.monthTitle.text = "${Jalali.monthName(shownMonth)} $shownYear"
            binding.dayGrid.removeAllViews()

            val first = Jalali.toLocalDate(Jalali.Date(shownYear, shownMonth, 1))
            val offset = (first.dayOfWeek.value + 1) % 7
            val length = Jalali.monthLength(shownYear, shownMonth)

            for (i in 0 until offset) {
                binding.dayGrid.addView(emptyCell(context), cellParams(context, 0, i))
            }

            for (day in 1..length) {
                val index = offset + day - 1
                val cell = dayCell(context, day)
                val isSelected = selected.year == shownYear &&
                    selected.month == shownMonth && selected.day == day
                val isToday = todayJalali.year == shownYear &&
                    todayJalali.month == shownMonth && todayJalali.day == day

                when {
                    isSelected -> {
                        cell.setBackgroundResource(R.drawable.bg_day_selected)
                        cell.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                    isToday -> {
                        cell.setBackgroundResource(R.drawable.bg_day_today)
                        cell.setTextColor(ContextCompat.getColor(context, R.color.primary))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                    else -> {
                        val attrs = TypedValue()
                        context.theme.resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless, attrs, true
                        )
                        cell.setBackgroundResource(attrs.resourceId)
                        cell.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                    }
                }

                cell.setOnClickListener {
                    selected = Jalali.Date(shownYear, shownMonth, day)
                    render()
                }
                binding.dayGrid.addView(cell, cellParams(context, index / 7, index % 7))
            }
        }

        binding.prevMonth.setOnClickListener {
            shownMonth -= 1
            if (shownMonth < 1) {
                shownMonth = 12
                shownYear -= 1
            }
            if (shownYear < 1179) {
                shownYear = 1179
                shownMonth = 1
            }
            render()
        }
        binding.nextMonth.setOnClickListener {
            shownMonth += 1
            if (shownMonth > 12) {
                shownMonth = 1
                shownYear += 1
            }
            if (shownYear > 3176) {
                shownYear = 3176
                shownMonth = 12
            }
            render()
        }
        binding.monthTitle.setOnClickListener {
            val years = (shownYear - 5..shownYear + 15).toList()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_year)
                .setItems(years.map { it.toString() }.toTypedArray()) { _, which ->
                    shownYear = years[which]
                    render()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.todayButton.setOnClickListener {
            selected = todayJalali
            shownYear = todayJalali.year
            shownMonth = todayJalali.month
            render()
        }

        render()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pick_date)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onPick(Jalali.toLocalDate(selected))
            }
            .show()
    }

    private fun buildWeekHeader(context: Context, container: LinearLayout) {
        val labels = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
        labels.forEach { label ->
            val cell = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 10f
            }
            container.addView(cell, LinearLayout.LayoutParams(0, dp(context, 22), 1f))
        }
    }

    private fun dayCell(context: Context, day: Int): TextView = TextView(context).apply {
        text = day.toString()
        gravity = Gravity.CENTER
        textSize = 14f
        isClickable = true
        isFocusable = true
    }

    private fun emptyCell(context: Context): TextView = TextView(context).apply {
        isClickable = false
        isFocusable = false
    }

    private fun cellParams(context: Context, row: Int, col: Int): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(row)
            columnSpec = GridLayout.spec(col, 1f)
            width = 0
            height = dp(context, 40)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
