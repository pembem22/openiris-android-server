package com.example.openiris_android_server

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.openiris_android_server.slime.PacketBuilder
import com.example.openiris_android_server.slime.SlimeVRTrackerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch


const val PROTOCOL_VERSION: Int = 20

class ForegroundService : Service() {
    companion object {
        lateinit var instance: ForegroundService
    }

    private val CHANNEL_ID = "OpenIrisServerForegroundServiceChannel"
    private val NOTIFICATION_ID = 100


    val REQUEST_GET_REPORT: Int = 0x01
    val REQUEST_SET_REPORT: Int = 0x09
    val REPORT_TYPE_INPUT: Int = 0x0100
    val REPORT_TYPE_OUTPUT: Int = 0x0200
    val REPORT_TYPE_FEATURE: Int = 0x0300


    private val frameSharedFlow = MutableSharedFlow<Frame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

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

//        serviceScope.launch {
//            println("Starting HTTP server")
//            startCameraServer(cameraManager)
//        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

        val TAG = "SLIME"

        device?.let {
            Log.i(TAG, "Model: " + device!!.deviceName)
            Log.i(TAG, "ID: " + device!!.deviceId)
            Log.i(TAG, "Class: " + device!!.deviceClass)
            Log.i(TAG, "Protocol: " + device!!.deviceProtocol)
            Log.i(TAG, "Vendor ID " + device!!.vendorId)
            Log.i(TAG, "Product ID: " + device!!.productId)
            Log.i(TAG, "Interface count: " + device!!.interfaceCount)
            Log.i(TAG, "---------------------------------------")

            // Get interface details
            for (index in 0 until device!!.interfaceCount) {
                val mUsbInterface = device!!.getInterface(index)

                Log.i(TAG, "  *****     *****")
                Log.i(TAG, "  Interface index: $index")
                Log.i(TAG, "  Interface ID: " + mUsbInterface.id)
                Log.i(TAG, "  Inteface class: " + mUsbInterface.interfaceClass)
                Log.i(TAG, "  Interface protocol: " + mUsbInterface.interfaceProtocol)
                Log.i(TAG, "  Endpoint count: " + mUsbInterface.endpointCount)
                // Get endpoint details
                for (epi in 0 until mUsbInterface.endpointCount) {
                    val mEndpoint = mUsbInterface.getEndpoint(epi)
                    Log.i(TAG, "    ++++   ++++   ++++")
                    Log.i(TAG, "    Endpoint index: $epi")
                    Log.i(TAG, "    Attributes: " + mEndpoint.attributes)
                    Log.i(TAG, "    Direction: " + mEndpoint.direction)
                    Log.i(TAG, "    Number: " + mEndpoint.endpointNumber)
                    Log.i(TAG, "    Interval: " + mEndpoint.interval)
                    Log.i(TAG, "    Packet size: " + mEndpoint.maxPacketSize)
                    Log.i(TAG, "    Type: " + mEndpoint.type)
                }
            }

            serviceScope.launch {
                var usbInterface: UsbInterface? = null
                var usbEndpointOut: UsbEndpoint? = null
                var usbEndpointIn: UsbEndpoint? = null
                var mUsbManager: UsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                var usbConnection = mUsbManager.openDevice(device)

                usbInterface = device.getInterface(2)

//                usbConnection.controlTransfer()

                if (!usbConnection!!.claimInterface(usbInterface, true))
                    Log.e(TAG, "Failed to claim interface")

                for (ii in 0 until usbInterface.endpointCount) {
                    val type = usbInterface.getEndpoint(ii).type
                    val direction = usbInterface.getEndpoint(ii).direction
                    val number = usbInterface.getEndpoint(ii).endpointNumber
//                    if (type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (direction == UsbConstants.USB_DIR_IN)
                        usbEndpointIn = usbInterface.getEndpoint(ii)
                    else
                        usbEndpointOut = usbInterface.getEndpoint(ii)
//                    }
                }

//                println(usbEndpointIn)
//                println(usbEndpointOut)
//
//                val discoveryUdp = DatagramSocket();
//                discoveryUdp.broadcast = true
//
//
//                val broadcastHandshakeJob = launch {
//                    // Find SlimeVR Server
//
//                    val packetBuilder = PacketBuilder()
//                    val (arr, len) = packetBuilder.makeHandshakePacket(
//                        0,
//                        0,
//                        0,
//                        PROTOCOL_VERSION,
//                        "virtual-smol-slime",
//                        "AABBCCDDEEFF".hexToByteArray(),
//                        0
//                    )
//
//                    val broadcastPacket =
//                        DatagramPacket(arr, len, InetAddress.getByName("255.255.255.255"), 6969)
//
//                    discoveryUdp.send(broadcastPacket)
//
//                    delay(1000)
//
//                    println("Sent broadcast")
//                }
//
//                runBlocking {
//                    val recvBuf = ByteArray(128)
//                    val packet = DatagramPacket(recvBuf, recvBuf.size)
//                    discoveryUdp.soTimeout = 10
//
//                    while (true) {
//                        try {
//                            discoveryUdp.receive(packet)
//                            break
//                        } catch (_: Error) {
//                        }
//                    }
//
//                    println("Got response ${packet.data.toHexString()}")
//                }


//                SlimeVRTrackerManager.startDiscovery() { serverAddress, port ->
                    SlimeVRTrackerManager.createTracker(1)
                    val tracker = SlimeVRTrackerManager.getTracker(1)

                    // Send fake rotation and position
                    tracker?.sendData(
                        PacketBuilder().makeBatteryLevelPacket(3.3f, 50f).first
                    )
//                }


                // Never finishes reading if buffer is larger than the maxPacketSize!!!
                val buffer = ByteArray(usbEndpointIn!!.maxPacketSize)
                while (true) {
                    buffer[0] = 0xF0.toByte()
                    buffer[20] = 0xF0.toByte()
                    buffer[40] = 0xF0.toByte()

                    val bytesGot =
                        usbConnection.bulkTransfer(usbEndpointIn, buffer, buffer.size, 100);
                    if (bytesGot == 0) {
                        continue
                    }

                    var newPackets = 0
                    newPackets += if (buffer[0].toInt() != 0xF0) 1 else 0
                    newPackets += if (buffer[20].toInt() != 0xF0) 1 else 0
                    newPackets += if (buffer[40].toInt() != 0xF0) 1 else 0

//                    println("$bytesGot $newPackets ${buffer.toHexString()}")

                    //|b0      |b1      |b2      |b3      |b4      |b5      |b6      |b7      |b8      |b9      |b10     |b11     |b12     |b13     |b14     |b15     |
                    //|type    |id      |packet data                                                                                                                  |
                    //|0       |id      |proto   |batt    |batt_v  |temp    |brd_id  |mcu_id  |imu_id  |mag_id  |fw_date          |major   |minor   |patch   |rssi    |
                    //|1       |id      |q0               |q1               |q2               |q3               |a0               |a1               |a2               |
                    //|2       |id      |batt    |batt_v  |temp    |q_buf                              |a0               |a1               |a2               |rssi    |
                    //|3	   |id      |svr_stat|status  |resv                                                                                              |rssi    |
                    //|255     |id      |addr                                                 |resv                                                                   |
//                    val size = usbConnection.controlTransfer(0xA0, REQUEST_GET_REPORT, REPORT_TYPE_OUTPUT, 0x00, buffer, buffer.size, 100);
//                    if (size > 0) {
//                        println("EXTRA $status")
//                        println(buffer.toHexString())
//                    }
                }
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
