package com.billiard.billiardclient

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.billiard.billiardclient.databinding.ActivitySettingBinding
import com.billiard.billiardclient.lock.AppLockSettingActivity

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

        // 저장 버튼 클릭 시
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
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        // 취소 버튼 클릭 시
        binding.cancelBtn.setOnClickListener {
            // MainActivity 로 이동
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        // 비밀번호 세팅 화면으로 이동
        binding.settingPwd.setOnClickListener {
            val intent = Intent(this, AppLockSettingActivity::class.java)
            startActivity(intent)
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