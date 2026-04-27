package com.example.scangrad.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentDashboardBinding
import com.example.scangrad.db.FirebaseManager
import com.example.scangrad.utils.RecentSubmissionsAdapter

class DashboardFragment : Fragment() {
        private var _binding: FragmentDashboardBinding? = null
        private val binding get() = _binding!!

        private lateinit var adapter: RecentSubmissionsAdapter

        private val pickPdfLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { navigateToValidation(it) }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentDashboardBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            setupRecyclerView()
            setupListeners()
            fetchDashboardData()
        }

        private fun setupRecyclerView() {
            adapter = RecentSubmissionsAdapter(emptyList())

             binding.rvRecentSubmissions.layoutManager = LinearLayoutManager(requireContext())
             binding.rvRecentSubmissions.adapter = adapter
        }

        private fun setupListeners() {
            binding.fabScanner.setOnClickListener {
                showAddDocumentDialog()
            }
        }

        private fun showAddDocumentDialog() {
            AlertDialog.Builder(requireContext())
                .setTitle("Add Document")
                .setItems(arrayOf("Scan Document", "Upload PDF")) { _, which ->
                    when (which) {
                        0 -> (requireActivity() as HostActivity).navigateTo(CameraFragment.newInstance())
                        1 -> pickPdfLauncher.launch("application/pdf")
                    }
                }
                .show()
        }

        private fun navigateToValidation(uri: Uri) {
            val fragment = ValidationFragment.newInstance(uri.toString(), ValidationFragment.SOURCE_UPLOAD)
            (requireActivity() as HostActivity).navigateTo(fragment)
        }

        private fun fetchDashboardData() {
            FirebaseManager(requireActivity()).fetchRecentSubmissions(
                userId = UserSession.uid,
                onSuccess = { submissions -> adapter.updateData(submissions) },
                onFailed = { error ->
                    Toast.makeText(requireContext(), "Failed to load submissions: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }

        override fun onResume() {
            super.onResume()
            fetchDashboardData()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        companion object {
            fun newInstance(): DashboardFragment {
                val fragment = DashboardFragment()
                val args = Bundle()
                fragment.arguments = args
                return fragment
            }
        }
}

