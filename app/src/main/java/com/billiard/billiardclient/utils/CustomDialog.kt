package com.billiard.billiardclient.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.billiard.billiardclient.databinding.CustomDialogBinding

// 커스텀 다이얼로그
class CustomDialog() : DialogFragment() {
    private var _binding: CustomDialogBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(message: String): CustomDialog {
            val args = Bundle()
            args.putString("message", message)
            val fragment = CustomDialog()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val message = arguments?.getString("message")

        _binding = CustomDialogBinding.inflate(inflater, container, false)
        val view = binding.root

        // 제목 설정
        binding.errorMessageTv.text = message

        // 확인 버튼
        binding.checkBtn.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}