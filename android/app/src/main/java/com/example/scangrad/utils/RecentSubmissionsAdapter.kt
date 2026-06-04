package com.example.scangrad.utils

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.scangrad.R
import com.example.scangrad.data.Submission
import com.example.scangrad.data.SubmissionStatus
import com.example.scangrad.databinding.ItemRecentSubmissionBinding

class RecentSubmissionsAdapter(
    private var submissions: List<Submission> = emptyList(),
    private val onItemClick: (Submission) -> Unit = {}
) : RecyclerView.Adapter<RecentSubmissionsAdapter.SubmissionViewHolder>() {

    inner class SubmissionViewHolder(private val binding: ItemRecentSubmissionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(submission: Submission) {
            binding.tvSubmissionTitle.text = submission.title

            val subtitleText = "${submission.department} • ${submission.date}"
            binding.tvSubmissionSubtitle.text = subtitleText

            bindAvatar(submission)

            when (SubmissionStatus.valueOf(submission.status)) {
                SubmissionStatus.GRADED -> {
                    binding.pbGrading.visibility = android.view.View.GONE
                    binding.tvStatusBadge.visibility = android.view.View.VISIBLE
                    binding.ivChevron.visibility = android.view.View.VISIBLE
                    val ctx = binding.root.context
                    // Map score to the semantic grading scale (high / mid / low).
                    val pillBg: Int
                    val pillText: Int
                    when {
                        submission.score < 0 -> { pillBg = R.drawable.pill_primary_soft; pillText = R.color.primary }
                        submission.score >= 85 -> { pillBg = R.drawable.pill_grade_high; pillText = R.color.grade_high }
                        submission.score >= 60 -> { pillBg = R.drawable.pill_grade_mid; pillText = R.color.grade_mid }
                        else -> { pillBg = R.drawable.pill_grade_low; pillText = R.color.grade_low }
                    }
                    binding.tvStatusBadge.text =
                        if (submission.score >= 0) submission.score.toInt().toString() else "GRADED"
                    binding.tvStatusBadge.setBackgroundResource(pillBg)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(ctx, pillText))
                    binding.root.isClickable = true
                    binding.root.setOnClickListener { onItemClick(submission) }
                }
                SubmissionStatus.PENDING -> {
                    binding.pbGrading.visibility = android.view.View.VISIBLE
                    binding.tvStatusBadge.visibility = android.view.View.GONE
                    binding.ivChevron.visibility = android.view.View.VISIBLE
                    // Still openable while grading runs — the details screen shows
                    // the in-progress animation instead of a grade.
                    binding.root.isClickable = true
                    binding.root.setOnClickListener { onItemClick(submission) }
                }
            }
        }

        /**
         * Loads a person photo for the submission from the pravatar pool. The photo
         * index is derived from the submission id so the same submission always shows
         * the same face (stable across scrolls and reloads) while different
         * submissions get different faces from the pool of [AVATAR_POOL_SIZE].
         */
        private fun bindAvatar(submission: Submission) {
            val key = submission.id.ifBlank { submission.studentName.ifBlank { submission.title } }
            val photoIndex = Math.floorMod(key.hashCode(), AVATAR_POOL_SIZE) + 1
            val url = "https://i.pravatar.cc/150?img=$photoIndex"

            binding.ivSubmissionAvatar.load(url) {
                crossfade(true)
                placeholder(R.drawable.ic_doc)
                error(R.drawable.ic_doc)
                transformations(CircleCropTransformation())
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

    private companion object {
        // Number of distinct pravatar photos to rotate through.
        const val AVATAR_POOL_SIZE = 10
    }
}
