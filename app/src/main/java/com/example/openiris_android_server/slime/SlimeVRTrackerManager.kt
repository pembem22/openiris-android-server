package com.example.openiris_android_server.slime

import android.app.Service
import android.content.Intent
import java.net.InetAddress

object SlimeVRTrackerManager {
    private val trackerConnections = mutableMapOf<Int, TrackerConnection>()
    private var discoveryClient: DiscoveryClient? = null

    fun startDiscovery(onServerFound: (InetAddress, Int) -> Unit) {
        discoveryClient = DiscoveryClient(onServerFound).also { it.start() }
    }

    fun stopDiscovery() {
        discoveryClient?.stop()
        discoveryClient = null
    }

    fun createTracker(id: Int) {
        if (trackerConnections.containsKey(id)) return
        val connection = TrackerConnection(id)
        trackerConnections[id] = connection
        connection.start()
    }

    fun removeTracker(id: Int) {
        trackerConnections.remove(id)?.close()
    }

    fun getTracker(id: Int): TrackerConnection? = trackerConnections[id]
}
