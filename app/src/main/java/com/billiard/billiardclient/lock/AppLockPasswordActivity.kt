package com.billiard.billiardclient.lock

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.billiard.billiardclient.MainActivity
import com.billiard.billiardclient.SettingActivity
import com.billiard.billiardclient.databinding.ActivityAppLockPasswordBinding

class AppLockPasswordActivity : AppCompatActivity() {

    private var _binding: ActivityAppLockPasswordBinding? = null
    private val binding get() = _binding!!

    private var oldPwd = ""
    private var changePwdUnlock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAppLockPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.correctBtn.setOnClickListener {
            inputType(intent.getIntExtra("type", 0))
        }

        binding.cancelBtn.setOnClickListener {
            // MainActivity 로 이동
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun inputType(type: Int) {
        when (type) {
            AppLockConst.ENABLE_PASSLOCK -> {   // 잠금 설정
                if (oldPwd.isEmpty()) {
                    oldPwd = binding.passwordEt.text.toString()
                    binding.passwordEt.setText("")
                    binding.noticeTv.text = "다시 한 번 입력"
                } else {
                    if (oldPwd == binding.passwordEt.text.toString()) {
                        AppLock(this).setPassLock(binding.passwordEt.text.toString())
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        binding.passwordEt.setText("")
                        oldPwd = ""
                        binding.noticeTv.text = "비밀번호 입력"
                    }
                }
            }
            AppLockConst.DISABLE_PASSLOCK -> {  // 잠금 해제
                if (AppLock(this).isPassLockSet()) {
                    if (AppLock(this).checkPassLock(binding.passwordEt.text.toString())) {
                        AppLock(this).removePassLock()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        binding.noticeTv.text = "비밀번호가 틀립니다."
                        binding.passwordEt.setText("")
                    }
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            AppLockConst.UNLOCK_PASSWORD -> {
                if (AppLock(this).checkPassLock(binding.passwordEt.text.toString())) {
                    setResult(Activity.RESULT_OK)
                    val intent = Intent(this, SettingActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    binding.noticeTv.text = "비밀번호가 틀립니다."
                    binding.passwordEt.setText("")
                }
            }
            AppLockConst.CHANGE_PASSWORD -> {   // 비밀번호 변경
                if (AppLock(this).checkPassLock(binding.passwordEt.text.toString()) && !changePwdUnlock) {
                    binding.passwordEt.setText("")
                    changePwdUnlock = true
                    binding.noticeTv.text = "새로운 비밀번호 입력"
                } else if (changePwdUnlock) {
                    if (oldPwd.isEmpty()) {
                        oldPwd = binding.passwordEt.text.toString()
                        binding.passwordEt.setText("")
                        binding.noticeTv.text = "새로운 비밀번호 다시 입력"
                    } else {
                        if (oldPwd == binding.passwordEt.text.toString()) {
                            AppLock(this).setPassLock(binding.passwordEt.text.toString())
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            binding.passwordEt.setText("")
                            oldPwd = ""
                            binding.noticeTv.text = "현재 비밀번호 다시 입력"
                            changePwdUnlock = false
                        }
                    }
                } else {
                    binding.noticeTv.text = "비밀번호가 틀립니다."
                    changePwdUnlock = false
                    binding.passwordEt.setText("")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}