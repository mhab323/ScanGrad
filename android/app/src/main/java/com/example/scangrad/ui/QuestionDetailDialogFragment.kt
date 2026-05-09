package com.example.scangrad.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.scangrad.databinding.DialogQuestionDetailBinding
import com.example.scangrad.network.QuestionEvaluation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class QuestionDetailDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogQuestionDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQuestionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val title = args.getString(ARG_TITLE).orEmpty()
        val questionText = args.getString(ARG_TEXT).orEmpty()
        val explanation = args.getString(ARG_EXPLANATION).orEmpty()
        val score = args.getDouble(ARG_SCORE)
        val maxScore = args.getDouble(ARG_MAX_SCORE)

        binding.tvQuestionTitle.text = title.ifBlank { "Question" }
        binding.tvQuestionText.text = questionText.ifBlank { "(no question text)" }
        binding.tvExplanation.text = explanation.ifBlank { "(no feedback)" }
        binding.tvScoreBadge.text = formatScore(score, maxScore)

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatScore(score: Double, max: Double): String {
        val s = if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString()
        val m = if (max == max.toLong().toDouble()) max.toLong().toString() else max.toString()
        return if (max > 0) "$s / $m" else s
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_TEXT = "text"
        private const val ARG_EXPLANATION = "explanation"
        private const val ARG_SCORE = "score"
        private const val ARG_MAX_SCORE = "max_score"
        const val TAG = "QuestionDetailDialog"

        fun newInstance(item: QuestionEvaluation, fallbackTitle: String): QuestionDetailDialogFragment {
            val fragment = QuestionDetailDialogFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TITLE, item.questionId.ifBlank { fallbackTitle })
                putString(ARG_TEXT, item.questionText)
                putString(ARG_EXPLANATION, item.explanation)
                putDouble(ARG_SCORE, item.score)
                putDouble(ARG_MAX_SCORE, item.maxScore)
            }
            return fragment
        }
    }
}
