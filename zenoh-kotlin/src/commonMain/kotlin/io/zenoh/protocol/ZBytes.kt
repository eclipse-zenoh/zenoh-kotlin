//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh.protocol

import io.zenoh.jni.JNIZBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KFunction1
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

class ZBytes(val bytes: ByteArray) : IntoZBytes, Serializable {

    override fun into() = this

    companion object {
        fun from(intoZBytes: IntoZBytes) = intoZBytes.into()
        fun from(string: String) = ZBytes(string.toByteArray())
        fun from(byteArray: ByteArray) = ZBytes(byteArray)
        fun from(number: Number): ZBytes {
            val byteArray = when (number) {
                is Byte -> byteArrayOf(number)
                is Short -> ByteBuffer.allocate(Short.SIZE_BYTES).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    putShort(number)
                }.array()

                is Int -> ByteBuffer.allocate(Int.SIZE_BYTES).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    putInt(number)
                }.array()

                is Long -> ByteBuffer.allocate(Long.SIZE_BYTES).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    putLong(number)
                }.array()

                is Float -> ByteBuffer.allocate(Float.SIZE_BYTES).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    putFloat(number)
                }.array()

                is Double -> ByteBuffer.allocate(Double.SIZE_BYTES).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    putDouble(number)
                }.array()

                else -> throw IllegalArgumentException("Unsupported number type")
            }
            return ZBytes(byteArray)
        }

        inline fun <reified T> serialize(t: T): Result<ZBytes> = runCatching {
            when {
                typeOf<List<*>>().isSupertypeOf(typeOf<T>()) -> {
                    val list = t as List<*>
                    val byteArrayList = list.map { it.into().bytes }
                    return Result.success(JNIZBytes.serializeIntoListViaJNI(byteArrayList).into())
                }
                typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                    val map = t as Map<*, *>
                    val byteArrayMap = map.map { (k, v) -> k.into().bytes to v.into().bytes }.toMap()
                    return Result.success(JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into())
                }
                typeOf<Any>().isSupertypeOf(typeOf<T>()) -> {
                    return Result.success((t as Any).into())
                }
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
    }

    inline fun <reified T> deserialize(
        deserializers: Map<KType, KFunction1<ByteArray, Any>> = emptyMap()
    ): Result<T> = runCatching {
        var deserializer = deserializers[typeOf<T>()]
        if (deserializer != null) {
            return@runCatching deserializer(bytes) as T
        }
        deserializer = DefaultMapDeserializer.deserializationMap[typeOf<T>()]
        if (deserializer != null) {
            return@runCatching deserializer(bytes) as T
        }
        handleSerializableTypes<T>(bytes)
    }

    inline fun <reified T> handleSerializableTypes(bytes: ByteArray): T {
        when {
            typeOf<Serializable>().isSupertypeOf(typeOf<T>()) -> {
                val function = T::class.companionObject?.declaredMemberFunctions?.find { it.name == "from" }!!
                return function.call(T::class.companionObjectInstance, bytes.into()) as T
            }
            typeOf<List<Serializable>>().isSupertypeOf(typeOf<T>()) -> {
                val elementType = typeOf<T>().arguments.first().type?.jvmErasure
                if (elementType != null) {
                    val companion = elementType.companionObject
                    val function = companion?.declaredMemberFunctions?.find { it.name == "from" }!!
                    return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.into() }
                        .map { function.call(elementType.companionObjectInstance, it) } as T
                }
            }
            typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                val keyType = typeOf<T>().arguments[0].type?.jvmErasure
                val valueType = typeOf<T>().arguments[1].type?.jvmErasure
                if (keyType != null && valueType != null &&
                    Serializable::class.java.isAssignableFrom(keyType.java) &&
                    Serializable::class.java.isAssignableFrom(valueType.java)) {

                    val keyCompanion = keyType.companionObject
                    val keyFromFunction = keyCompanion?.declaredMemberFunctions?.find { it.name == "from" }!!
                    val valueCompanion = valueType.companionObject
                    val valueFromFunction = valueCompanion?.declaredMemberFunctions?.find { it.name == "from" }!!

                    val byteArrayMap = JNIZBytes.deserializeIntoMapViaJNI(bytes)
                    val deserializedMap = byteArrayMap.map { (k, v) ->
                        val key = keyFromFunction.call(keyType.companionObjectInstance, k.into())
                        val value = valueFromFunction.call(valueType.companionObjectInstance, v.into())
                        key to value
                    }.toMap()
                    return deserializedMap as T
                }
            }
        }
        throw IllegalArgumentException("Unsupported type.")
    }

    override fun toString() = bytes.decodeToString()

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}

