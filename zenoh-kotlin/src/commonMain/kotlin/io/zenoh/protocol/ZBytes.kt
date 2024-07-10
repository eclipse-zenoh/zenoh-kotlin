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
import com.google.gson.reflect.TypeToken

typealias Deserializer<T> = (ByteArray) -> T

class ZBytes(val bytes: ByteArray) : IntoZBytes {

    override fun into(): ZBytes {
        return this
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
                        val typeToken = object : TypeToken<T>() {}.type
                        when (typeToken) {
                            object : TypeToken<Map<ByteArray, ByteArray>>() {}.type -> {
                                JNIZBytes.deserializeIntoMapViaJNI(bytes) as T
                            }

                            object : TypeToken<Map<String, String>>() {}.type -> {
                                JNIZBytes.deserializeIntoMapViaJNI(bytes)
                                    .map { (key, value) -> key.decodeToString() to value.decodeToString() }.toMap() as T
                            }

                            object : TypeToken<List<ByteArray>>() {}.type -> {
                                JNIZBytes.deserializeIntoListViaJNI(bytes) as T
                            }

                            object : TypeToken<List<String>>() {}.type -> {
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