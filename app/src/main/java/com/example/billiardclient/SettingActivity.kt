package com.example.billiardclient

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.billiardclient.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity() {

    private var _binding: ActivitySettingBinding? = null
    private val binding get() = _binding!!
    lateinit var tableNumber: SharedPreferences
    lateinit var ipAddress: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tableNumber = getSharedPreferences("number", MODE_PRIVATE)
        ipAddress = getSharedPreferences("ip", MODE_PRIVATE)

        binding.saveBtn.setOnClickListener {
            // sharedPreference 저장
            tableNumber.edit {
                putString("number", binding.tableEt.text.toString())
            }
            ipAddress.edit {
                putString("ip", binding.ipEt.text.toString())
            }
            // MainActivity 로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onStart() {
        saveValueLoad()
        super.onStart()
    }

    // 저장된 값 불러오기
    private fun saveValueLoad() {
        binding.tableEt.setText(tableNumber.getString("number", ""))
        binding.ipEt.setText(ipAddress.getString("ip", ""))
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}