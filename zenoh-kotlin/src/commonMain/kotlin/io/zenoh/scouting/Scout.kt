package io.zenoh.scouting

import io.zenoh.jni.JNIScout

class Scout<R> internal constructor(
    val receiver: R,
    private val jniScout: JNIScout?
) : AutoCloseable {

    fun stop() {
        jniScout?.close()
    }

    override fun close() {
        stop()
    }
}


