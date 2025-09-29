package com.example.openiris_android_server

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.SharedFlow

const val PART_BOUNDARY = "123456789000000000000987654321"

fun startCameraServer(cameraManager: CameraManager) {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        routing {
            get("/") {
                call.respondText {
                    cameraManager.connectedCameras.joinToString(prefix = "\"", postfix = "\"")
                }
            }
            get(Regex("/(?<serial>([0-9A-F]{2}:){5}[0-9A-F]{2})")) {
                val serial = call.parameters["serial"]!!
                println("HTTP request serial $serial, available: ${cameraManager.frameFlows.values} ")
                call.respond(multiPartStream(cameraManager.getFrameFlow(serial)))
            }
        }
    }.start(wait = true)
}

private fun multiPartStream(frameChannel: SharedFlow<Frame>): OutgoingContent.WriteChannelContent {
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType =
            ContentType("multipart", "x-mixed-replace").withParameter("boundary", PART_BOUNDARY)
        override val headers: Headers =
            Headers.build { append("Access-Control-Allow-Origin", "*") }

        override suspend fun writeTo(channel: ByteWriteChannel) {
            frameChannel
                .collect { frame ->
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