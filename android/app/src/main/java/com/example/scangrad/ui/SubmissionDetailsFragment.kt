package com.example.scangrad.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scangrad.data.Submission
import com.example.scangrad.databinding.FragmentSubmissionDetailsBinding
import com.example.scangrad.network.QuestionEvaluation
import com.example.scangrad.utils.QuestionEvaluationAdapter

class SubmissionDetailsFragment : Fragment() {

    private var _binding: FragmentSubmissionDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubmissionDetailsViewModel
    private lateinit var adapter: QuestionEvaluationAdapter
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

        setupRecyclerView()
        binding.tvBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        viewModel.submission.observe(viewLifecycleOwner) { submission ->
            submission?.let { renderSubmission(it) }
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
            binding.svContent.visibility = if (loading) View.GONE else View.VISIBLE
        }

        viewModel.load(requireActivity(), submissionId)
    }

    private fun setupRecyclerView() {
        adapter = QuestionEvaluationAdapter(
            items = emptyList(),
            onItemClick = { item, position -> showQuestionDialog(item, position) }
        )
        binding.rvQuestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQuestions.adapter = adapter
    }

    private fun renderSubmission(submission: Submission) {
        binding.tvStudentName.text = submission.studentName.ifBlank { "Unknown student" }
        binding.tvExamSubtitle.text = listOf(submission.title, submission.courseCode)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "—" }

        val score = submission.score
        binding.tvOverallGrade.text =
            if (score >= 0) (if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString())
            else "—"
        binding.tvOverallFeedback.text = submission.feedback.ifBlank { "(no feedback)" }

        adapter.updateData(submission.evaluations)
        binding.tvEmpty.visibility = if (submission.evaluations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showQuestionDialog(item: QuestionEvaluation, position: Int) {
        val fallback = "Question ${position + 1}"
        QuestionDetailDialogFragment.newInstance(item, fallback)
            .show(parentFragmentManager, QuestionDetailDialogFragment.TAG)
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
