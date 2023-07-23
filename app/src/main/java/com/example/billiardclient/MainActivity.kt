package com.example.billiardclient

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.billiardclient.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.timer


class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private var isRunning = false
    private var time = 0
    private var timerTask: Timer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startBtn.setOnClickListener {
            isRunning = !isRunning
            if (isRunning) start() else stop()
        }
    }

    private fun start() {
        binding.startBtn.text = "종료"
        timerTask =
            timer(period = 60000) { //반복주기는 peroid 프로퍼티로 설정, 단위는 1000분의 1초 (period = 1000, 1초)
                val hour = time / 60 // 나눗셈의 몫 (시간 부분)
                val minute = time % 60 // 나눗셈의 나머지 (분 부분)

                time++ // period = 60000으로 1분마다 time를 1씩 증가하게 됩니다

                // UI조작을 위한 메서드
                runOnUiThread {
                    binding.hourText.text = String.format("%02d", hour)
                    binding.minuteText.text = String.format("%02d", minute)
                }
            }
    }

    private fun stop() {
        timerTask?.cancel() // timerTask가 null이 아니라면 cancel() 호출

        time = 0 // 시간저장 변수 초기화
        isRunning = false // 현재 진행중인지 판별하기 위한 Boolean변수 false 세팅
        binding.hourText.text = "00" // 시간(시간) 초기화
        binding.minuteText.text = "00" // 시간(분) 초기화
        binding.startBtn.text = "시작"
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}