package com.example.openiris_android_server.slime

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SLIMEVR_PROTOCOL_VERSION: Int = 20

// Packet type constants
const val PACKET_HEARTBEAT = 0
const val PACKET_ACCEL = 4
const val PACKET_BATTERY_LEVEL = 12
const val PACKET_TAP = 13
const val PACKET_ROTATION_DATA = 17
const val PACKET_SIGNAL_STRENGTH = 19
const val PACKET_TEMPERATURE = 20
const val PACKET_HANDSHAKE = 3 // example, adjust as needed

// Utility data classes
data class Vector3(val x: Float, val y: Float, val z: Float)
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float)

typealias PacketView = Pair<ByteArray, Int>

class PacketBuilder {
    private val buffer = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)
    private var packetNumber = 0

    private fun nextPacketNumber(): Int = packetNumber++

    private fun beginPacket() {
        buffer.clear()
    }

    private fun endPacket(): PacketView {
        return Pair(buffer.array(), buffer.position())
    }

    private fun sendByte(value: Int) = buffer.put(value.toByte())
    private fun sendInt(value: Int) = buffer.putInt(value)
    private fun sendLong(value: Long) = buffer.putLong(value)
    private fun sendFloat(value: Float) = buffer.putFloat(value)
    private fun sendShortString(value: String) {
        val bytes = value.toByteArray()
        buffer.put(bytes.size.toByte())
        buffer.put(bytes)
    }
    private fun sendBytes(bytes: ByteArray) = buffer.put(bytes)

    private fun sendPacketType(type: Int) {
        sendInt(type)
    }
    private fun sendPacketNumber() = sendInt(nextPacketNumber())

    // Packet methods

    fun makeHeartbeatPacket(): PacketView {
        beginPacket()
        sendPacketType(PACKET_HEARTBEAT)
        sendPacketNumber()
        return endPacket()
    }

    fun makeAccelPacket(sensorId: Int, vector: Vector3): PacketView {
        beginPacket()
        sendPacketType(PACKET_ACCEL)
        sendPacketNumber()
        sendFloat(vector.x)
        sendFloat(vector.y)
        sendFloat(vector.z)
        sendByte(sensorId)
        return endPacket()
    }

    fun makeBatteryLevelPacket(voltage: Float, percentage: Float): PacketView {
        beginPacket()
        sendPacketType(PACKET_BATTERY_LEVEL)
        sendPacketNumber()
        sendFloat(voltage)
        sendFloat(percentage)
        return endPacket()
    }

    fun makeTapPacket(sensorId: Int, value: Int): PacketView {
        beginPacket()
        sendPacketType(PACKET_TAP)
        sendPacketNumber()
        sendByte(sensorId)
        sendByte(value)
        return endPacket()
    }

    fun makeRotationDataPacket(
        sensorId: Int,
        quat: Quaternion,
        dataType: Int,
        accuracy: Int
    ): PacketView {
        beginPacket()
        sendPacketType(PACKET_ROTATION_DATA)
        sendPacketNumber()
        sendByte(sensorId)
        sendByte(dataType)
        sendFloat(quat.x)
        sendFloat(quat.y)
        sendFloat(quat.z)
        sendFloat(quat.w)
        sendByte(accuracy)
        return endPacket()
    }

    fun makeSignalStrengthPacket(strength: Int): PacketView {
        beginPacket()
        sendPacketType(PACKET_SIGNAL_STRENGTH)
        sendPacketNumber()
        sendByte(255)
        sendByte(strength)
        return endPacket()
    }

    fun makeTemperaturePacket(sensorId: Int, temperature: Float): PacketView {
        beginPacket()
        sendPacketType(PACKET_TEMPERATURE)
        sendPacketNumber()
        sendByte(sensorId)
        sendFloat(temperature)
        return endPacket()
    }

    fun makeHandshakePacket(
        board: Int,
        sensorType: Int,
        mcuType: Int,
        protocolVersion: Int,
        firmwareVersion: String,
        macAddress: ByteArray, // 6 bytes
        trackerType: Int
    ): PacketView {
        require(macAddress.size == 6)
        beginPacket()
        sendPacketType(PACKET_HANDSHAKE)
        sendLong(0L) // packet number is always 0 for handshake
        sendInt(board)
        sendInt(sensorType)
        sendInt(mcuType)
        sendInt(0)
        sendInt(0)
        sendInt(0)
        sendInt(protocolVersion)
        sendShortString(firmwareVersion)
        sendBytes(macAddress)
        sendByte(trackerType)
        return endPacket()
    }
}
