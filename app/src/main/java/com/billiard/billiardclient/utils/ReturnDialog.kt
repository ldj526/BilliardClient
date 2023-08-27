package com.billiard.billiardclient.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.billiard.billiardclient.databinding.ReturnDialogBinding

class ReturnDialog(private val title: String, private val content: String) : DialogFragment() {
    private var _binding: ReturnDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ReturnDialogBinding.inflate(inflater, container, false)
        val view = binding.root

        // 제목 설정
        binding.titleText.text = title

        // 내용 설정
        binding.contentText.text = content

        isCancelable = false

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}