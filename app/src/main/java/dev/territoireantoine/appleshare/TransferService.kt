package dev.territoireantoine.appleshare

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class TransferService : Service() {
    companion object {
        const val ACTION_START = "dev.territoireantoine.appleshare.START"
        const val ACTION_STOP = "dev.territoireantoine.appleshare.STOP"
        const val CHANNEL_ID = "transfers"
    }

    private var server: LocalTransferServer? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Partage local", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val openApp = PendingIntent.getActivity(this, 101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenDrop S23")
            .setContentText("Partage local actif. Arrêtez-le depuis l'application.")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(101, notification)
        if (server == null) {
            try {
                server = LocalTransferServer(applicationContext).also { it.start() }
            } catch (e: Exception) {
                TransferState.status = "Erreur : " + (e.localizedMessage ?: "serveur indisponible")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.close()
        server = null
        TransferState.answer(false)
        TransferState.active = false
        TransferState.address = ""
        TransferState.status = "Serveur arrêté"
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
