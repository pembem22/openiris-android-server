package com.example.openiris_android_server

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.openiris_android_server.ui.theme.OpenirisandroidserverTheme
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.time.Instant

const val BAUD_RATE: Int = 3000000
const val HEADER_SIZE: Int = 6
@OptIn(ExperimentalStdlibApi::class)
val ETVR_PACKET_HEADER: ByteArray = "FFA0FFA1".hexToByteArray()
const val READ_WAIT_MILLIS = 0

fun <T : Any?> ArrayList<T>.resize(newSize: Int, newValue: T) {
    if (newSize < this.size) {
        while (this.size > newSize) {
            this.removeLast()
        }
        return
    }

    this.ensureCapacity(newSize)
    while (this.size < newSize) {
        this.add(newValue)
    }
}

//fun UsbSerialPort.readExact(dest: ByteArray, startPos: Int = 0): Int {
//    val readBuffer = ByteArray(512)
//    var bytesInBuffer = startPos
//    while (bytesInBuffer < dest.size) {
//        val bytesLeftToRead = dest.size - bytesInBuffer
//        val bytesRead = this.read(readBuffer, bytesLeftToRead,0)
//        println("readExact expected $bytesLeftToRead got ${bytesRead}")
//        System.arraycopy(readBuffer, 0, dest, bytesInBuffer, bytesRead)
//        bytesInBuffer += bytesRead
//    }
//    return dest.size
//}

data class Frame(val rawData: ByteArray, val timestamp: Instant)

val frameChannel = Channel<Frame>(1, BufferOverflow.DROP_OLDEST)

suspend fun processSerialData(port: UsbSerialPort) {
    val reader = SerialPortReader(port, READ_WAIT_MILLIS)

    println("SIZE ${port.readEndpoint.maxPacketSize}")

    init@ while (true) {
//        println("init")
        var remainingBytes = ByteArray(0)

        findPacket@ while (true) {
//            println("findPacket")
//            remainingBytes.resize(remainingBytes.size + 2048, 0);
//            val readPosition = remainingBytes.size - 2048

            val readBuffer = ByteArray(2048)
            val bytesRead =
                try {
                    reader.readExact(readBuffer)
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Serial error: ${e.message}")
                    continue@findPacket
                }

//            println("findPacket bytes = ${bytesRead}")
//            println("content = ${readBuffer.copyOf(bytesRead).toHexString()}")

//            remainingBytes += readBuffer.copyOf(bytesRead)
            remainingBytes += readBuffer

            for (i in 0 until remainingBytes.size - ETVR_PACKET_HEADER.size - 2 + 1) {
                if (remainingBytes.copyOfRange(i, i + ETVR_PACKET_HEADER.size).contentEquals(ETVR_PACKET_HEADER)) {
                    remainingBytes = remainingBytes.copyOfRange(i, remainingBytes.size)
                    break@findPacket
                }
            }
        }

        while (true) {
            val headerBuffer = ByteArray(HEADER_SIZE)
            val headerBytesToCopy = minOf(remainingBytes.size, HEADER_SIZE)
            System.arraycopy(remainingBytes, 0, headerBuffer, 0, headerBytesToCopy)
            remainingBytes = remainingBytes.copyOfRange(headerBytesToCopy, remainingBytes.size)

            val headerBytesToRead = HEADER_SIZE - headerBytesToCopy

            if (headerBytesToRead > 0) {
//                val additionalHeaderBytes = ByteArray(headerBytesToRead)
                val bytesRead =
                    try {
                        reader.readExact(headerBuffer, headerBytesToCopy)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Serial error: ${e.message}")
                        continue@init
                    }
//                println("Header buffer headerBytesToRead=${headerBytesToRead} bytesRead=$bytesRead")
//                System.arraycopy(additionalHeaderBytes, 0, headerBuffer, headerBytesToCopy, headerBytesToRead)
            }

            if (!headerBuffer.copyOfRange(0, 4).contentEquals(ETVR_PACKET_HEADER)) {
//                println("Wrong packet header ${headerBuffer.copyOfRange(0, 4).toHexString()}")
                continue@init
            }

            val packetLen = ByteBuffer.wrap(headerBuffer.copyOfRange(4, 6)).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

            val dataBuffer = ByteArray(packetLen)
            val dataBytesToCopy = minOf(remainingBytes.size, packetLen)
            System.arraycopy(remainingBytes, 0, dataBuffer, 0, dataBytesToCopy)
            remainingBytes = remainingBytes.copyOfRange(dataBytesToCopy, remainingBytes.size)

            val dataRead =
                try {
                    reader.readExact(dataBuffer, dataBytesToCopy)
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Serial error: ${e.message}")
                    continue@init
                }


            val imageInputStream = ByteArrayInputStream(dataBuffer)
            println("Frame! ${dataBuffer.size}")

            frameChannel.send(Frame(dataBuffer, Instant.now()))
//            val image = try {
//                ImageIO.read(imageInputStream)
//            } catch (e: Exception) {
//                println("Failed to decode image: ${e.message}")
//                continue@init
//            }

//            val newFrame = Frame(
//                timestamp = Instant.now(),
//                rawData = dataBuffer,
//                decoded = image
//            )
//
//            sender.broadcastDirect(newFrame)
        }
    }
}

const val PART_BOUNDARY = "123456789000000000000987654321"

fun startCameraServer(frameChannel: ReceiveChannel<Frame>) {
    println("Server started!!!")

    embeddedServer(CIO, port = 8081, host = "0.0.0.0") {
        routing {
            get("/") {
//                call.respondText("Hello, world!", ContentType.Text.Plain)
                call.respond(multiPartStream(frameChannel))
            }
        }
    }.start(wait = false)
}

private fun multiPartStream(frameChannel: ReceiveChannel<Frame>): OutgoingContent.WriteChannelContent {
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType("multipart", "x-mixed-replace").withParameter("boundary", PART_BOUNDARY)
        override val headers: Headers = Headers.build { this.append("Access-Control-Allow-Origin", "*") }

        override suspend fun writeTo(channel: ByteWriteChannel) {
            frameChannel.consumeAsFlow().collect { frame ->
                channel.writeFully(
                    buildString {
                        append("--$PART_BOUNDARY\r\n")
                        append("Content-Type: image/jpeg\r\n")
                        append("Content-Length: ${frame.rawData.size}\r\n")
                        append("\r\n")
                    }.toByteArray()
                )
                channel.writeFully(frame.rawData)
                channel.writeFully("\r\n".toByteArray())
                channel.flush()
            }
        }
    }
}



class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            val usbSerialPort = driver.ports[0]
            val usbManager = this.getSystemService(USB_SERVICE) as UsbManager
            val usbConnection = usbManager.openDevice(driver.device)
            usbSerialPort.open(usbConnection)
            usbSerialPort.setParameters(
                BAUD_RATE,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            val mainHandler: Handler = Handler(mainLooper)

            mainHandler.post(Runnable { // Do your stuff here related to UI, e.g. show toast
                Toast.makeText(applicationContext, "Connected!", Toast.LENGTH_SHORT).show()
            })

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    startCameraServer(frameChannel)
                }

                launch(newSingleThreadContext("UsbThread")) {
                    processSerialData(usbSerialPort)
                }
            }
        }

        setContent {
            OpenirisandroidserverTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Text("Worki")
                }
            }
        }
    }
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