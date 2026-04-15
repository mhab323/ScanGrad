package com.example.scangrad.utils

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scangrad.data.Submission
import com.example.scangrad.data.SubmissionStatus
import com.example.scangrad.databinding.ItemRecentSubmissionBinding

class RecentSubmissionsAdapter(
    private var submissions: List<Submission> = emptyList()
) : RecyclerView.Adapter<RecentSubmissionsAdapter.SubmissionViewHolder>() {

    inner class SubmissionViewHolder(private val binding: ItemRecentSubmissionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(submission: Submission) {
            binding.tvSubmissionTitle.text = submission.title

            val subtitleText = "${submission.department} • ${submission.date}"
            binding.tvSubmissionSubtitle.text = subtitleText

            when (SubmissionStatus.valueOf(submission.status)) {
                SubmissionStatus.GRADED -> {
                    binding.tvStatusBadge.text = "GRADED"
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#3F51B5"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E8EAF6"))
                }
                SubmissionStatus.PENDING -> {
                    binding.tvStatusBadge.text = "PENDING"
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#D84315"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FBE9E7"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionViewHolder {
        val binding = ItemRecentSubmissionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubmissionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int) {
        holder.bind(submissions[position])
    }

    override fun getItemCount(): Int = submissions.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSubmissions: List<Submission>) {
        this.submissions = newSubmissions
        notifyDataSetChanged()
    }
}
