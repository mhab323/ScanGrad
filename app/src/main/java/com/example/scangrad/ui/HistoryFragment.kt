package com.example.scangrad.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scangrad.adapter.HistoryAdapter
import com.example.scangrad.data.HistoryRecord
import com.example.scangrad.data.HistoryStatus
import com.example.scangrad.data.Submission
import com.example.scangrad.data.SubmissionStatus
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentHistoryBinding
import com.example.scangrad.db.FirebaseManager

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        fetchHistoryData()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(emptyList())
        binding.rvHistoryList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun setupListeners() {
        binding.btnFilters.setOnClickListener {
            // TODO: open filter bottom sheet
        }
    }

    private fun fetchHistoryData() {
        setLoadingState(true)
        FirebaseManager(requireActivity()).fetchUserHistory(
            userId    = UserSession.uid,
            onSuccess = { submissions ->
                setLoadingState(false)
                val records = submissions.map { it.toHistoryRecord() }
                historyAdapter.updateData(records)
                updateFooter(records.size)
            },
            onFailed  = { error ->
                setLoadingState(false)
                Toast.makeText(requireContext(), "Failed to load history: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
        binding.rvHistoryList.visibility = if (loading) View.GONE    else View.VISIBLE
    }

    private fun updateFooter(count: Int) {
        if (count == 0) {
            binding.tvEmptyState.visibility  = View.VISIBLE
            binding.tvLoadMore.visibility    = View.GONE
            binding.tvDisplayCount.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility  = View.GONE
            binding.tvLoadMore.visibility    = View.VISIBLE
            binding.tvDisplayCount.visibility = View.VISIBLE
            binding.tvDisplayCount.text      = "Displaying $count submission${if (count == 1) "" else "s"}"
        }
    }

    /**
     * Maps a [Submission] (as stored in Firestore) to the [HistoryRecord] that
     * the adapter displays.
     *
     * Score is 0 until the AI grading pipeline populates a `score` field;
     * status maps GRADED → HIGH_CONFIDENCE, anything else → VALIDATED.
     */
    private fun Submission.toHistoryRecord(): HistoryRecord {
        val subtitle = buildString {
            if (date.isNotEmpty()) append(date)
            if (date.isNotEmpty() && department.isNotEmpty()) append(" • ")
            if (department.isNotEmpty()) append(department)
        }
        val badge = if (status == SubmissionStatus.GRADED.name) {
            HistoryStatus.HIGH_CONFIDENCE
        } else {
            HistoryStatus.VALIDATED
        }
        return HistoryRecord(
            id          = id,
            courseCode  = courseCode.ifEmpty { "—" },
            title       = title.ifEmpty { "Untitled" },
            dateAndType = subtitle,
            score       = 0,
            maxScore    = 100,
            statusBadge = badge
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): HistoryFragment {
            val fragment = HistoryFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}
