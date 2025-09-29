package com.example.openiris_android_server.slime

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

const val SLIMEVR_SERVER_PORT = 6969

@OptIn(ExperimentalStdlibApi::class)
val SLIMEVR_BROADCAST_PACKET = PacketBuilder().makeHandshakePacket(
    0,
    0,
    0,
    SLIMEVR_PROTOCOL_VERSION,
    "virtual-smol-slime",
    "AABBCCDDEEFF".hexToByteArray(),
    0
)

class TrackerConnection(
    val id: Int,
) {
    // Connection to the SlimeVR server, null if not connected
    private var socket: DatagramSocket? = null
    private var discoveryJob: Job? = null
    private var listeningJob: Job? = null

    // TODO: state/struct to hold found SlimeVR server info in one place
    private var serverAddress: InetAddress? = null
    private var serverPort: Int? = null

    @OptIn(ExperimentalStdlibApi::class)
    fun start() {
//        discoveryJob

        listeningJob = CoroutineScope(Dispatchers.IO).launch {
            var broadcastSocket = DatagramSocket().apply { broadcast = true }

            println("Here??????????")
//            println(discoveryMessage.first.sliceArray(0..discoveryMessage.second).toHexString())

            val broadcastPacket = DatagramPacket(
                SLIMEVR_BROADCAST_PACKET.first,
                SLIMEVR_BROADCAST_PACKET.second,
                InetAddress.getByName("255.255.255.255"),
                SLIMEVR_SERVER_PORT
            )

            val buffer = ByteArray(512)
            val inPacket = DatagramPacket(buffer, buffer.size)

            while (true) {
                broadcastSocket.send(broadcastPacket)

                broadcastSocket.soTimeout = 1000

                try {
                    broadcastSocket.receive(inPacket)
                    serverAddress = inPacket.address
                    serverPort = inPacket.port
                    break
                } catch (e: SocketTimeoutException) {
                    Log.w("DiscoveryClient", "No server response")
                    continue
                } finally {
                    broadcastSocket.close()
                }
            }

            println("got!!! ${inPacket.data.slice(inPacket.offset..inPacket.offset +inPacket.length).toByteArray().toHexString()} ")

            val socket = DatagramSocket()
            this@TrackerConnection.socket = socket
            while (isActive) {
                try {
                    socket.receive(inPacket)
                    println("Packet from server ${inPacket.data}")
//                    IncomingPacketHandler.handle(packet.data.copyOf(packet.length), this@TrackerConnection)
                } catch (e: IOException) {
                    break
                }
            }
        }
    }

    fun sendData(data: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val socket = socket ?: return@launch

            val packet = DatagramPacket(data, data.size, serverAddress!!, serverPort!!)
            socket.send(packet)
        }
    }

    fun close() {
        listeningJob?.cancel()
        socket?.close()
    }
}
