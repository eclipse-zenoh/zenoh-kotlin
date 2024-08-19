package io.zenoh.jni

import io.zenoh.ZenohLoad
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into

object JNIZBytes {

    init {
        ZenohLoad
    }

    fun serializeIntoList(list: List<ZBytes>): ZBytes {
        return serializeIntoListViaJNI(list.map { it.bytes }).into()
    }

    fun deserializeIntoList(zbytes: ZBytes): List<ZBytes> {
        return deserializeIntoListViaJNI(zbytes.bytes).map { it.into() }.toList()
    }

    fun serializeIntoMap(map: Map<ZBytes, ZBytes>): ZBytes {
        return serializeIntoMapViaJNI(map.map { (k, v) -> k.bytes to v.bytes }.toMap()).into()
    }

    fun deserializeIntoMap(bytes: ZBytes): Map<ZBytes, ZBytes> {
        return deserializeIntoMapViaJNI(bytes.bytes).map { (k, v) -> k.into() to v.into() }.toMap()
    }

    private external fun serializeIntoMapViaJNI(map: Map<ByteArray, ByteArray>): ByteArray

    private external fun serializeIntoListViaJNI(list: List<ByteArray>): ByteArray

    private external fun deserializeIntoMapViaJNI(payload: ByteArray): Map<ByteArray, ByteArray>

    private external fun deserializeIntoListViaJNI(payload: ByteArray): List<ByteArray>
}