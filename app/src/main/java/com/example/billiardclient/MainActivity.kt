package com.example.billiardclient

import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.billiardclient.databinding.ActivityMainBinding
import java.io.IOException
import java.net.Socket
import java.net.UnknownHostException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        socket = Socket()

        setFullScreen()

        // 사운드 추가
        val soundPool = SoundPool.Builder().build()
        val startSound = soundPool.load(this, R.raw.sound1, 1)
        val endSound = soundPool.load(this, R.raw.sound2, 1)

        tableNumber = getSharedPreferences("number", MODE_PRIVATE)
        ipAddress = getSharedPreferences("ip", MODE_PRIVATE)

        tcpConnect()

        // Setting 화면에서 입력한 table 번호 가져오기
        val text = "${tableNumber.getString("number", "NULL")}"
        binding.tableNumber.text = text

        // 테이블 번호 LongClick 시 Setting 화면으로 이동
        binding.tableNumber.setOnLongClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
            true
        }

        // start 버튼 클릭 시 실행 여부에 따른 start stop 실행
        binding.startBtn.setOnClickListener {
            if (binding.startBtn.text == "START") {
                takePhoto()
                soundPool.play(startSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val startThread = StartThread()
                startThread.start()
            } else {
                soundPool.play(endSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val stopThread = StopThread()
                stopThread.start()
            }
        }

        // 권한에 따른 카메라 실행
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
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
                "END",
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
                if (tmp.contains("0d")) {
                    tmp2 = String(buffer)
                    Log.d(TAG, tmp2)
                    val token = tmp2.split(' ')     // 받아온 신호 space로 구분하기
                    Log.d(TAG, token[2])

//                    if (tmp2.length - token[0].length != 19) continue

                    checkFunc(token[2])
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
                Log.d(TAG, "스타트!!!!!")
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
        val formatter = SimpleDateFormat("yyMMddHHmmss", Locale.KOREA)
        val calendar = Calendar.getInstance()
        formatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return formatter.format(calendar.time)
    }


    // Timer start
    private fun start() {
        totalGameCount++
        runOnUiThread {
            binding.startBtn.text = "E N D"
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
        gameCount++
        runOnUiThread {
            getGameTime()
            binding.gameCountTv.text = gameCount.toString()
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

    // 앱을 전체화면으로 만들기
    private fun setFullScreen() {
        // R 버전 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 상단 ActionBar 제거
            supportActionBar?.hide()

            /* True -> 내부적으로 SYSTEM UI LAYOUT FLAG 값들을 살펴보고
            해당 설정 값들을 토대로 화면을 구성하게 된다.
            따라서 False로 설정을 해주어야 이제 사라질(Deprecated) Flag 값들을
            무시하고 Window Insets를 통해 화면을 구상하게 된다.
             */
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                // 상태바와 네비게이션 사라지게 한다.
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                // Swipe 했을 경우에만 시스템 바 보이게 설정
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        }
        // R 버전 이하
        else {
            supportActionBar?.hide()
            // 전체 화면 적용
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    // 컨텐츠를 시스템 바 밑에 보이도록
                    // 시스템바가 숨겨지거나 보여질 때 컨텐츠 부분이 리사이징 되는 것을 막기 위함
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // 네비게이션과 상태바 사라지게
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    // 사진 저장
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // 카메라 시작
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // 카메라 권한
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 권한 설정했으면 카메라 실행 / 아니면 Toast 메세지
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
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
        cameraExecutor.shutdown()
        _binding = null
    }
}