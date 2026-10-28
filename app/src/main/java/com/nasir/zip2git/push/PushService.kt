package com.nasir.zip2git.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nasir.zip2git.MainActivity
import com.nasir.zip2git.data.model.PushProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Foreground service: keeps the push alive when the app is in the background or the screen is off. */
class PushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            PushManager.cancel()
        }

        createChannels()
        val first = buildOngoing(PushManager.progress.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ONGOING, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ONGOING, first)
        }

        if (!started) {
            started = true
            acquireWakeLock()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            scope.launch {
                PushManager.progress.collect { p ->
                    if (PushManager.running.value) {
                        nm.notify(NOTIF_ONGOING, buildOngoing(p))
                    }
                }
            }
            scope.launch {
                PushManager.running.first { running -> !running }
                finish(nm)
            }
        }
        return START_NOT_STICKY
    }

    private fun finish(nm: NotificationManager) {
        val result = PushManager.result.value
        val ok = result != null && result.isSuccess
        val title = if (ok) "Push complete" else "Push failed"
        val text = if (result != null && result.isSuccess) {
            "${result.filesUploaded} files pushed to ${result.repoFullName}"
        } else {
            result?.errorMessage ?: "Something went wrong"
        }
        val done = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

        stopForeground(STOP_FOREGROUND_REMOVE)
        nm.notify(NOTIF_DONE, done)
        releaseWakeLock()
        stopSelf()
    }

    private fun buildOngoing(p: PushProgress): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PushService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Pushing to GitHub")
            .setContentText(p.message.ifBlank { "Working…" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", cancelIntent)

        if (p.totalFiles > 0) {
            builder.setProgress(p.totalFiles, p.completedFiles.coerceAtMost(p.totalFiles), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Push progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Push finished", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zip2git:push").apply {
                acquire(2 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.nasir.zip2git.action.CANCEL_PUSH"
        private const val CHANNEL_ONGOING = "push_ongoing"
        private const val CHANNEL_DONE = "push_done"
        private const val NOTIF_ONGOING = 1001
        private const val NOTIF_DONE = 1002
    }
}
