package com.fairy.agypc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

class DesktopService : Service() {
    companion object {
        @Volatile var proc: Process? = null
        private val logs = ConcurrentLinkedQueue<String>()
        fun isRunning() = proc?.isAlive == true
        fun drain(): List<String> {
            val l = ArrayList<String>()
            while (true) l.add(logs.poll() ?: break)
            return l
        }
    }

    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("agy", "Desktop", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)
        val n = Notification.Builder(this, "agy")
            .setContentTitle("Antigravity PC chal raha hai")
            .setContentText("Tap karke desktop kholo")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pi).setOngoing(true).build()
        startForeground(1, n)
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "agypc:desktop").also { it.acquire() }
        if (!isRunning()) thread {
            try {
                val p = Env(this).prootCmd("/bin/bash", "/root/start-desktop.sh").start()
                proc = p
                p.inputStream.bufferedReader().forEachLine {
                    logs.add(it); while (logs.size > 500) logs.poll()
                }
                logs.add("Desktop process band ho gaya (exit ${p.waitFor()})")
            } catch (e: Throwable) {
                logs.add("START ERROR: ${e.message}")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        proc?.destroy(); proc = null
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
