package com.billiard.billiardclient.lock

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.billiard.billiardclient.SettingActivity
import com.billiard.billiardclient.databinding.ActivityAppLockSettingBinding

class AppLockSettingActivity : AppCompatActivity() {

    private var _binding: ActivityAppLockSettingBinding? = null
    private val binding get() = _binding!!

    private var lock = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAppLockSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()

        binding.backImg.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        binding.setLockBtn.setOnClickListener {
            val intent = Intent(this, AppLockPasswordActivity::class.java).apply {
                putExtra(AppLockConst.type, AppLockConst.ENABLE_PASSLOCK)
            }
            startActivityForResult(intent, AppLockConst.ENABLE_PASSLOCK)
        }

        binding.setDelLockBtn.setOnClickListener {
            val intent = Intent(this, AppLockPasswordActivity::class.java).apply {
                putExtra(AppLockConst.type, AppLockConst.DISABLE_PASSLOCK)
            }
            startActivityForResult(intent, AppLockConst.DISABLE_PASSLOCK)
        }

        binding.changePwdBtn.setOnClickListener {
            val intent = Intent(this, AppLockPasswordActivity::class.java).apply {
                putExtra(AppLockConst.type, AppLockConst.CHANGE_PASSWORD)
            }
            startActivityForResult(intent, AppLockConst.CHANGE_PASSWORD)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            AppLockConst.ENABLE_PASSLOCK -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "암호 설정 됨", Toast.LENGTH_SHORT).show()
                    init()
                }
            }
            AppLockConst.DISABLE_PASSLOCK -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "암호 삭제 됨", Toast.LENGTH_SHORT).show()
                    init()
                }
            }
            AppLockConst.CHANGE_PASSWORD -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "암호 변경 됨", Toast.LENGTH_SHORT).show()
                    init()
                }
            }
            AppLockConst.UNLOCK_PASSWORD -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "잠금 해제 됨", Toast.LENGTH_SHORT).show()
                    init()
                }
            }
        }
    }

    private fun init() {
        if (AppLock(this).isPassLockSet()) {
            binding.setLockBtn.isEnabled = false
            binding.setDelLockBtn.isEnabled = true
            binding.changePwdBtn.isEnabled = true
            lock = true
        } else {
            binding.setLockBtn.isEnabled = true
            binding.setDelLockBtn.isEnabled = false
            binding.changePwdBtn.isEnabled = false
            lock = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}