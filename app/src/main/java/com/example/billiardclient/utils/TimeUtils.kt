package com.example.billiardclient.utils

import java.text.SimpleDateFormat
import java.util.*

class TimeUtils {
    fun getTime(): String {
        val formatter = SimpleDateFormat("yyMMddHHmmss", Locale.KOREA)
        val calendar = Calendar.getInstance()
        formatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return formatter.format(calendar.time)
    }
}