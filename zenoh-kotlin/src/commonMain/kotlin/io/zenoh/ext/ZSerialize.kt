package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.bytes.into
import io.zenoh.jni.JNIZBytes
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.typeOf

/**
 * Serialize an element of type [T] into a [ZBytes].
 *
 * Supported types:
 * - [Number]: Byte, Short, Int, Long, Float, Double
 * - [String]
 * - [ByteArray]
 * - [IntoZBytes]
 * - Lists and Maps of the above-mentioned types.
 *
 * @see ZBytes
 * @return a [Result] with the serialized [ZBytes].
 */
inline fun <reified T: Any> zSerialize(t: T): Result<ZBytes> = runCatching {
    return zSerialize(t, T::class)
}

fun <T: Any> zSerialize(t: T, clazz: KClass<T>): Result<ZBytes> = runCatching {
    val type: KType = when (clazz) {
        List::class -> typeOf<List<*>>()
        Map::class -> typeOf<Map<*, *>>()
        else -> clazz.createType()
    }
    when {
        typeOf<List<*>>().isSupertypeOf(type) -> {
            val list = t as List<*>
            val zbytesList = list.map { it.into() }
            return Result.success(JNIZBytes.serializeIntoList(zbytesList))
        }

        typeOf<Map<*, *>>().isSupertypeOf(type) -> {
            val map = t as Map<*, *>
            val zbytesMap = map.map { (k, v) -> k.into() to v.into() }.toMap()
            if (zbytesMap.isEmpty()) {
                TODO()
            }
            val types = zbytesMap.entries.first()
            return Result.success(JNIZBytes.serializeIntoMap(zbytesMap, types.key.type, types.value.type))
        }

        typeOf<Any>().isSupertypeOf(type) -> {
            return Result.success((t as Any).into())
        }

        else -> throw IllegalArgumentException("Unsupported type '$type' for serialization.")
    }
}
