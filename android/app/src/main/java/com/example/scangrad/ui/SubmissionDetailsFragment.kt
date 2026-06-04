package com.example.scangrad.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.scangrad.R
import com.example.scangrad.data.Submission
import com.example.scangrad.data.SubmissionStatus
import com.example.scangrad.databinding.FragmentSubmissionDetailsBinding
import com.example.scangrad.databinding.ItemQuestionEvaluationBinding
import com.example.scangrad.network.QuestionEvaluation

class SubmissionDetailsFragment : Fragment() {

    private var _binding: FragmentSubmissionDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubmissionDetailsViewModel
    private lateinit var submissionId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubmissionDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        submissionId = arguments?.getString(ARG_SUBMISSION_ID).orEmpty()

        viewModel = ViewModelProvider(this).get(SubmissionDetailsViewModel::class.java)

        binding.tvBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        viewModel.submission.observe(viewLifecycleOwner) { submission ->
            submission?.let { renderSubmission(it) }
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.svContent.visibility = View.GONE
                binding.layoutGrading.visibility = View.GONE
            }
        }

        viewModel.load(requireActivity(), submissionId)
    }

    private fun renderSubmission(submission: Submission) {
        binding.tvStudentName.text = submission.studentName.ifBlank { "Unknown student" }
        binding.tvExamTitle.text = submission.title.ifBlank { "Untitled exam" }
        binding.tvExamMeta.text = listOf(submission.courseCode, submission.date)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "—" }

        if (submission.status == SubmissionStatus.PENDING.name) {
            renderGradingState()
        } else {
            renderGradedState(submission)
        }
    }

    /** Still being graded: show the centered Lottie animation, hide the scroll content. */
    private fun renderGradingState() {
        binding.svContent.visibility = View.GONE
        binding.layoutGrading.visibility = View.VISIBLE
    }

    private fun renderGradedState(submission: Submission) {
        binding.layoutGrading.visibility = View.GONE
        binding.svContent.visibility = View.VISIBLE

        val score = submission.score
        binding.tvOverallGrade.text =
            if (score >= 0) (if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString())
            else "—"
        binding.tvOverallFeedback.text = submission.feedback.ifBlank { "(no feedback)" }

        renderGrade(score)
        renderQuestions(submission.evaluations)
    }


    private fun renderQuestions(items: List<QuestionEvaluation>) {
        val container = binding.llQuestions
        container.removeAllViews()
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEachIndexed { index, item ->
            val itemBinding = ItemQuestionEvaluationBinding.inflate(layoutInflater, container, false)
            bindQuestion(itemBinding, item, index)
            container.addView(itemBinding.root)
            animateItem(itemBinding.root, index)
        }
    }

    private fun bindQuestion(ib: ItemQuestionEvaluationBinding, item: QuestionEvaluation, position: Int) {
        ib.tvQuestionId.text = item.questionId.takeIf { it.isNotBlank() } ?: "Question ${position + 1}"
        ib.tvQuestionScore.text = formatScore(item.score, item.maxScore)
        ib.tvQuestionText.text = item.questionText.ifBlank { "(no question text)" }
        ib.tvQuestionFeedback.text = item.explanation.ifBlank { "(no feedback)" }

        // Tap a card to open the full question detail dialog.
        ib.root.setOnClickListener { showQuestionDialog(item, position) }

        // Student's answer — hide the block when the backend didn't supply one.
        val hasStudent = item.studentAnswer.isNotBlank()
        ib.lblStudentAnswer.visibility = if (hasStudent) View.VISIBLE else View.GONE
        ib.tvStudentAnswer.visibility = if (hasStudent) View.VISIBLE else View.GONE
        ib.tvStudentAnswer.text = item.studentAnswer

        // Color the score + left accent on the semantic grading scale.
        val ratio = if (item.maxScore > 0) item.score / item.maxScore else 1.0
        val colorRes = when {
            ratio >= 0.85 -> R.color.grade_high
            ratio >= 0.5 -> R.color.grade_mid
            else -> R.color.grade_low
        }
        val resolved = ContextCompat.getColor(requireContext(), colorRes)
        ib.tvQuestionScore.setTextColor(resolved)
        ib.vAccent.setBackgroundColor(resolved)
    }

    private fun showQuestionDialog(item: QuestionEvaluation, position: Int) {
        QuestionDetailDialogFragment.newInstance(item, "Question ${position + 1}")
            .show(parentFragmentManager, QuestionDetailDialogFragment.TAG)
    }

    private fun formatScore(score: Double, max: Double): String {
        val s = if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString()
        val m = if (max == max.toLong().toDouble()) max.toLong().toString() else max.toString()
        return if (max > 0) "$s / $m" else s
    }

    private fun animateItem(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = resources.displayMetrics.density * 16f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(position * 70L)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Drives the grade ring + status pill on the semantic grading scale. */
    private fun renderGrade(score: Double) {
        val ctx = requireContext()
        val pct = if (score >= 0) score.toInt().coerceIn(0, 100) else 0
        binding.progressGrade.setProgressCompat(pct, true)

        val (colorRes, pillBg, label) = when {
            score < 0 -> Triple(R.color.on_surface_variant, R.drawable.pill_primary_soft, "NOT GRADED")
            score >= 85 -> Triple(R.color.grade_high, R.drawable.pill_grade_high, "PASSING GRADE")
            score >= 60 -> Triple(R.color.grade_mid, R.drawable.pill_grade_mid, "PASSING GRADE")
            else -> Triple(R.color.grade_low, R.drawable.pill_grade_low, "NEEDS REVIEW")
        }
        val color = ContextCompat.getColor(ctx, colorRes)
        binding.progressGrade.setIndicatorColor(color)
        binding.tvGradeStatus.text = label
        binding.tvGradeStatus.setTextColor(color)
        binding.tvGradeStatus.setBackgroundResource(pillBg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SUBMISSION_ID = "submission_id"

        fun newInstance(submissionId: String): SubmissionDetailsFragment {
            val fragment = SubmissionDetailsFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_SUBMISSION_ID, submissionId)
            }
            return fragment
        }
    }
}
