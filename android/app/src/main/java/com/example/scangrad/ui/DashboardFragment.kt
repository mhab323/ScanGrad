package com.example.scangrad.ui

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scangrad.R
import com.example.scangrad.data.Submission
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentDashboardBinding
import com.example.scangrad.db.FirebaseManager
import com.example.scangrad.utils.RecentSubmissionsAdapter
import com.google.firebase.firestore.ListenerRegistration

class DashboardFragment : Fragment() {
        private var _binding: FragmentDashboardBinding? = null
        private val binding get() = _binding!!

        private lateinit var adapter: RecentSubmissionsAdapter
        private var submissionsListener: ListenerRegistration? = null

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

            showGreeting()
            setupRecyclerView()
            setupListeners()
            startListeningForSubmissions()
            loadSuccessRate()
        }

        /** Greets the signed-in user by their first name (from their profile). */
        private fun showGreeting() {
            val name = UserSession.current?.displayName?.trim().orEmpty()
            val firstName = name.substringBefore(" ").ifBlank { "there" }
            binding.tvGreeting.text = "Hello, $firstName"
        }

        /**
         * Pulls every submission for the user and derives the academic-standing
         * success rate from the graded ones (average score). The circular progress
         * bar then animates from 0 up to that value each time the screen loads.
         */
        private fun loadSuccessRate() {
            FirebaseManager(requireActivity()).fetchUserHistory(
                userId = UserSession.uid,
                onSuccess = { submissions -> if (_binding != null) showSuccessRate(submissions) },
                onFailed = { /* keep the placeholder; no toast to avoid noise on the dashboard */ }
            )
        }

        private fun showSuccessRate(submissions: List<Submission>) {
            binding.tvScannedCount.text = submissions.size.toString()

            val gradedScores = submissions.map { it.score }.filter { it >= 0 }
            val rate = if (gradedScores.isEmpty()) 0.0 else gradedScores.average()

            binding.tvSuccessRate.text = String.format("%.1f%%", rate)

            val colorRes = when {
                gradedScores.isEmpty() -> R.color.on_surface_variant
                rate >= 85 -> R.color.grade_high
                rate >= 60 -> R.color.grade_mid
                else -> R.color.grade_low
            }
            val color = ContextCompat.getColor(requireContext(), colorRes)

            binding.tvGradeLetter.text = if (gradedScores.isEmpty()) "—" else letterFor(rate)
            binding.tvGradeLetter.setTextColor(color)
            binding.progressSuccess.setIndicatorColor(color)

            animateProgress(rate.toInt().coerceIn(0, 100))
        }

        private fun letterFor(rate: Double): String = when {
            rate >= 90 -> "A"
            rate >= 80 -> "B"
            rate >= 70 -> "C"
            rate >= 60 -> "D"
            else -> "F"
        }

        private fun animateProgress(target: Int) {
            binding.progressSuccess.progress = 0
            ObjectAnimator.ofInt(binding.progressSuccess, "progress", 0, target).apply {
                duration = 900
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        private fun setupRecyclerView() {
            adapter = RecentSubmissionsAdapter(
                submissions = emptyList(),
                onItemClick = { submission -> openSubmissionDetails(submission.id) }
            )

             binding.rvRecentSubmissions.layoutManager = LinearLayoutManager(requireContext())
             binding.rvRecentSubmissions.adapter = adapter
        }

        private fun openSubmissionDetails(submissionId: String) {
            if (submissionId.isBlank()) return
            (requireActivity() as HostActivity)
                .navigateTo(SubmissionDetailsFragment.newInstance(submissionId))
        }

        private fun setupListeners() {
            binding.fabScanner.setOnClickListener {
                showAddDocumentDialog()
            }
        }

        private fun showAddDocumentDialog() {
            AlertDialog.Builder(requireContext())
                .setTitle("Add Document")
                .setItems(arrayOf("Scan Document", "Upload PDF", "Upload Exam Key (Knowledge Base)")) { _, which ->
                    when (which) {
                        0 -> (requireActivity() as HostActivity).navigateTo(CameraFragment.newInstance())
                        1 -> pickPdfLauncher.launch("application/pdf")
                        2 -> (requireActivity() as HostActivity).navigateTo(IngestFragment.newInstance())
                    }
                }
                .show()
        }

        private fun navigateToValidation(uri: Uri) {
            val fragment = ValidationFragment.newInstance(uri.toString(), ValidationFragment.SOURCE_UPLOAD)
            (requireActivity() as HostActivity).navigateTo(fragment)
        }

        private fun startListeningForSubmissions() {
            submissionsListener?.remove()
            submissionsListener = FirebaseManager(requireActivity()).listenRecentSubmissions(
                userId = UserSession.uid,
                onUpdate = { submissions -> adapter.updateData(submissions) },
                onFailed = { error ->
                    Toast.makeText(requireContext(), "Failed to load submissions: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }

        override fun onDestroyView() {
            super.onDestroyView()
            submissionsListener?.remove()
            submissionsListener = null
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

