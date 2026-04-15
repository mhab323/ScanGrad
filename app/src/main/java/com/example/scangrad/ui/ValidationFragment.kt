package com.example.scangrad.ui

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
import java.io.File

class ValidationFragment : Fragment() {

    private var _binding: FragmentValidationBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageUriString: String
    private lateinit var source: String

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

    private fun populateDummyData() {
        binding.etCourseCode.setText("CS 301")
        binding.etDepartment.setText("Computer Science")
        binding.etTitle.setText("Final Exam")
        binding.etDate.setText("Oct 24")
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener { onCancelClicked() }
        binding.btnRetake.setOnClickListener { onRetakeClicked() }
        binding.btnConfirm.setOnClickListener { onConfirmClicked() }
    }

    private fun onCancelClicked() {
        requireActivity().supportFragmentManager
            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    private fun onRetakeClicked() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    private fun onConfirmClicked() {
        val submission = buildSubmission()
        binding.btnConfirm.isEnabled = false

        FirebaseManager(requireActivity()).saveSubmission(
            submission = submission,
            onSuccess = {
                Toast.makeText(requireContext(), "Submission saved!", Toast.LENGTH_SHORT).show()
                requireActivity().supportFragmentManager
                    .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            },
            onFailed = { error ->
                binding.btnConfirm.isEnabled = true
                Toast.makeText(requireContext(), "Save failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun buildSubmission(): Submission {
        return Submission(
            userId = UserSession.uid,
            courseCode = binding.etCourseCode.text.toString().trim(),
            department = binding.etDepartment.text.toString().trim(),
            title = binding.etTitle.text.toString().trim(),
            date = binding.etDate.text.toString().trim(),
            imageUri = imageUriString,
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
