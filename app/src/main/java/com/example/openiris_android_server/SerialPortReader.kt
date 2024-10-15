package com.example.openiris_android_server

import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlin.math.min

class SerialPortReader(private val port: UsbSerialPort, private val readWaitMillis: Int) {
    private val maxPacketSize = port.readEndpoint.maxPacketSize
    private val circularBuffer = ByteArray(maxPacketSize * 4)
    private var writeIndex = 0
    private var readIndex = 0
    private var availableBytes = 0

    /**
     * Reads data from the serial port into the circular buffer.
     */
    fun readFromPort() {
        val tempBuffer = ByteArray(maxPacketSize)
        val bytesRead = port.read(tempBuffer, readWaitMillis)
        if (bytesRead > 0) {
            writeToBuffer(tempBuffer, bytesRead)
        }
    }

    /**
     * Writes data to the circular buffer.
     */
    private fun writeToBuffer(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            circularBuffer[writeIndex] = data[i]
            writeIndex = (writeIndex + 1) % circularBuffer.size
            if (availableBytes < circularBuffer.size) {
                availableBytes++
            } else {
                // Overwriting unread data, advance readIndex
                readIndex = (readIndex + 1) % circularBuffer.size
            }
        }
    }

    /**
     * Reads the exact amount of bytes requested from the circular buffer.
     * If more bytes are requested than available in the buffer, reads directly from the port
     * to fulfill the request and stores any remaining bytes back into the buffer.
     */
    fun readExact(destination: ByteArray, startPos: Int = 0) {
        val amount = destination.size - startPos
        // Check if the request can be fulfilled by the buffer alone
        if (amount <= availableBytes) {
            return readFromBuffer(destination, startPos)
        }

        // Create a result array to hold the requested bytes
        var bytesRead = 0

        // Read all available bytes from the circular buffer first
        if (availableBytes > 0) {
            bytesRead += availableBytes
            readFromBuffer(destination, startPos)
        }

        // Read the remaining bytes directly from the port
        while (bytesRead < amount) {
            readFromPort()
            val gotBytes = availableBytes
            readFromBuffer(destination, startPos + bytesRead)
            bytesRead += gotBytes
        }
    }

    /**
     * Reads a specified number of bytes from the circular buffer.
     */
    private fun readFromBuffer(destination: ByteArray, startPos: Int) {
        val amount = min(destination.size - startPos, availableBytes)

        for (i in 0 until amount) {
            destination[startPos + i] = circularBuffer[readIndex]
            readIndex = (readIndex + 1) % circularBuffer.size
        }
        availableBytes -= amount
    }

    /**
     * Gets the current available bytes in the buffer.
     */
    fun getAvailableBytes(): Int {
        return availableBytes
    }
}
