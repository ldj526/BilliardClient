package com.example.billiardclient

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
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
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.billiardclient.databinding.ActivityMainBinding
import com.example.billiardclient.lock.AppLock
import com.example.billiardclient.lock.AppLockConst
import com.example.billiardclient.lock.AppLockPasswordActivity
import com.example.billiardclient.utils.CustomDialog
import com.example.billiardclient.utils.CustomProgressDialog
import com.example.billiardclient.utils.TimeUtils
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
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
    private var hour = 0
    private var minute = 0
    lateinit var viewFinder: PreviewView
    private var backgroundCode = 0
    private var pressTime = 0L

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var functionName = ""

    var startTime = ""
    var endTime = ""

    var cameraProvider: ProcessCameraProvider? = null

    companion object {
        private const val LIMIT_TIME = 1.0
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewFinder = binding.viewFinder

        socket = Socket()

        setFullScreen()

        removeFileDate()

        // 사운드 추가
        val soundPool = SoundPool.Builder().build()
        val startSound = soundPool.load(this, R.raw.sound1, 1)
        val endSound = soundPool.load(this, R.raw.sound2, 1)

        tableNumber = getSharedPreferences("number", MODE_PRIVATE)
        ipAddress = getSharedPreferences("ip", MODE_PRIVATE)

        // 권한에 따른 카메라 실행
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setting 화면에서 입력한 table 번호 가져오기
        val text = "${tableNumber.getString("number", "NULL")}"
        binding.tableNumber.text = text

        // 테이블 번호 LongClick 시 Setting 화면으로 이동
        binding.tableNumber.setOnTouchListener { v, event ->
            // 잠금이 걸려있으면 비밀번호 화면으로
            if (event.action == MotionEvent.ACTION_DOWN) {
                pressTime = System.currentTimeMillis()
            } else if (event.action == MotionEvent.ACTION_UP) {
                if ((System.currentTimeMillis() - pressTime) >= 5000) {
                    if (AppLock(this).isPassLockSet()) {
                        val intent = Intent(this, AppLockPasswordActivity::class.java).apply {
                            putExtra(AppLockConst.type, AppLockConst.UNLOCK_PASSWORD)
                        }
                        startActivityForResult(intent, AppLockConst.UNLOCK_PASSWORD)
                    } else {
                        // 잠금이 없다면 바로 Setting 화면으로
                        val intent = Intent(this, SettingActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            true
        }

        // start 버튼 클릭 시 실행 여부에 따른 start stop 실행
        binding.startBtn.setOnClickListener {
            Log.d("MainActivity", backgroundCode.toString())
            displayCurState("전송 중..")
            when (backgroundCode) {
                0 -> {
                    tcpConnect()
                }
                1 -> {
                    startCamera()
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(100)
                        takePhoto()
                        soundPool.play(startSound, 1.0f, 1.0f, 0, 0, 1.0f)
                        dataSend("START", totalGameCount.toString())
                        startTime = TimeUtils().getTime()
                        if (checkReceiveTime("START", startTime)) {
                            startTimer()
                            displayCurState("")
                        } else {
                            runOnUiThread {
                                CustomProgressDialog("접속 중..").show(
                                    supportFragmentManager,
                                    "CustomDialog"
                                )
                            }
                            displayCurState("네트워크 오류")
                            tcpConnect()
                        }
                        functionName = ""
                    }
                    Handler().postDelayed(Runnable {
                        cameraProvider!!.unbindAll()
                    }, 3000)

                }
                2 -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        soundPool.play(endSound, 1.0f, 1.0f, 0, 0, 1.0f)
                        timerTask?.cancel()
                        getGameTime()
                        dataSend(
                            "END",
                            totalGameCount.toString(),
                            "${String.format("%02d", totalTimeHour)}${
                                String.format(
                                    "%02d",
                                    totalTimeMinutes
                                )
                            }"
                        )
                        endTime = TimeUtils().getTime()
                        if (checkReceiveTime("END", endTime)) {
                            stopTimer()
                            displayCurState("")
                        } else {
                            runOnUiThread {
                                CustomProgressDialog("접속 중..").show(
                                    supportFragmentManager,
                                    "CustomDialog"
                                )
                            }
                            displayCurState("네트워크 오류")
                            tcpConnect()
                        }
                        functionName = ""
                    }
                }
            }
        }
    }

    // Connect 버튼 클릭 시 연결/데이터 송신
    inner class ConnectThread(var hostname: String) : Thread() {
        override fun run() {
            try { //클라이언트 소켓 생성
                val port = 7777
                socket = Socket()
                socket.connect(InetSocketAddress(hostname, port), 1000)
                Log.d(TAG, "소켓 연결")

                if (backgroundCode == 0) {
                    runOnUiThread {
                        binding.startBtn.setBackgroundResource(R.drawable.start_button_ripple)
                    }
                    backgroundCode = 1
                }

                // 1분마다 Server로 신호를 보내줌
                statusTimerTask = timer(period = 60000) {
                    dataSend("STATUS")
                }

                displayCurState("")

                dataReceive()

            } catch (uhe: UnknownHostException) { // 소켓 생성 시 전달되는 호스트(www.unknown-host.com)의 IP를 식별할 수 없음.
                Log.e(TAG, "생성 Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)")
                uhe.printStackTrace()

                runOnUiThread {
                    displayCurState("네트워크 오류")
                    CustomDialog("호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)").show(
                        supportFragmentManager,
                        "CustomDialog"
                    )
                }
            } catch (ioe: IOException) { // 소켓 생성 과정에서 I/O 에러 발생.
                Log.e(TAG, "생성 Error : 네트워크 응답 없음")

                runOnUiThread {
                    displayCurState("네트워크 오류")
                    CustomDialog("네트워크 응답 없음").show(supportFragmentManager, "CustomDialog")
                }
            } catch (se: SecurityException) { // security manager에서 허용되지 않은 기능 수행.
                Log.e(
                    TAG,
                    "생성 Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)"
                )

                runOnUiThread {
                    displayCurState("네트워크 오류")
                    CustomDialog("보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)").show(
                        supportFragmentManager,
                        "CustomDialog"
                    )
                }
            } catch (le: IllegalArgumentException) { // 소켓 생성 시 전달되는 포트 번호(65536)이 허용 범위(0~65535)를 벗어남.
                Log.e(
                    TAG,
                    " 생성 Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)"
                )

                runOnUiThread {
                    displayCurState("네트워크 오류")
                    CustomDialog("메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)").show(
                        supportFragmentManager,
                        "CustomDialog"
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
                    functionName = token[2]
                    Log.d(TAG, "dataReceive에서 functionName: $functionName")

                    checkFunc(functionName)
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
                "$tableNumberFormat ${TimeUtils().getTime()} $funcName $totalGameFormat ${timeData}${
                    Char(
                        13
                    )
                }"
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

    // 네트워크의 상태를 나타내주는 text
    private fun displayCurState(curState: String) {
        runOnUiThread {
            binding.curStateTv.text = curState
        }
    }

    // Timer start
    private fun startTimer() {
        Log.d(TAG, "startTimer에서 $functionName 확인")
        totalGameCount++
        runOnUiThread {
            binding.startBtn.setBackgroundResource(R.drawable.end_button_ripple)
            backgroundCode = 2
            time = 0
            timerTask =
                timer(period = 1000) { //반복주기는 peroid 프로퍼티로 설정, 단위는 1000분의 1초 (period = 1000, 1초)
                    Log.d("TimeCheck", "$time")
                    hour = time / 60 // 나눗셈의 몫 (시간 부분)
                    minute = time % 60 // 나눗셈의 나머지 (분 부분)
                    time++

                    runOnUiThread {
                        binding.hourTensText.text = String.format("%01d", hour / 10)
                        binding.hourUnitsText.text = String.format("%01d", hour % 10)
                        binding.minuteTensText.text = String.format("%01d", minute / 10)
                        binding.minuteUnitsText.text = String.format("%01d", minute % 10)
                    }
                }
        }
    }

    // Timer stop
    private fun stopTimer() {
        Log.d(TAG, "stopTimer에서 $functionName 확인")
        gameCount++
        runOnUiThread {
            binding.gameCountTv.text = gameCount.toString()
            // 시간초기화
            binding.hourTensText.text = "0"
            binding.hourUnitsText.text = "0"
            binding.minuteTensText.text = "0"
            binding.minuteUnitsText.text = "0"
            binding.startBtn.setBackgroundResource(R.drawable.start_button_ripple)
            backgroundCode = 1
            Log.d("stopTimer()", "backgroundCode : $backgroundCode")
            binding.totalHourTimeTv.text = String.format("%02d", totalTimeHour)
            binding.totalMinutesTimeTv.text = String.format("%02d", totalTimeMinutes)
        }
        endTime = ""
    }

    // 데이터 보낸 시간과 받은 시간을 비교해 데이터 처리
    private fun checkReceiveTime(funcName: String, sendTime: String): Boolean {
        var curTime = TimeUtils().getTime()
        Log.d(TAG, "checkReceiveTime 에서 startTime : $startTime")
        Log.d(TAG, "checkReceiveTime 에서 endTime : $endTime")
        Log.d(TAG, "checkReceiveTime 에서 functionName : $functionName")
        while (curTime.toDouble() - sendTime.toDouble() <= LIMIT_TIME) {
            curTime = TimeUtils().getTime()
            if (funcName == functionName) {
                return true
            } else {
                Thread.sleep(100)
                Thread.yield()
            }
        }
        return false
    }

    // 계산 전까지의 모든 게임 시간을 합쳐주는 기능
    private fun getGameTime() {
        totalTimeMinutes += minute
        val exceedMinutes = totalTimeMinutes / 60
        totalTimeMinutes %= 60
        totalTimeHour += hour + exceedMinutes
        hour = 0
        minute = 0
        time = 0 // 시간저장 변수 초기화
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

    // 사진 기간되면 삭제
    private fun removeFileDate() {
        try {
            Log.d("FileDelete", "파일 삭제")
            val cal = Calendar.getInstance()
            val todayMil = cal.timeInMillis // 현재단위 밀리세컨드
            Log.d("FileDelete", "todayMil : $todayMil")
            val oneDayMil = 20 * 60 * 60 * 1000 // 일 단위
            val fileCal = Calendar.getInstance()
            var fileDate: Date
            val path = Environment.getExternalStoragePublicDirectory("Pictures/Billiard")
            Log.d("FileDelete", "파일 경로 : $path")
            val list = path.listFiles()

            for (i in list.indices) {
                Log.d("FileDelete", "list의 경로 ${list[i]}")
                // 파일 마지막 수정시간
                fileDate = Date(list[i].lastModified())
                Log.d("FileDelete", "파일 수정 시간 : $fileDate")
                // 현재시간과 파일수정시간 시간차 계산
                fileCal.time = fileDate
                Log.d("FileDelete", "fileCal 시간 : ${fileCal.time}")
                //밀리세컨드로 계산
                val diffMil = todayMil - fileCal.timeInMillis
                Log.d("FileDelete", "diffMill 시간 : $diffMil")
                Log.d("FileDelete", "fileCal.timeInMillis 시간 : ${fileCal.timeInMillis}")
                // 날짜 변경
                val diffDay = (diffMil / oneDayMil).toInt()
                Log.d("FileDelete", "diffDay : $diffDay")
                if (diffDay >= 0 && list[i].exists()) {
                    list[i].delete()
                    Log.d("FileDelete", "파일 삭제되나?")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("FileDelete", "파일이 존재하지 않습니다.")
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
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Billiard")
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
//                    val msg = "Photo capture succeeded: ${output.savedUri}"
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Photo capture succeeded")
                }
            }
        )
    }

    // 카메라 시작
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider!!.unbindAll()

                // Bind use cases to camera
                cameraProvider!!.bindToLifecycle(
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

    override fun onDestroy() {
        try {
            socket.close() //소켓을 닫는다.
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onDestroy()
        cameraExecutor.shutdown()
        _binding = null
    }
}