object DefaultMapDeserializer {
    val deserializationMap: Map<KType, KFunction1<ByteArray, Any>> = mapOf(
        typeOf<Map<String, String>>() to ::map_string_string_deserialize,
        typeOf<Map<String, ByteArray>>() to ::map_string_bytes_deserialize,
        typeOf<Map<String, ZBytes>>() to ::map_string_zbytes_deserialize,
        typeOf<Map<String, Int>>() to ::map_string_int_deserialize,
        typeOf<Map<String, Long>>() to ::map_string_long_deserialize,
        typeOf<Map<String, Float>>() to ::map_string_float_deserialize,
        typeOf<Map<String, Double>>() to ::map_string_double_deserialize,
        typeOf<Map<String, Short>>() to ::map_string_short_deserialize,
        typeOf<Map<String, Byte>>() to ::map_string_byte_deserialize,
        typeOf<Map<Byte, String>>() to ::map_byte_string_deserialize,
        typeOf<Map<Byte, ByteArray>>() to ::map_byte_bytes_deserialize,
        typeOf<Map<Byte, ZBytes>>() to ::map_byte_zbytes_deserialize,
        typeOf<Map<Byte, Int>>() to ::map_byte_int_deserialize,
        typeOf<Map<Byte, Long>>() to ::map_byte_long_deserialize,
        typeOf<Map<Byte, Float>>() to ::map_byte_float_deserialize,
        typeOf<Map<Byte, Double>>() to ::map_byte_double_deserialize,
        typeOf<Map<Byte, Short>>() to ::map_byte_short_deserialize,
        typeOf<Map<Byte, Byte>>() to ::map_byte_byte_deserialize,
        typeOf<Map<Short, String>>() to ::map_short_string_deserialize,
        typeOf<Map<Short, ByteArray>>() to ::map_short_bytes_deserialize,
        typeOf<Map<Short, ZBytes>>() to ::map_short_zbytes_deserialize,
        typeOf<Map<Short, Int>>() to ::map_short_int_deserialize,
        typeOf<Map<Short, Long>>() to ::map_short_long_deserialize,
        typeOf<Map<Short, Float>>() to ::map_short_float_deserialize,
        typeOf<Map<Short, Double>>() to ::map_short_double_deserialize,
        typeOf<Map<Short, Short>>() to ::map_short_short_deserialize,
        typeOf<Map<Short, Byte>>() to ::map_short_byte_deserialize,
        typeOf<Map<Int, String>>() to ::map_int_string_deserialize,
        typeOf<Map<Int, ByteArray>>() to ::map_int_bytes_deserialize,
        typeOf<Map<Int, ZBytes>>() to ::map_int_zbytes_deserialize,
        typeOf<Map<Int, Int>>() to ::map_int_int_deserialize,
        typeOf<Map<Int, Long>>() to ::map_int_long_deserialize,
        typeOf<Map<Int, Float>>() to ::map_int_float_deserialize,
        typeOf<Map<Int, Double>>() to ::map_int_double_deserialize,
        typeOf<Map<Int, Short>>() to ::map_int_short_deserialize,
        typeOf<Map<Int, Byte>>() to ::map_int_byte_deserialize,
        typeOf<Map<Long, String>>() to ::map_long_string_deserialize,
        typeOf<Map<Long, ByteArray>>() to ::map_long_bytes_deserialize,
        typeOf<Map<Long, ZBytes>>() to ::map_long_zbytes_deserialize,
        typeOf<Map<Long, Int>>() to ::map_long_int_deserialize,
        typeOf<Map<Long, Long>>() to ::map_long_long_deserialize,
        typeOf<Map<Long, Float>>() to ::map_long_float_deserialize,
        typeOf<Map<Long, Double>>() to ::map_long_double_deserialize,
        typeOf<Map<Long, Short>>() to ::map_long_short_deserialize,
        typeOf<Map<Long, Byte>>() to ::map_long_byte_deserialize,
        typeOf<Map<Float, String>>() to ::map_float_string_deserialize,
        typeOf<Map<Float, ByteArray>>() to ::map_float_bytes_deserialize,
        typeOf<Map<Float, ZBytes>>() to ::map_float_zbytes_deserialize,
        typeOf<Map<Float, Int>>() to ::map_float_int_deserialize,
        typeOf<Map<Float, Long>>() to ::map_float_long_deserialize,
        typeOf<Map<Float, Float>>() to ::map_float_float_deserialize,
        typeOf<Map<Float, Double>>() to ::map_float_double_deserialize,
        typeOf<Map<Float, Short>>() to ::map_float_short_deserialize,
        typeOf<Map<Float, Byte>>() to ::map_float_byte_deserialize,
        typeOf<Map<Double, String>>() to ::map_double_string_deserialize,
        typeOf<Map<Double, ByteArray>>() to ::map_double_bytes_deserialize,
        typeOf<Map<Double, ZBytes>>() to ::map_double_zbytes_deserialize,
        typeOf<Map<Double, Int>>() to ::map_double_int_deserialize,
        typeOf<Map<Double, Long>>() to ::map_double_long_deserialize,
        typeOf<Map<Double, Float>>() to ::map_double_float_deserialize,
        typeOf<Map<Double, Double>>() to ::map_double_double_deserialize,
        typeOf<Map<Double, Short>>() to ::map_double_short_deserialize,
        typeOf<Map<Double, Byte>>() to ::map_double_byte_deserialize,

        // List type deserialization
        typeOf<List<String>>() to ::list_string_deserialize,
        typeOf<List<ByteArray>>() to ::list_bytes_deserialize,
        typeOf<List<ZBytes>>() to ::list_zbytes_deserialize,
        typeOf<List<Int>>() to ::list_int_deserialize,
        typeOf<List<Long>>() to ::list_long_deserialize,
        typeOf<List<Float>>() to ::list_float_deserialize,
        typeOf<List<Double>>() to ::list_double_deserialize,
        typeOf<List<Short>>() to ::list_short_deserialize,
        typeOf<List<Byte>>() to ::list_byte_deserialize,

        // Raw type deserialization methods
        typeOf<String>() to ::string_deserialize,
        typeOf<ByteArray>() to ::bytes_deserialize,
        typeOf<ZBytes>() to ::zbytes_deserialize,
        typeOf<Byte>() to ::byte_deserialize,
        typeOf<Short>() to ::short_deserialize,
        typeOf<Int>() to ::int_deserialize,
        typeOf<Long>() to ::long_deserialize,
        typeOf<Float>() to ::float_deserialize,
        typeOf<Double>() to ::double_deserialize
    )

