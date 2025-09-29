package com.example.openiris_android_server

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.activity.ComponentActivity
import androidx.core.content.getSystemService
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Instant

const val BAUD_RATE: Int = 3000000
const val HEADER_SIZE: Int = 6

@OptIn(ExperimentalStdlibApi::class)
val ETVR_PACKET_HEADER: ByteArray = "FFA0FFA1".hexToByteArray()
const val READ_WAIT_MILLIS = 0

class Camera(private val usbDevice: UsbDevice) {
    val serialNumber = usbDevice.serialNumber

    suspend fun processSerial(frameFlow: MutableSharedFlow<Frame>) {
        val usbManager = ForegroundService.instance.getSystemService<UsbManager>()!!

        val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
        val port = driver.ports[0]
        val usbConnection = usbManager.openDevice(driver.device)
        port.open(usbConnection)
        port.setParameters(
            BAUD_RATE,
            UsbSerialPort.DATABITS_8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )

        val reader = SerialPortReader(port, READ_WAIT_MILLIS)

        try {
            init@ while (true) {
                var remainingBytes = ByteArray(0)

                findPacket@ while (true) {
                    val readBuffer = ByteArray(2048)
                    val bytesRead =
                        reader.readExact(readBuffer)

                    remainingBytes += readBuffer

                    for (i in 0 until remainingBytes.size - ETVR_PACKET_HEADER.size - 2 + 1) {
                        if (remainingBytes.copyOfRange(i, i + ETVR_PACKET_HEADER.size)
                                .contentEquals(ETVR_PACKET_HEADER)
                        ) {
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
                        val bytesRead =
                            reader.readExact(headerBuffer, headerBytesToCopy)
                    }

                    if (!headerBuffer.copyOfRange(0, 4).contentEquals(ETVR_PACKET_HEADER)) {
                        continue@init
                    }

                    val packetLen = ByteBuffer.wrap(headerBuffer.copyOfRange(4, 6))
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

                    val dataBuffer = ByteArray(packetLen)
                    val dataBytesToCopy = minOf(remainingBytes.size, packetLen)
                    System.arraycopy(remainingBytes, 0, dataBuffer, 0, dataBytesToCopy)
                    remainingBytes = remainingBytes.copyOfRange(dataBytesToCopy, remainingBytes.size)

                    val dataRead =
                        reader.readExact(dataBuffer, dataBytesToCopy)

                    frameFlow.emit(Frame(dataBuffer, Instant.now()))
                }
            }
        } catch (e: IOException) {
            println("Serial error $e, probably disconnected")
        }
    }
}