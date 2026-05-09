package com.example.scangrad.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.scangrad.data.UserSession
import com.example.scangrad.databinding.FragmentSettingsBinding
import com.example.scangrad.db.FirebaseManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()
        setupListeners()
    }

    private fun loadUserProfile() {
        val user = UserSession.current ?: return
        binding.tvDisplayName.text   = user.displayName.ifEmpty { "User" }
        binding.tvEmailOrPhone.text  = user.email.ifEmpty { user.phoneNumber.ifEmpty { "—" } }
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            FirebaseManager(requireActivity()).signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}