    private fun string_deserialize(bytes: ByteArray): String {
        return bytes.decodeToString()
    }

    private fun bytes_deserialize(bytes: ByteArray): ByteArray {
        return bytes
    }

    private fun zbytes_deserialize(bytes: ByteArray): ZBytes {
        return ZBytes(bytes)
    }

    private fun byte_deserialize(bytes: ByteArray): Byte = bytes.toByte()

    private fun short_deserialize(bytes: ByteArray): Short = bytes.toShort()

    private fun int_deserialize(bytes: ByteArray): Int = bytes.toInt()

    private fun long_deserialize(bytes: ByteArray): Long = bytes.toLong()

    private fun float_deserialize(bytes: ByteArray): Float = bytes.toFloat()

    private fun double_deserialize(bytes: ByteArray): Double = bytes.toDouble()

    // List deserialization methods
    private fun list_string_deserialize(bytes: ByteArray): List<String> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.decodeToString() }
    }

    private fun list_bytes_deserialize(bytes: ByteArray): List<ByteArray> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes)
    }

    private fun list_zbytes_deserialize(bytes: ByteArray): List<ZBytes> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.into() }
    }

    private fun list_int_deserialize(bytes: ByteArray): List<Int> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toInt() }
    }

    private fun list_long_deserialize(bytes: ByteArray): List<Long> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toLong() }
    }

    private fun list_float_deserialize(bytes: ByteArray): List<Float> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toFloat() }
    }

    private fun list_double_deserialize(bytes: ByteArray): List<Double> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toDouble() }
    }

    private fun list_short_deserialize(bytes: ByteArray): List<Short> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toShort() }
    }

    private fun list_byte_deserialize(bytes: ByteArray): List<Byte> {
        return JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.toByte() }
    }

    // Map deserialization methods

    // Map<String, _>
    private fun map_string_string_deserialize(bytes: ByteArray): Map<String, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.decodeToString() }.toMap()
    }

    private fun map_string_bytes_deserialize(bytes: ByteArray): Map<String, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v }.toMap()
    }

    private fun map_string_zbytes_deserialize(bytes: ByteArray): Map<String, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.into() }.toMap()
    }

    private fun map_string_int_deserialize(bytes: ByteArray): Map<String, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toInt() }.toMap()
    }

    private fun map_string_long_deserialize(bytes: ByteArray): Map<String, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toLong() }.toMap()
    }

    private fun map_string_float_deserialize(bytes: ByteArray): Map<String, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toFloat() }.toMap()
    }

    private fun map_string_double_deserialize(bytes: ByteArray): Map<String, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toDouble() }.toMap()
    }

    private fun map_string_short_deserialize(bytes: ByteArray): Map<String, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toShort() }.toMap()
    }

    private fun map_string_byte_deserialize(bytes: ByteArray): Map<String, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.decodeToString() to v.toByte() }.toMap()
    }

    // Map<Byte, _>
    private fun map_byte_string_deserialize(bytes: ByteArray): Map<Byte, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.decodeToString() }.toMap()
    }

    private fun map_byte_bytes_deserialize(bytes: ByteArray): Map<Byte, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v }.toMap()
    }

    private fun map_byte_zbytes_deserialize(bytes: ByteArray): Map<Byte, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.into() }.toMap()
    }

    private fun map_byte_int_deserialize(bytes: ByteArray): Map<Byte, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toInt() }.toMap()
    }

    private fun map_byte_long_deserialize(bytes: ByteArray): Map<Byte, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toLong() }.toMap()
    }

    private fun map_byte_float_deserialize(bytes: ByteArray): Map<Byte, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toFloat() }.toMap()
    }

    private fun map_byte_double_deserialize(bytes: ByteArray): Map<Byte, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toDouble() }.toMap()
    }

    private fun map_byte_short_deserialize(bytes: ByteArray): Map<Byte, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toShort() }.toMap()
    }

    private fun map_byte_byte_deserialize(bytes: ByteArray): Map<Byte, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toByte() to v.toByte() }.toMap()
    }

    // Map<Short, _>
    private fun map_short_string_deserialize(bytes: ByteArray): Map<Short, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.decodeToString() }.toMap()
    }

    private fun map_short_bytes_deserialize(bytes: ByteArray): Map<Short, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v }.toMap()
    }

    private fun map_short_zbytes_deserialize(bytes: ByteArray): Map<Short, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.into() }.toMap()
    }

    private fun map_short_int_deserialize(bytes: ByteArray): Map<Short, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toInt() }.toMap()
    }

    private fun map_short_long_deserialize(bytes: ByteArray): Map<Short, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toLong() }.toMap()
    }

    private fun map_short_float_deserialize(bytes: ByteArray): Map<Short, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toFloat() }.toMap()
    }

    private fun map_short_double_deserialize(bytes: ByteArray): Map<Short, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toDouble() }.toMap()
    }

    private fun map_short_short_deserialize(bytes: ByteArray): Map<Short, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toShort() }.toMap()
    }

    private fun map_short_byte_deserialize(bytes: ByteArray): Map<Short, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toShort() to v.toByte() }.toMap()
    }

    // Map<Int, _>
    private fun map_int_string_deserialize(bytes: ByteArray): Map<Int, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.decodeToString() }.toMap()
    }

    private fun map_int_bytes_deserialize(bytes: ByteArray): Map<Int, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v }.toMap()
    }

    private fun map_int_zbytes_deserialize(bytes: ByteArray): Map<Int, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.into() }.toMap()
    }

    private fun map_int_int_deserialize(bytes: ByteArray): Map<Int, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toInt() }.toMap()
    }

    private fun map_int_long_deserialize(bytes: ByteArray): Map<Int, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toLong() }.toMap()
    }

    private fun map_int_float_deserialize(bytes: ByteArray): Map<Int, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toFloat() }.toMap()
    }

    private fun map_int_double_deserialize(bytes: ByteArray): Map<Int, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toDouble() }.toMap()
    }

    private fun map_int_short_deserialize(bytes: ByteArray): Map<Int, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toShort() }.toMap()
    }

    private fun map_int_byte_deserialize(bytes: ByteArray): Map<Int, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toInt() to v.toByte() }.toMap()
    }

    // Map<Long, _>
    private fun map_long_string_deserialize(bytes: ByteArray): Map<Long, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.decodeToString() }.toMap()
    }

    private fun map_long_bytes_deserialize(bytes: ByteArray): Map<Long, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v }.toMap()
    }

    private fun map_long_zbytes_deserialize(bytes: ByteArray): Map<Long, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.into() }.toMap()
    }

    private fun map_long_int_deserialize(bytes: ByteArray): Map<Long, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toInt() }.toMap()
    }

    private fun map_long_long_deserialize(bytes: ByteArray): Map<Long, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toLong() }.toMap()
    }

    private fun map_long_float_deserialize(bytes: ByteArray): Map<Long, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toFloat() }.toMap()
    }

    private fun map_long_double_deserialize(bytes: ByteArray): Map<Long, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toDouble() }.toMap()
    }

    private fun map_long_short_deserialize(bytes: ByteArray): Map<Long, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toShort() }.toMap()
    }

    private fun map_long_byte_deserialize(bytes: ByteArray): Map<Long, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toLong() to v.toByte() }.toMap()
    }

    // Map<Float, _>
    private fun map_float_string_deserialize(bytes: ByteArray): Map<Float, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.decodeToString() }.toMap()
    }

    private fun map_float_bytes_deserialize(bytes: ByteArray): Map<Float, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v }.toMap()
    }

    private fun map_float_zbytes_deserialize(bytes: ByteArray): Map<Float, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.into() }.toMap()
    }

    private fun map_float_int_deserialize(bytes: ByteArray): Map<Float, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toInt() }.toMap()
    }

    private fun map_float_long_deserialize(bytes: ByteArray): Map<Float, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toLong() }.toMap()
    }

    private fun map_float_float_deserialize(bytes: ByteArray): Map<Float, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toFloat() }.toMap()
    }

    private fun map_float_double_deserialize(bytes: ByteArray): Map<Float, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toDouble() }.toMap()
    }

    private fun map_float_short_deserialize(bytes: ByteArray): Map<Float, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toShort() }.toMap()
    }

    private fun map_float_byte_deserialize(bytes: ByteArray): Map<Float, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toFloat() to v.toByte() }.toMap()
    }

    // Map<Double, _>
    private fun map_double_string_deserialize(bytes: ByteArray): Map<Double, String> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.decodeToString() }.toMap()
    }

    private fun map_double_bytes_deserialize(bytes: ByteArray): Map<Double, ByteArray> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v }.toMap()
    }

    private fun map_double_zbytes_deserialize(bytes: ByteArray): Map<Double, ZBytes> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.into() }.toMap()
    }

    private fun map_double_int_deserialize(bytes: ByteArray): Map<Double, Int> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toInt() }.toMap()
    }

    private fun map_double_long_deserialize(bytes: ByteArray): Map<Double, Long> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toLong() }.toMap()
    }

    private fun map_double_float_deserialize(bytes: ByteArray): Map<Double, Float> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toFloat() }.toMap()
    }

    private fun map_double_double_deserialize(bytes: ByteArray): Map<Double, Double> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toDouble() }.toMap()
    }

    private fun map_double_short_deserialize(bytes: ByteArray): Map<Double, Short> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toShort() }.toMap()
    }

    private fun map_double_byte_deserialize(bytes: ByteArray): Map<Double, Byte> {
        return JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (k, v) -> k.toDouble() to v.toByte() }.toMap()
    }
}
