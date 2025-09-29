package com.example.openiris_android_server.slime

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DiscoveryClient(
    private val onServerFound: (InetAddress, Int) -> Unit
) {
    private var job: Job? = null

    @OptIn(ExperimentalStdlibApi::class)
    fun start() {
        job = CoroutineScope(Dispatchers.IO).launch {

        }
    }

    fun stop() {
        job?.cancel()
    }
}
