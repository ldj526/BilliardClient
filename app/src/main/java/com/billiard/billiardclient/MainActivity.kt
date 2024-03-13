package com.billiard.billiardclient

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.DialogInterface
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
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.billiard.billiardclient.databinding.ActivityMainBinding
import com.billiard.billiardclient.lock.AppLock
import com.billiard.billiardclient.lock.AppLockConst
import com.billiard.billiardclient.lock.AppLockPasswordActivity
import com.billiard.billiardclient.utils.CustomDialog
import com.billiard.billiardclient.utils.CustomProgressDialog
import com.billiard.billiardclient.utils.ReturnDialog
import com.billiard.billiardclient.utils.TimeUtils
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

    private lateinit var binding: ActivityMainBinding
    private var time: Long = 0
    private var timerTask: Timer? = null
    private var statusTimerTask: Timer? = null
    private var totalGameCount = 1
    private var gameCount = 0
    private var totalTimeHour = 0
    private var totalTimeMinutes = 0
    private var hour = 0
    private var minute = 0
    var startTimeD: Long = 0
    var endTimeD: Long = 0

    lateinit var tableNumber: SharedPreferences
    lateinit var ipAddress: SharedPreferences
    private lateinit var socket: Socket

    private var backgroundCode = 0
    private var pressTime = 0L

    lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    var cameraProvider: ProcessCameraProvider? = null

    private var functionName = ""

    var returnDialog: ReturnDialog? = null

    private val communicationList = mutableListOf<CommunicationData>()

    var startState = true
    var endState = true

    lateinit var soundPool: SoundPool
    var startSound = 0
    var endSound = 0

    var alertDialog: AlertDialog? = null

    companion object {
        private const val LIMIT_TIME = 3.0
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val funcNameList = arrayListOf("START", "END", "POLL", "ENDGAME", "CLEAR")

        for (i in funcNameList.indices) {
            communicationList.add(CommunicationData(funcNameList[i]))
        }

        // 화면 항상 켜짐 상태
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewFinder = binding.viewFinder

//        returnDialog = ReturnDialog("정산 중..", "카운터로 가서 계산하세요.")

        socket = Socket()

        setFullScreen()

        removeFileDate()

        tcpConnect()

        // 사운드 추가
        soundPool = SoundPool.Builder().build()
        startSound = soundPool.load(this, R.raw.sound1, 1)
        endSound = soundPool.load(this, R.raw.sound2, 1)

        CoroutineScope(Dispatchers.IO).launch {
            processCommunication()
        }

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
                if ((System.currentTimeMillis() - pressTime) >= 3000) {
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
            displayCurState("전송 중..")
            when (backgroundCode) {
                0 -> {
                    tcpConnect()
                }
                1 -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        dataSend("START", totalGameCount.toString())
                    }
                    handleStart()
                    communicationList.find { it.funcName == "START" }?.sendTime = startTimeD
                }
                2 -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        dataSend(
                            "END",
                            totalGameCount.toString(),
                            "${String.format("%02d", hour)}${String.format("%02d", minute)}"
                        )
                    }
                    handleEnd()
                    communicationList.find { it.funcName == "END" }?.sendTime = endTimeD

                }
            }
        }
    }

    private fun handleStart() {
        startState = false
        startCamera()
        takePhoto()
        soundPool.play(startSound, 1.0f, 1.0f, 0, 0, 1.0f)
        startTimeD = System.currentTimeMillis()
        runOnUiThread {
            binding.startBtn.isEnabled = false
            Handler().postDelayed({
                cameraProvider!!.unbindAll()
            }, 3000)
        }
    }

    private fun handleEnd() {
        endState = false
        runOnUiThread {
            binding.startBtn.isEnabled = false
        }
        timerTask?.cancel()
        binding.colonText.clearAnimation()
        soundPool.play(endSound, 1.0f, 1.0f, 0, 0, 1.0f)
        if (endTimeD == 0L) {
            endTimeD = System.currentTimeMillis()
        }
        getTotalGameTime()
        hour = 0
        minute = 0
        time = 0 // 시간저장 변수 초기화
    }

    private fun processCommunication() {
        while (true) {
            for (i in communicationList.indices) {
                if (communicationList[i].receiveTime == 0L) {
                    receiveTimeNotExist(
                        communicationList[i].funcName,
                        communicationList[i].sendTime
                    )
                } else {
                    receiveTimeExist(communicationList[i].funcName)
                }
                communicationList[i].sendTime = 0L
                communicationList[i].receiveTime = 0L
                communicationList[i].data = ""
            }
            Thread.yield()
            Thread.sleep(1000)
        }
    }

    private fun receiveTimeNotExist(funcName: String, sendTime: Long) {
        if (sendTime != 0L) {   // 데이터를 보냈지만 서버로부터 응답이 없을 경우
            Log.d("TEST", "receiveNot")
            if (checkReceiveTime(
                    funcName,
                    sendTime
                )
            ) {
                when (funcName) {
                    "START" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            startTimer()
                        }
                        displayCurState("")
                    }
                    "END" -> {
                        timerTask?.cancel()
                        displayTotal()
                        endDialog()
                    }
                }
                runOnUiThread {
                    binding.startBtn.isEnabled = true
                }
                functionName = ""
            } else {
                runOnUiThread {
                    val customProgressDialog = CustomProgressDialog.newInstance("접속 중..")
                    customProgressDialog.show(supportFragmentManager, "CustomDialog")
                    binding.startBtn.isEnabled = true
                }
                displayCurState("네트워크 오류")
                tcpConnect()
            }
        }
    }

    private fun receiveTimeExist(funcName: String) {
        when (funcName) {
            "START" -> {
                if (startState) {
                    handleStart()
                }
                startState = true
                startTimer()
                displayCurState("")
                runOnUiThread {
                    binding.startBtn.isEnabled = true
                }
                functionName = ""
            }
            "END" -> {
                if (endState) {
                    handleEnd()
                }
                displayTotal()
                endState = true
                endDialog()
                runOnUiThread {
                    binding.startBtn.isEnabled = true
                }
                functionName = ""
            }
            "ENDGAME" -> {
                alertDialog?.dismiss()
                returnDialog?.dismiss()
                timerClear()
                runOnUiThread {
                    binding.totalHourTimeTv.text = "00"
                    binding.totalMinutesTimeTv.text = "00"
                    binding.gameCountTv.text = "0"
                    binding.startBtn.isEnabled = true
                }
                totalTimeHour = 0
                totalTimeMinutes = 0
                gameCount = 0
            }
            "POLL" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    dataSend("STATUS")
                }
            }
            "CLOSE" -> {
                totalGameCount = 0
            }
        }
    }

    private fun endDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("게임을 더 하시겠습니까?")
            .setPositiveButton("네", DialogInterface.OnClickListener { dialog, which ->
                displayCurState("")
                runOnUiThread {
                    binding.startBtn.isEnabled = true
                }
                timerClear()
            })
            .setNegativeButton("아니오", DialogInterface.OnClickListener { dialog, which ->
                CoroutineScope(Dispatchers.IO).launch {
                    dataSend(
                        "ENDGAME",
                        gameCount.toString(),
                        "${String.format("%02d", totalTimeHour)}${
                            String.format(
                                "%02d",
                                totalTimeMinutes
                            )
                        }"
                    )
                }
                displayCurState("")
                returnDialog = ReturnDialog("정산 중..", "카운터로 가서 계산하세요.")
                returnDialog?.show(supportFragmentManager, "ReturnDialog")
                functionName = ""
            })
        totalGameCount++
        runOnUiThread {
            alertDialog = builder.create()
            alertDialog?.show()
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
                    showDialog("호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)")
                }
            } catch (ioe: IOException) { // 소켓 생성 과정에서 I/O 에러 발생.
                Log.e(TAG, "생성 Error : 네트워크 응답 없음")

                runOnUiThread {
                    showDialog("네트워크 응답 없음")
                }
            } catch (se: SecurityException) { // security manager에서 허용되지 않은 기능 수행.
                Log.e(
                    TAG,
                    "생성 Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)"
                )

                runOnUiThread {
                    showDialog("보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)")
                }
            } catch (le: IllegalArgumentException) { // 소켓 생성 시 전달되는 포트 번호(65536)이 허용 범위(0~65535)를 벗어남.
                Log.e(
                    TAG,
                    " 생성 Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)"
                )

                runOnUiThread {
                    showDialog("메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)")
                }
            }
        }
    }

    // CustomDialog 띄우기
    private fun showDialog(message: String) {
        if (!isFinishing && !isDestroyed) {
            displayCurState("네트워크 오류")
            val customDialog = CustomDialog.newInstance(message)
            customDialog.show(supportFragmentManager, "CustomDialog")
        }
    }

    // tcp를 통해 네트워크 연결
    private fun tcpConnect() {
        tableNumber = getSharedPreferences("number", MODE_PRIVATE)
        ipAddress = getSharedPreferences("ip", MODE_PRIVATE)
        val addr = "${ipAddress.getString("ip", "NULL")}"
        Log.d(TAG, addr)
        val thread = ConnectThread(addr)
        thread.start()
    }

    // 데이터 수신
    private fun dataReceive() {
        try {
            while (true) {
                Log.d("dataReceive", "데이터 수신 준비")
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
                    val receiveTimeD = System.currentTimeMillis()
                    communicationList.find { it.funcName == functionName }?.receiveTime =
                        receiveTimeD
                    communicationList.find { it.funcName == functionName }?.data = tmp
                }

                Thread.sleep(10)    // Thread 일시 정지
                Thread.yield()      // 다른 스레드에게 양보
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "수신 에러")
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
        runOnUiThread {
            binding.startBtn.setBackgroundResource(R.drawable.end_button_ripple)
            val anim = AnimationUtils.loadAnimation(this, R.anim.blink_animation)
            binding.colonText.startAnimation(anim)
        }
        backgroundCode = 2
        time = 0
        timerTask =
            timer(period = 500) { //반복주기는 peroid 프로퍼티로 설정, 단위는 1000분의 1초 (period = 1000, 1초)
                val curTimeD = System.currentTimeMillis()
                time = (curTimeD - startTimeD) / 1000 / 60
                hour = (time / 60).toInt() // 나눗셈의 몫 (시간 부분)
                minute = (time % 60).toInt() // 나눗셈의 나머지 (분 부분)

                runOnUiThread {
                    binding.hourTensText.text = String.format("%01d", hour / 10)
                    binding.hourUnitsText.text = String.format("%01d", hour % 10)
                    binding.minuteTensText.text = String.format("%01d", minute / 10)
                    binding.minuteUnitsText.text = String.format("%01d", minute % 10)
                }
            }
    }

    // Timer stop
    private fun displayTotal() {
        gameCount++
        runOnUiThread {
            binding.gameCountTv.text = gameCount.toString()
            binding.totalHourTimeTv.text = String.format("%02d", totalTimeHour)
            binding.totalMinutesTimeTv.text = String.format("%02d", totalTimeMinutes)
        }
    }

    private fun timerClear() {
        runOnUiThread {
            binding.startBtn.setBackgroundResource(R.drawable.start_button_ripple)
            backgroundCode = 1
            // 시간초기화
            binding.hourTensText.text = "0"
            binding.hourUnitsText.text = "0"
            binding.minuteTensText.text = "0"
            binding.minuteUnitsText.text = "0"
        }
        endTimeD = 0L
    }

    // 데이터 보낸 시간과 받은 시간을 비교해 데이터 처리
    private fun checkReceiveTime(funcName: String, sendTime: Long): Boolean {
        var curTime = System.currentTimeMillis()
        while ((curTime - sendTime) / 1000 <= LIMIT_TIME) {
            curTime = System.currentTimeMillis()
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
    private fun getTotalGameTime() {
        totalTimeMinutes += minute
        val exceedMinutes = totalTimeMinutes / 60
        totalTimeMinutes %= 60
        totalTimeHour += hour + exceedMinutes
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
            val cal = Calendar.getInstance()
            val todayMil = cal.timeInMillis // 현재단위 밀리세컨드
            val oneDayMil = 20 * 60 * 60 * 1000 // 일 단위
            val fileCal = Calendar.getInstance()
            var fileDate: Date
            val path = Environment.getExternalStoragePublicDirectory("Pictures/Billiard")
            val list = path.listFiles()

            for (i in list.indices) {
                // 파일 마지막 수정시간
                fileDate = Date(list[i].lastModified())
                // 현재시간과 파일수정시간 시간차 계산
                fileCal.time = fileDate
                //밀리세컨드로 계산
                val diffMil = todayMil - fileCal.timeInMillis
                // 날짜 변경
                val diffDay = (diffMil / oneDayMil).toInt()
                if (diffDay >= 7 && list[i].exists()) {
                    list[i].delete()
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
    }
}