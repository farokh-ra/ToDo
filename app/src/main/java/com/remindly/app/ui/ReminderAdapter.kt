package com.remindly.app.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.remindly.app.R
import com.remindly.app.data.Reminder
import com.remindly.app.databinding.ItemHeaderBinding
import com.remindly.app.databinding.ItemReminderBinding
import com.remindly.app.util.TimeFmt

class ReminderAdapter(
    private val onOpen: (Reminder) -> Unit,
    private val onToggle: (Reminder) -> Unit,
    private val onMenu: (Reminder, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed interface Row {
        data class Header(val label: String) : Row
        data class Item(val reminder: Reminder) : Row
    }

    private val rows = ArrayList<Row>()

    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemReminderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.label)
            is Row.Item -> (holder as ItemHolder).bind(row.reminder)
        }
    }

    private class HeaderHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String) {
            binding.label.text = label
        }
    }

    private inner class ItemHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            val context = binding.root.context
            val next = reminder.next()
            val done = reminder.isDone
            val count = reminder.occurrences.size
            val dates = context.resources.getQuantityString(R.plurals.date_count, count, count)

            binding.title.text = reminder.title
            binding.title.paintFlags = if (done) {
                binding.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.title.setTextColor(
                ContextCompat.getColor(context, if (done) R.color.muted else R.color.on_surface)
            )

            if (done || next == null) {
                binding.time.text = context.getString(R.string.completed)
                binding.time.setTextColor(ContextCompat.getColor(context, R.color.muted))
                binding.meta.text = dates
            } else {
                binding.time.text = TimeFmt.dateTime(next.at)
                binding.time.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (TimeFmt.isOverdue(next.at)) R.color.error else R.color.primary
                    )
                )
                binding.meta.text = if (count > 1) {
                    "${TimeFmt.countdown(next.at)} · $dates"
                } else {
                    TimeFmt.countdown(next.at)
                }
            }

            binding.check.setImageResource(
                if (done) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
            )
            binding.check.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, if (done) R.color.primary else R.color.muted)
            )
            binding.check.contentDescription =
                context.getString(if (done) R.string.undo_all else R.string.mark_done)

            binding.accent.setBackgroundColor(
                ContextCompat.getColor(context, ACCENTS[Math.floorMod(reminder.id.hashCode(), ACCENTS.size)])
            )
            binding.root.alpha = if (done) 0.72f else 1f
            binding.card.contentDescription = reminder.title

            binding.card.setOnClickListener { onOpen(reminder) }
            binding.check.setOnClickListener { onToggle(reminder) }
            binding.more.setOnClickListener { onMenu(reminder, it) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private val ACCENTS = intArrayOf(
            R.color.accent_a,
            R.color.accent_b,
            R.color.accent_c,
            R.color.accent_d,
            R.color.accent_e
        )
    }
}
