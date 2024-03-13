package com.billiard.billiardclient.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.billiard.billiardclient.databinding.CustomProgressDialogBinding
import java.util.*

// 커스텀 다이얼로그
class CustomProgressDialog() : DialogFragment() {
    private var _binding: CustomProgressDialogBinding? = null
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

        _binding = CustomProgressDialogBinding.inflate(inflater, container, false)
        val view = binding.root

        // 제목 설정
        binding.errorMessageTv.text = message


        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                dismiss()
            }
        }, 2000)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}