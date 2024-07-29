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

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T> serialize(t: T): Result<ZBytes> = runCatching {
            when {
                typeOf<Serializable>().isSupertypeOf(typeOf<T>()) -> {
                    return Result.success((t as Serializable).into())
                }
                typeOf<List<Serializable>>().isSupertypeOf(typeOf<T>()) -> {
                    val list = t as List<Serializable>
                    val byteArrayList = list.map { it.into().bytes }
                    return Result.success(JNIZBytes.serializeIntoListViaJNI(byteArrayList).into())
                }
                typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                    val map = t as Map<*, *>
                    if (map.keys.all { it is Serializable } && map.values.all { it is Serializable }) {
                        val serializableMap = map as Map<Serializable, Serializable>
                        val byteArrayMap = serializableMap.map { (k, v) -> k.into().bytes to v.into().bytes }.toMap()
                        return Result.success(JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into())
                    }
                }
            }
            val serializedBytes = when (typeOf<T>()) {
                typeOf<Map<ByteArray, ByteArray>>() -> {
                    JNIZBytes.serializeIntoMapViaJNI(t as Map<ByteArray, ByteArray>).into()
                }

                typeOf<Map<String, String>>() -> {
                    val map = t as Map<String, String>
                    val byteArrayMap = map.map { (k, v) -> k.toByteArray() to v.toByteArray() }.toMap()
                    JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into()
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

    inline fun <reified T> deserialize(
        deserializers: Map<KType, KFunction1<ByteArray, Any>> = emptyMap()
    ): Result<T> = runCatching {
        val deserializer = deserializers[typeOf<T>()]
        if (deserializer != null) {
            return@runCatching deserializer(bytes) as T
        }

        when (T::class) {
            ByteArray::class -> bytes as T
            String::class -> bytes.decodeToString() as T
            Byte::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).get() as T
            Short::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short as T
            Int::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int as T
            Long::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long as T
            Float::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float as T
            Double::class -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).double as T
            else -> deserializeComplexTypes<T>(bytes)
        }
    }

    inline fun <reified T> deserializeComplexTypes(bytes: ByteArray): T {
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
        return when (typeOf<T>()) {
            typeOf<Map<ZBytes, ZBytes>>() -> JNIZBytes.deserializeIntoMapViaJNI(bytes)
                .map { (key, value) -> key.into() to value.into() } as T

            typeOf<Map<ByteArray, ByteArray>>() -> JNIZBytes.deserializeIntoMapViaJNI(bytes) as T
            typeOf<Map<String, String>>() -> JNIZBytes.deserializeIntoMapViaJNI(bytes)
                .map { (key, value) -> key.decodeToString() to value.decodeToString() }.toMap() as T

            typeOf<List<ZBytes>>() -> JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.into() } as T
            typeOf<List<ByteArray>>() -> JNIZBytes.deserializeIntoListViaJNI(bytes) as T
            typeOf<List<String>>() -> JNIZBytes.deserializeIntoListViaJNI(bytes).map { it.decodeToString() } as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    override fun toString() = bytes.decodeToString()

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}
