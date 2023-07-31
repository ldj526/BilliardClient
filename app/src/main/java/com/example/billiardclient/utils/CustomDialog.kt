package com.example.billiardclient.utils

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.billiardclient.SettingActivity
import com.example.billiardclient.databinding.CustomDialogBinding

// 커스텀 다이얼로그
class CustomDialog(private val message: String) : DialogFragment() {
    private var _binding: CustomDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CustomDialogBinding.inflate(inflater, container, false)
        val view = binding.root

        // 제목 설정
        binding.errorMessageTv.text = message

        // 확인 버튼
        binding.checkBtn.setOnClickListener {
            val intent = Intent(requireContext(), SettingActivity::class.java)
            startActivity(intent)
            dismiss()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}