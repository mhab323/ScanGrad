package com.example.scangrad.ui

import android.R
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.scangrad.data.Submission
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentValidationBinding
import com.example.scangrad.db.FirebaseManager
import com.example.scangrad.network.EvaluationRequest
import com.example.scangrad.network.EvaluationService
import java.io.File
import androidx.core.net.toUri
import java.util.Calendar

class ValidationFragment : Fragment() {

    private var _binding: FragmentValidationBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageUriString: String
    private lateinit var source: String

    private var selectedDateString: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentValidationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageUriString = arguments?.getString(ARG_IMAGE_URI).orEmpty()
        source = arguments?.getString(ARG_SOURCE) ?: SOURCE_CAMERA
        if (source == SOURCE_UPLOAD) {
            binding.btnRetake.text = "Replace"
        }
        binding.ivCapturedPhoto.post { loadPdfPreview() }
        populateDummyData()
        setupListeners()
    }

    private fun loadPdfPreview() {
        if (imageUriString.isEmpty()) return
        try {
            val uri = Uri.parse(imageUriString)
            val pfd: ParcelFileDescriptor = if (uri.scheme == "file") {
                ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                requireContext().contentResolver.openFileDescriptor(uri, "r")
                    ?: return
            }
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val viewWidth = binding.ivCapturedPhoto.width.takeIf { it > 0 } ?: page.width
            val viewHeight = binding.ivCapturedPhoto.height.takeIf { it > 0 } ?: page.height
            val bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            binding.ivCapturedPhoto.setImageBitmap(bitmap)
            page.close()
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            binding.ivCapturedPhoto.setImageResource(android.R.drawable.ic_menu_agenda)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateDummyData() {
        binding.etCourseCode.setText("CS 301")
        binding.etDepartment.setText("Computer Science")
        binding.etTitle.setText("Final Exam")
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener { onCancelClicked() }
        binding.btnRetake.setOnClickListener { onRetakeClicked() }
        binding.btnConfirm.setOnClickListener { onConfirmClicked() }
        binding.btnSelectDate.setOnClickListener {
            openDatePicker()
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-indexed
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)

                selectedDateString = "$year-$formattedMonth-$formattedDay"

                binding.btnSelectDate.text = selectedDateString
                binding.btnSelectDate.setTextColor(requireContext().getColor(R.color.black))
            },
            currentYear,
            currentMonth,
            currentDay
        )

        datePickerDialog.show()
    }

    private fun onCancelClicked() {
        requireActivity().supportFragmentManager
            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    private fun onRetakeClicked() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    @SuppressLint("SetTextI18n")
    private fun onConfirmClicked() {
        val localUri = imageUriString.toUri()
        binding.btnConfirm.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.btnRetake.isEnabled = false
        binding.pbUpload.visibility = View.VISIBLE
        binding.tvUploadStatus.visibility = View.VISIBLE
        binding.tvUploadStatus.text = "Uploading document..."

        // Read the form NOW — the view may be destroyed (e.g. the user taps Home)
        // before these async callbacks fire, after which `binding` is null and any
        // access to it crashes. Capture an app context for toasts for the same reason.
        val baseSubmission = buildSubmission(imageUri = "")
        val appContext = requireContext().applicationContext
        val firebaseManager = FirebaseManager(requireActivity())

        firebaseManager.uploadFileToStorage(
            fileUri = localUri,
            userId = UserSession.uid,
            onSuccess = { downloadUrl ->
                _binding?.tvUploadStatus?.text = "Saving submission..."
                val submission = baseSubmission.copy(imageUri = downloadUrl)

                firebaseManager.saveSubmission(
                    submission = submission,
                    onSuccess = { submissionId ->
                        val request = EvaluationRequest(
                            submissionId = submissionId,
                            courseCode = submission.courseCode,
                            examQuestion = submission.title,
                            extractedText = "",
                            imageUrl = downloadUrl
                        )
                        // Grade in the background so the screen doesn't block.
                        EvaluationService.submitForGrading(request)

                        Toast.makeText(appContext, "Submission sent for grading", Toast.LENGTH_SHORT).show()
                        // Only navigate if we're still on this screen; if the user
                        // already left, leave them wherever they are.
                        if (_binding != null) {
                            activity?.supportFragmentManager
                                ?.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        }
                    },
                    onFailed = { error ->
                        if (_binding != null) resetButtons()
                        Toast.makeText(appContext, "Save failed: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailed = { error ->
                if (_binding != null) resetButtons()
                Toast.makeText(appContext, "Upload failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun resetButtons() {
        binding.btnConfirm.isEnabled = true
        binding.btnCancel.isEnabled = true
        binding.btnRetake.isEnabled = true
        binding.pbUpload.visibility = View.GONE
        binding.tvUploadStatus.visibility = View.GONE
    }

    private fun buildSubmission(imageUri: String): Submission {
        return Submission(
            userId = UserSession.uid,
            studentName = UserSession.current?.displayName.orEmpty(),
            courseCode = binding.etCourseCode.text.toString().trim(),
            department = binding.etDepartment.text.toString().trim(),
            title = binding.etTitle.text.toString().trim(),
            date = selectedDateString,
            imageUri = imageUri,
            status = "PENDING"
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IMAGE_URI = "image_uri"
        private const val ARG_SOURCE = "source"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_UPLOAD = "upload"

        fun newInstance(imageUri: String, source: String): ValidationFragment {
            val fragment = ValidationFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_IMAGE_URI, imageUri)
                putString(ARG_SOURCE, source)
            }
            return fragment
        }
    }
}
