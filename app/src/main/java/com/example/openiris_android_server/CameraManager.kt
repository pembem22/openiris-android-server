package com.example.openiris_android_server

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.TreeSet

class CameraManager {
    val connectedCameras = TreeSet<String>()
    val frameFlows = HashMap<String, MutableSharedFlow<Frame>>()

    suspend fun onConnected(camera: Camera) {
        val serial = camera.serialNumber
        if (serial == null) {
            println("Device has no serial number, skipping")
            return
        }

        println("New camera with serial ${serial} connected")

        connectedCameras.add(serial)

        camera.processSerial(getFrameFlow(serial))
    }

    fun getFrameFlow(serial: String): MutableSharedFlow<Frame> {
        if (!frameFlows.contains(serial)) {
            frameFlows[serial] = MutableSharedFlow(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

        return frameFlows.getValue(serial)
    }

    fun onDisconnected(camera: Camera) {
        connectedCameras.remove(camera.serialNumber!!)
    }

    private fun pushConnectedCamerasUpdate() {

    }
}