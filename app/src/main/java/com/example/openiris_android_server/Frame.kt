package com.example.openiris_android_server

import java.time.Instant

data class Frame(val rawData: ByteArray, val timestamp: Instant)
