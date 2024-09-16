package com.example.openiris_android_server

import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.openiris_android_server.ui.theme.OpenirisandroidserverTheme
import com.hoho.android.usbserial.BuildConfig
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenirisandroidserverTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")

                    Button(onClick = {
                        withContext(Dispatchers.IO) {

                        }
                    }) { Text("Connect") }
                }
            }
        }
    }

    private fun connect(permissionGranted: Boolean?, deviceId: Int, portNum: Int, baudRate: Int) {
        var device: UsbDevice? = null
        val usbManager = this.getSystemService(USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        if (device == null) {
//            status("connection failed: device not found")
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
//        if (driver == null) {
//            driver = CustomProber.getCustomProber().probeDevice(device)
//        }
//        driver = driver!!
//        if (driver == null) {
//            status("connection failed: no driver for device")
//            return
//        }
//        if (driver.ports.size < portNum) {
//            status("connection failed: not enough ports at device")
//            return
//        }
        val usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.device)) {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
            val intent: Intent = Intent(Constants.INTENT_ACTION_GRANT_USB)
            intent.setPackage(this.getPackageName())
            val usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }
//        if (usbConnection == null) {
//            if (!usbManager.hasPermission(driver.device)) status("connection failed: permission denied")
//            else status("connection failed: open failed")
//            return
//        }

//        connected = Connected.Pending
        try {
            usbSerialPort.open(usbConnection)
//            try {
                usbSerialPort.setParameters(
                    baudRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
//            } catch (e: UnsupportedOperationException) {
////                status("Setting serial parameters failed: " + e.message)
//            }
//            val socket: SerialSocket =
//                SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort)
//            service.connect(socket)
//            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
//            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
//            onSerialConnect()
        } catch (e: Exception) {
//            onSerialConnectError(e)
        }
    }
}

internal object Constants {
    // values have to be globally unique
    val INTENT_ACTION_GRANT_USB: String = BuildConfig.APPLICATION_ID + ".GRANT_USB"
    val INTENT_ACTION_DISCONNECT: String = BuildConfig.APPLICATION_ID + ".Disconnect"
    val NOTIFICATION_CHANNEL: String = BuildConfig.APPLICATION_ID + ".Channel"
    val INTENT_CLASS_MAIN_ACTIVITY: String = BuildConfig.APPLICATION_ID + ".MainActivity"

    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE: Int = 1001
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OpenirisandroidserverTheme {
        Greeting("Android")
    }
}