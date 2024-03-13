package com.billiard.billiardclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BroadCastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            startService(context!!)
        }
    }

    private fun startService(context: Context) {
        val appIntent = Intent(context, BootService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(appIntent)
        } else {
            context.startService(appIntent)
        }
    }
}