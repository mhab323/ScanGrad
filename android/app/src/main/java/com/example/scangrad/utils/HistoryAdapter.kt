package com.example.scangrad.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scangrad.databinding.ItemHistoryCardBinding
import com.example.scangrad.data.HistoryRecord
import com.example.scangrad.data.HistoryStatus

class HistoryAdapter(
    private var records: List<HistoryRecord> = emptyList(),
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(private val binding: ItemHistoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: HistoryRecord) {
            binding.tvCourseCode.text = record.courseCode
            binding.tvHistoryTitle.text = record.title
            binding.tvHistorySubtitle.text = record.dateAndType
            binding.tvScoreValue.text = record.score.toString()
            binding.tvScoreMax.text = "/${record.maxScore}"
            binding.tvHistoryBadge.text = record.statusBadge.label

            binding.root.setOnClickListener {
                onItemClick(record.id)
            }

            val badgeBackground = GradientDrawable()
            badgeBackground.cornerRadius = 999f

            when (record.statusBadge) {
                HistoryStatus.HIGH_CONFIDENCE -> {
                    badgeBackground.setColor(Color.parseColor("#16A34A"))
                    binding.tvHistoryBadge.setTextColor(Color.WHITE)
                }
                HistoryStatus.VALIDATED -> {
                    badgeBackground.setColor(Color.parseColor("#6B6880"))
                    binding.tvHistoryBadge.setTextColor(Color.WHITE)
                }
                HistoryStatus.PENDING -> {
                    badgeBackground.setColor(Color.parseColor("#D97706"))
                    binding.tvHistoryBadge.setTextColor(Color.WHITE)
                }
            }
            binding.tvHistoryBadge.background = badgeBackground
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newRecords: List<HistoryRecord>) {
        this.records = newRecords
        notifyDataSetChanged()
    }
}