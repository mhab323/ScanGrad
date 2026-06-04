package com.example.scangrad.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentIngestBinding
import com.example.scangrad.db.FirebaseManager
import com.example.scangrad.network.FastApiService
import com.example.scangrad.network.IngestRequest
import com.example.scangrad.viewmodel.IngestViewModel

class IngestFragment : Fragment() {

    private var _binding: FragmentIngestBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: IngestViewModel

    private var questionsUri: Uri? = null
    private var answerKeyUri: Uri? = null

    private val pickQuestions = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { questionsUri = it; binding.tvQuestionsFile.text = it.lastPathSegment } }

    private val pickAnswerKey = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { answerKeyUri = it; binding.tvAnswerKeyFile.text = it.lastPathSegment } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIngestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this).get(IngestViewModel::class.java)
        binding.btnPickQuestions.setOnClickListener { pickQuestions.launch("application/pdf") }
        binding.btnPickAnswerKey.setOnClickListener { pickAnswerKey.launch("application/pdf") }
        binding.btnSubmitIngest.setOnClickListener { onSubmit() }
        observeVm()
    }

    private fun observeVm() {
        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.pbIngest.visibility = if (loading) View.VISIBLE else View.GONE
            binding.tvIngestStatus.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSubmitIngest.isEnabled = !loading
        }
        vm.result.observe(viewLifecycleOwner) { r ->
            r ?: return@observe
            Toast.makeText(
                requireContext(),
                "Added ${r.chunksAdded} chunks (${r.mode}) → ${r.docId}",
                Toast.LENGTH_LONG
            ).show()
            vm.clear()
            requireActivity().supportFragmentManager.popBackStack()
        }
        vm.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            Toast.makeText(requireContext(), "Ingest failed: $msg", Toast.LENGTH_LONG).show()
            vm.clear()
        }
    }

    private fun onSubmit() {
        if (!FastApiService.ApiClient.isReady) {
            Toast.makeText(
                requireContext(),
                "Server not reachable yet, please try again in a moment",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val courseCode = binding.etCourseCode.text.toString().trim()
        val year = binding.etYear.text.toString().trim()
        val semester = binding.spSemester.selectedItem.toString()   // sem1/sem2/sem3
        val moed = binding.spMoed.selectedItem.toString()           // moed1/moed2
        val version = binding.etVersion.text.toString().trim().ifEmpty { "v1" }

        if (courseCode.isEmpty() || year.isEmpty()) {
            Toast.makeText(requireContext(), "Course code and year are required", Toast.LENGTH_SHORT).show(); return
        }
        if (questionsUri == null && answerKeyUri == null) {
            Toast.makeText(requireContext(), "Pick a questions and/or answer-key PDF", Toast.LENGTH_SHORT).show(); return
        }

        binding.pbIngest.visibility = View.VISIBLE
        binding.tvIngestStatus.visibility = View.VISIBLE
        binding.tvIngestStatus.text = "Uploading file(s)..."
        binding.btnSubmitIngest.isEnabled = false
        val fm = FirebaseManager(requireActivity())

        // Upload questions (if any), then answer key (if any), then POST. Nested
        // callbacks keep it simple; a coroutine wrapper is a fine refactor later.
        uploadOrNull(fm, questionsUri) { qUrl ->
            uploadOrNull(fm, answerKeyUri) { aUrl ->
                binding.tvIngestStatus.text = "Indexing on server..."
                vm.ingest(
                    IngestRequest(
                        courseCode = courseCode, year = year, semester = semester,
                        moed = moed, version = version,
                        questionsUrl = qUrl, answerKeyUrl = aUrl
                    )
                )
            }
        }
    }

    /** Upload [uri] if non-null and return its download URL, else pass null through. */
    private fun uploadOrNull(fm: FirebaseManager, uri: Uri?, next: (String?) -> Unit) {
        if (uri == null) { next(null); return }
        fm.uploadFileToStorage(
            fileUri = uri,
            userId = UserSession.uid,
            onSuccess = { url -> next(url) },
            onFailed = { err ->
                _binding?.let {
                    it.pbIngest.visibility = View.GONE
                    it.tvIngestStatus.visibility = View.GONE
                    it.btnSubmitIngest.isEnabled = true
                }
                Toast.makeText(requireContext(), "Upload failed: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = IngestFragment()
    }
}
