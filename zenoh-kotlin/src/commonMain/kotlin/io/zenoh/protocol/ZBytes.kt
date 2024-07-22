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
import kotlin.reflect.typeOf

typealias Deserializer<T> = (ByteArray) -> T

class ZBytes(val bytes: ByteArray) : IntoZBytes {

    override fun into(): ZBytes {
        return this
    }

    companion object {
        fun from(intoZBytes: IntoZBytes): ZBytes {
            return intoZBytes.into()
        }

        fun from(string: String): ZBytes {
            return ZBytes(string.toByteArray())
        }

        fun from(byteArray: ByteArray): ZBytes {
            return ZBytes(byteArray)
        }

        fun from(number: Number): ZBytes {
            return when (number) {
                is Byte -> byteArrayOf(number).into()
                is Short -> ByteBuffer.allocate(Short.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(number)
                    .array()
                    .into()
                is Int -> ByteBuffer.allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(number)
                    .array()
                    .into()
                is Long -> ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(number)
                    .array()
                    .into()
                is Float -> ByteBuffer.allocate(Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(number)
                    .array()
                    .into()
                is Double -> ByteBuffer.allocate(Double.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putDouble(number)
                    .array()
                    .into()
                else -> throw IllegalArgumentException("Unsupported number type")
            }
        }

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T>  serialize(t: T): Result<ZBytes> = runCatching {
            val type = typeOf<T>()
            val serializedBytes = when (type) {

                typeOf<Map<IntoZBytes, IntoZBytes>> () -> {
                    val map = t as Map<IntoZBytes, IntoZBytes>
                    val byteArrayMap = map.map { (k, v) -> k.into().bytes to v.into().bytes}.toMap()
                    JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into()
                }

                typeOf<Map<ZBytes, ZBytes>>() -> {
                    val map = t as Map<ZBytes, ZBytes>
                    val byteArrayMap = map.map { (k, v) -> k.bytes to v.bytes }.toMap()
                    JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into()
                }

                typeOf<Map<ByteArray, ByteArray>>() -> {
                    JNIZBytes.serializeIntoMapViaJNI(t as Map<ByteArray, ByteArray>).into()
                }

                typeOf<Map<String, String>>() -> {
                    val map = t as Map<String, String>
                    val byteArrayMap = map.map { (k, v) -> k.toByteArray() to v.toByteArray() }.toMap()
                    JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into()
                }

                typeOf<List<IntoZBytes>>() -> {
                    val list = t as List<IntoZBytes>
                    val byteArrayList = list.map { it.into().bytes }
                    JNIZBytes.serializeIntoListViaJNI(byteArrayList).into()
                }

                typeOf<List<ZBytes>>() -> {
                    val list = t as List<ZBytes>
                    val byteArrayList = list.map { it.bytes }
                    JNIZBytes.serializeIntoListViaJNI(byteArrayList).into()
                }


                typeOf<List<ByteArray>>() -> {
                    val list = t as List<ByteArray>
                    JNIZBytes.serializeIntoListViaJNI(list).into()
                }

                typeOf<List<String>>() -> {
                    val list = t as List<String>
                    val byteArrayList = list.map { it.toByteArray() }
                    JNIZBytes.serializeIntoListViaJNI(byteArrayList).into()
                }

                else -> throw IllegalArgumentException("Unsupported type")
            }
            return Result.success(serializedBytes)
        }
    }

    inline fun <reified T> deserialize(deserializers: Map<Class<*>, Deserializer<*>> = emptyMap()): Result<T> {
        return try {
            val deserializer = deserializers[T::class.java] as? Deserializer<T>
            val result: T = if (deserializer != null) {
                deserializer(bytes)
            } else {
                when (T::class) {
                    String::class -> bytes.decodeToString() as T
                    Byte::class -> {
                        require(bytes.size == Byte.SIZE_BYTES) { "Byte array must have exactly ${Byte.SIZE_BYTES} bytes to convert to a ${Byte::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).get() as T
                    }

                    Short::class -> {
                        require(bytes.size == Short.SIZE_BYTES) { "Byte array must have exactly ${Short.SIZE_BYTES} bytes to convert to a ${Short::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short as T
                    }

                    Int::class -> {
                        require(bytes.size == Int.SIZE_BYTES) { "Byte array must have exactly ${Int.SIZE_BYTES} bytes to convert to an ${Int::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int as T
                    }

                    Long::class -> {
                        require(bytes.size == Long.SIZE_BYTES) { "Byte array must have exactly ${Long.SIZE_BYTES} bytes to convert to a ${Long::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long as T
                    }

                    Float::class -> {
                        require(bytes.size == Float.SIZE_BYTES) { "Byte array must have exactly ${Float.SIZE_BYTES} bytes to convert to a ${Float::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float as T
                    }

                    Double::class -> {
                        require(bytes.size == Double.SIZE_BYTES) { "Byte array must have exactly ${Double.SIZE_BYTES} bytes to convert to a ${Double::class.simpleName}" }
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).double as T
                    }

                    else -> {
                        val type = typeOf<T>()
                        when (type) {
                            typeOf<Map<ZBytes, ZBytes>>() -> {
                                JNIZBytes.deserializeIntoMapViaJNI(bytes).map { (key, value) -> key.into() to value.into() } as T
                            }

                            typeOf<Map<ByteArray, ByteArray>>() -> {
                                JNIZBytes.deserializeIntoMapViaJNI(bytes) as T
                            }

                            typeOf<Map<String, String>>() -> {
                                JNIZBytes.deserializeIntoMapViaJNI(bytes)
                                    .map { (key, value) -> key.decodeToString() to value.decodeToString() }.toMap() as T
                            }

                            typeOf<List<ZBytes>>() -> {
                                JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.into() } as T
                            }

                            typeOf<List<ByteArray>>() -> {
                                JNIZBytes.deserializeIntoListViaJNI(bytes) as T
                            }

                            typeOf<List<String>>() -> {
                                JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.decodeToString() } as T
                            }

                            else -> throw IllegalArgumentException("Unsupported type")
                        }
                    }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZBytes

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}