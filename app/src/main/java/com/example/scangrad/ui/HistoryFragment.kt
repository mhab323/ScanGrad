// HistoryFragment.kt
package com.example.scangrad.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scangrad.adapter.HistoryAdapter
import com.example.scangrad.data.HistoryRecord
import com.example.scangrad.data.HistoryStatus
import com.example.scangrad.databinding.FragmentHistoryBinding

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
        // Setup Search TextWatcher or Filter click events here
        binding.btnFilters.setOnClickListener {
            // Open filter bottom sheet
        }
    }

    private fun fetchHistoryData() {
        // Dummy data perfectly matching your Figma screenshot
        val mockData = listOf(
            HistoryRecord("1", "CS-403", "Advanced Quantum Algorithms", "Oct 24, 2023 • Mid-Term Assessment", 94, 100, HistoryStatus.HIGH_CONFIDENCE),
            HistoryRecord("2", "LIT-210", "Renaissance Literature Analysis", "Oct 12, 2023 • Essay Submission", 88, 100, HistoryStatus.VALIDATED),
            HistoryRecord("3", "MATH-101", "Linear Algebra Essentials", "Sep 28, 2023 • Quiz 04", 72, 100, HistoryStatus.VALIDATED),
            HistoryRecord("4", "BIO-305", "Molecular Genetics Lab", "Sep 15, 2023 • Final Practical", 100, 100, HistoryStatus.HIGH_CONFIDENCE)
        )
        historyAdapter.updateData(mockData)
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