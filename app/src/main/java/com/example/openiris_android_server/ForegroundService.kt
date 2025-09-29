package com.example.openiris_android_server

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ForegroundService : Service() {
    companion object {
        lateinit var instance: ForegroundService
    }

    private val CHANNEL_ID = "OpenIrisServerForegroundServiceChannel"
    private val NOTIFICATION_ID = 100

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val cameraManager = CameraManager()

    override fun onCreate() {
        instance = this

        val serviceChannel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("OpenIris Server Foreground Service Channel")
            .build()
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(serviceChannel)

        startForegroundService()

        serviceScope.launch {
            println("Starting HTTP server")
            startCameraServer(cameraManager)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

        println("ForegroundService onStartCommand $device")

        if (device != null) {
            serviceScope.launch {
                cameraManager.onConnected(Camera(device))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Cancel all coroutines when service is destroyed
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
    }

    private fun startForegroundService() {
        Toast.makeText(this, "OpenIris service starting", Toast.LENGTH_SHORT).show()

        // Create a notification for the foreground service
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenIris Server")
            .setContentText("The OpenIris server is running")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            /* service = */ this,
            /* id = */ NOTIFICATION_ID, // Cannot be 0
            /* notification = */ notification,
            /* foregroundServiceType = */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }
}
