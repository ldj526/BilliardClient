package com.example.billiardclient

import android.content.ContentValues.TAG
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.billiardclient.databinding.ActivityMainBinding
import java.io.IOException
import java.net.Socket
import java.net.UnknownHostException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private var time = 0
    private var timerTask: Timer? = null
    private var statusTimerTask: Timer? = null
    lateinit var tableNumber: SharedPreferences
    lateinit var ipAddress: SharedPreferences
    private lateinit var socket: Socket
    private var totalGameCount = 0
    private var gameCount = 0
    private var totalTimeHour = 0
    private var totalTimeMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        socket = Socket()

        tableNumber = getSharedPreferences("number", MODE_PRIVATE)
        ipAddress = getSharedPreferences("ip", MODE_PRIVATE)

        binding.connectBtn.setOnClickListener {
            Toast.makeText(applicationContext, "Connect 시도", Toast.LENGTH_SHORT).show()
            tcpConnect()
        }

        // Setting 화면에서 입력한 table 번호 가져오기
        val text = "${tableNumber.getString("number", "NULL")}"
        binding.tableNumber.text = text

        // 테이블 번호 LongClick 시 Setting 화면으로 이동
        binding.tableNumber.setOnLongClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
            true
        }

        // (임시) start 버튼 클릭 시 실행 여부에 따른 start stop 실행
        binding.startBtn.setOnClickListener {
            if (binding.startBtn.text == "START") {
                val startThread = StartThread()
                startThread.start()
            } else {
                val stopThread = StopThread()
                stopThread.start()
            }
        }

        // (임시) socket 연결 끊기
        binding.disconnectBtn.setOnClickListener {
            try {
                socket.close()
                Toast.makeText(applicationContext, "DisConnect", Toast.LENGTH_SHORT).show()
                binding.disconnectBtn.isEnabled = false
                binding.connectBtn.isEnabled = true
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(applicationContext, "DisConnect 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Start 버튼 클릭 시 데이터 송신.
    inner class StartThread : Thread() {
        override fun run() {
            dataSend("START", totalGameCount.toString())
        }
    }

    // Stop 버튼 클릭 시 데이터 송신.
    inner class StopThread : Thread() {
        override fun run() {
            dataSend(
                "STOP",
                totalGameCount.toString(),
                "${binding.hourText.text}${binding.minuteText.text}"
            )
        }
    }

    // Connect 버튼 클릭 시 연결/데이터 송신
    inner class ConnectThread(var hostname: String) : Thread() {
        override fun run() {
            try { //클라이언트 소켓 생성
                val port = 7777
                socket = Socket(hostname, port)
                Log.d(TAG, "Socket 생성, 연결.")
                runOnUiThread(Runnable {
                    val addr = socket.inetAddress
                    val tmp = addr.hostAddress
                    Toast.makeText(applicationContext, "Connected", Toast.LENGTH_LONG).show()
                })

                // 1분마다 Server로 신호를 보내줌
                statusTimerTask = timer(period = 60000) {
                    time++
                    dataSend("STATUS")
                }

                dataReceive()

            } catch (uhe: UnknownHostException) { // 소켓 생성 시 전달되는 호스트(www.unknown-host.com)의 IP를 식별할 수 없음.
                Log.e(TAG, " 생성 Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)")
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)")
                }
            } catch (ioe: IOException) { // 소켓 생성 과정에서 I/O 에러 발생.
                Log.e(TAG, " 생성 Error : 네트워크 응답 없음")
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Error : 네트워크 응답 없음",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "네트워크 연결 오류")
                }
            } catch (se: SecurityException) { // security manager에서 허용되지 않은 기능 수행.
                Log.e(
                    TAG,
                    " 생성 Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)"
                )
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(
                        TAG,
                        "Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)"
                    )
                }
            } catch (le: IllegalArgumentException) { // 소켓 생성 시 전달되는 포트 번호(65536)이 허용 범위(0~65535)를 벗어남.
                Log.e(
                    TAG,
                    " 생성 Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)"
                )
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        " Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(
                        TAG,
                        "Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)"
                    )
                }
            }
        }
    }

    // tcp를 통해 네트워크 연결
    private fun tcpConnect() {
        val addr = "${ipAddress.getString("ip", "NULL")}"
        Log.d(TAG, addr)
        val thread = ConnectThread(addr)
        thread.start()
    }

    // 데이터 수신
    private fun dataReceive() {
        try {
            Log.d(TAG, "데이터 수신 준비")
            while (true) {
                var bytes: Int  // 단어 개수
                var tmp: String?    // 단어
                var tmp2: String?
                val buffer = ByteArray(1024)
                val input = socket.getInputStream()
                bytes = input.read(buffer)
                Log.d(TAG, "byte = $bytes")

                tmp = byteArrayToHex(buffer)
                tmp = tmp.substring(0, bytes * 3)
                Log.d(TAG, tmp)

                // carriage return이 들어오면 server로 답주기
                if (tmp.contains("33")) {       // cr 임시로 3 으로
                    tmp2 = String(buffer)
                    Log.d(TAG, tmp2)
                    val token = tmp2.split(' ')     // 받아온 신호 space로 구분하기
                    Log.d(TAG, token[0])

//                    if (tmp2.length - token[0].length != 19) continue

                    checkFunc(token[0])
                }

                Thread.sleep(10)    // Thread 일시 정지
                Thread.yield()      // 다른 스레드에게 양보
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "수신 에러")
        }
    }

    // 받아온 정보의 Function에 대해 처리
    private fun checkFunc(name: String) {
        when (name) {
            "POLL" -> {
                dataSend("STATUS")
            }
            "START" -> {    // 강제로 timer 시작
                start()
            }
            "END" -> {      // 강제로 timer 종료
                stop()
            }
            "RESET" -> {
                runOnUiThread {
                    binding.totalHourTimeTv.text = "00"
                    binding.totalMinutesTimeTv.text = "00"
                    binding.gameCountTv.text = "0"
                }
            }
            "CLOSE" -> {
                totalGameCount = 0
            }
        }
    }

    // 데이터 송신
    private fun dataSend(funcName: String, totalGame: String = "", timeData: String = "") {
        try {
            // 세 자리 수가 안될 때 앞에 0을 추가해 줌
            val tableNumberFormat =
                tableNumber.getString("number", "NULL").toString().padStart(3, '0')
            val totalGameFormat = totalGame.padStart(3, '0')

            val outData =
                "$tableNumberFormat ${getTime()} $funcName $totalGameFormat ${timeData}${Char(13)}"
            val data = outData.toByteArray()
            val output = socket.getOutputStream()
            output.write(data)
            Log.d(TAG, "$funcName\\n COMMAND 송신")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "데이터 송신 오류")
        }
    }

    // byte를 Hexa로 변환
    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder()
        for (b in a) sb.append(String.format("%02x ", b.toInt()))   // and 0xff
        return sb.toString()
    }

    // 현재 시각 가져오기
    private fun getTime(): String {
        val formatter = SimpleDateFormat("yyMMdd HHmmss", Locale.KOREA)
        val calendar = Calendar.getInstance()
        formatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return formatter.format(calendar.time)
    }


    // Timer start
    private fun start() {
        totalGameCount++
        gameCount++
        runOnUiThread {
            binding.startBtn.text = "E N D"
            binding.gameCountTv.text = gameCount.toString()
            time = 0
            timerTask =
                timer(period = 600) { //반복주기는 peroid 프로퍼티로 설정, 단위는 1000분의 1초 (period = 1000, 1초)
                    val hour = time / 60 // 나눗셈의 몫 (시간 부분)
                    val minute = time % 60 // 나눗셈의 나머지 (분 부분)

                    time++ // period = 60000으로 1분마다 time를 1씩 증가하게 됩니다

                    runOnUiThread {
                        binding.hourText.text = String.format("%02d", hour)
                        binding.minuteText.text = String.format("%02d", minute)
                    }
                }
        }
    }

    // Timer stop
    private fun stop() {
        runOnUiThread {
            getGameTime()
            timerTask?.cancel() // timerTask가 null이 아니라면 cancel() 호출

            time = 0 // 시간저장 변수 초기화
            binding.hourText.text = "00" // 시간(시간) 초기화
            binding.minuteText.text = "00" // 시간(분) 초기화
            binding.startBtn.text = "START"
        }
    }

    // 계산 전까지의 모든 게임 시간을 합쳐주는 기능
    private fun getGameTime() {
        // 한자리만 나왔을 경우 십의 자리를 0으로 바꿔주는 포맷
        val df = DecimalFormat("00")

        totalTimeMinutes += binding.minuteText.text.toString().toInt()
        val exceedMinutes = totalTimeMinutes / 60
        totalTimeMinutes %= 60
        totalTimeHour = binding.hourText.text.toString().toInt() + exceedMinutes

        // format 변환
        val totalTimeHourFormat = df.format(totalTimeHour)
        val totalTimeMinutesFormat = df.format(totalTimeMinutes)

        binding.totalHourTimeTv.text = totalTimeHourFormat
        binding.totalMinutesTimeTv.text = totalTimeMinutesFormat
    }

    // 앱 종료 시
    override fun onStop() {
        super.onStop()
        try {
            socket.close() //소켓을 닫는다.
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}