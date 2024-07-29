package io.zenoh.jni

import io.zenoh.ZenohLoad

object JNIZBytes {

    init {
        ZenohLoad
    }

    external fun serializeIntoMapViaJNI(map: Map<ByteArray, ByteArray>): ByteArray

    external fun deserializeIntoMapViaJNI(payload: ByteArray): Map<ByteArray, ByteArray>

    external fun serializeIntoListViaJNI(list: List<ByteArray>): ByteArray

    external fun deserializeIntoListViaJNI(payload: ByteArray): List<ByteArray>
}