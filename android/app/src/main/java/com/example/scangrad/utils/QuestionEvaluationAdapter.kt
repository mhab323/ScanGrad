package com.example.scangrad.utils

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scangrad.databinding.ItemQuestionEvaluationBinding
import com.example.scangrad.network.QuestionEvaluation

class QuestionEvaluationAdapter(
    private var items: List<QuestionEvaluation> = emptyList(),
    private val onItemClick: (item: QuestionEvaluation, position: Int) -> Unit
) : RecyclerView.Adapter<QuestionEvaluationAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(private val binding: ItemQuestionEvaluationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QuestionEvaluation, position: Int) {
            val label = item.questionId.takeIf { it.isNotBlank() } ?: "Question ${position + 1}"
            binding.tvQuestionId.text = label
            binding.tvQuestionScore.text = formatScore(item.score, item.maxScore)
            binding.tvQuestionFeedback.text = item.explanation
            binding.root.setOnClickListener { onItemClick(item, position) }
        }

        private fun formatScore(score: Double, max: Double): String {
            val s = if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString()
            val m = if (max == max.toLong().toDouble()) max.toLong().toString() else max.toString()
            return if (max > 0) "$s / $m" else s
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemQuestionEvaluationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<QuestionEvaluation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
