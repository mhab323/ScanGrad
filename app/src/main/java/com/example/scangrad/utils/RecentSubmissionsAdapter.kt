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
                    binding.pbGrading.visibility = android.view.View.GONE
                    binding.tvStatusBadge.visibility = android.view.View.VISIBLE
                    val scoreText = if (submission.score >= 0) "GRADED ${submission.score}" else "GRADED"
                    binding.tvStatusBadge.text = scoreText
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#3F51B5"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E8EAF6"))
                }
                SubmissionStatus.PENDING -> {
                    binding.pbGrading.visibility = android.view.View.VISIBLE
                    binding.tvStatusBadge.visibility = android.view.View.GONE
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
