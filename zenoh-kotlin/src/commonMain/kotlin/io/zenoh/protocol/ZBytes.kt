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

/**
 * The ZBytes class (Zenoh bytes) represents the bytes received through the Zenoh network.
 *
 * It provides many utilities to serialize an object into a ZBytes, as well as to deserialize from a ZBytes instance.
 *
 * # Serialization
 *
 * Supported types:
 *
 * ## Raw types
 *
 * * Numeric: Byte, Short, Int, Long, Float and Double.**
 * * String
 * * ByteArray
 *
 * For the raw types, there are basically three ways to serialize them into a ZBytes, for instance let's suppose
 * we want to serialize an `Int`, we could achieve it by::
 * * using the `into()` syntax:
 *  ```kotlin
 *  val exampleInt: Int = 256
 *  val zbytes: ZBytes = exampleInt.into()
 *  ```
 *
 * * using the `from()` syntax:
 *  ```kotlin
 *  val exampleInt: Int = 256
 *  val zbytes: ZBytes = ZBytes.from(exampleInt)
 *  ```
 *
 * * using the serialize syntax:
 * ```kotlin
 * val exampleInt: Int = 256
 * val zbytes: ZBytes = ZBytes.serialize<Int>(exampleInt).getOrThrow()
 * ```
 * This approach works as well for the other mentioned types.
 *
 * ## Lists
 *
 * Lists are supported, but they must be either:
 * - List of [Number] (Byte, Short, Int, Long, Float or Double)
 * - List of [String]
 * - List of [ByteArray]
 * - List of [Serializable]
 *
 * The serialize syntax must be used:
 * ```kotlin
 * val myList = listOf(1, 2, 5, 8, 13, 21)
 * val zbytes = ZBytes.serialize<List<Int>>(myList).getOrThrow()
 * ```
 *
 * ## Maps
 *
 * Maps are supported as well, with the restriction that their inner types must be either:
 * - [Number]
 * - [String]
 * - [ByteArray]
 * - [Serializable]
 *
 * ```kotlin
 * val myMap: Map<String, Int> = mapOf("foo" to 1, "bar" to 2)
 * val zbytes = ZBytes.serialize<Map<String, Int>>(myMap).getOrThrow()
 * ```
 *
 * # Deserialization
 *
 * ## Raw types
 *
 * * Numeric: Byte, Short, Int, Long, Float and Double
 * * String
 * * ByteArray
 *
 * Example:
 *
 * For these raw types, you can use the functions `to<Type>`, that is
 * - [toByte]
 * - [toShort]
 * - [toInt]
 * - [toLong]
 * - [toDouble]
 * - [toString]
 * - [toByteArray]
 *
 * For instance, for an Int:
 * ```kotlin
 * val example: Int = 256
 * val zbytes: ZBytes = exampleInt.into()
 * val deserializedInt = zbytes.toInt()
 * ```
 *
 * Alternatively, the deserialize syntax can be used as well:
 * ```kotlin
 * val exampleInt: Int = 256
 * val zbytes: ZBytes = exampleInt.into()
 * val deserializedInt = zbytes.deserialize<Int>().getOrThrow()
 * ```
 *
 * ## Lists
 *
 * Lists are supported, but they must be deserialized either into a:
 * - List of [Number] (Byte, Short, Int, Long, Float or Double)
 * - List of [String]
 * - List of [ByteArray]
 * - List of [Deserializable]
 *
 * To deserialize into a list, we need to use the deserialize syntax as follows:
 * ```kotlin
 * val inputList = listOf("sample1", "sample2", "sample3")
 * payload = ZBytes.serialize(inputList).getOrThrow()
 * val outputList = payload.deserialize<List<String>>().getOrThrow()
 * ```
 *
 * ## Maps
 *
 * Maps are supported as well, with the restriction that their inner types must be either:
 * - [Number]
 * - [String]
 * - [ByteArray]
 * - [Deserializable]
 *
 * ```kotlin
 * val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
 * payload = ZBytes.serialize(inputMap).getOrThrow()
 * val outputMap = payload.deserialize<Map<String, String>>().getOrThrow()
 * check(inputMap == outputMap)
 * ```
 *
 * # Custom serialization and deserialization
 *
 * ## Serialization
 *
 * For custom serialization, classes to be serialized need to implement the [Serializable] interface.
 * For instance:
 *
 * ```kotlin
 * class Foo(val content: String) : Serializable {
 *
 *   /*Inherits: Serializable*/
 *   override fun into(): ZBytes = content.into()
 * }
 * ```
 *
 * This way, we can do:
 * ```kotlin
 * val foo = Foo("bar")
 * val serialization = ZBytes.serialize<Foo>(foo).getOrThrow()
 * ```
 *
 * Implementing the [Serializable] interface on a class enables the possibility of serializing lists and maps
 * of that type, for instance:
 * ```kotlin
 * val list = listOf(Foo("bar"), Foo("buz"), Foo("fizz"))
 * val zbytes = ZBytes.serialize<List<Foo>>(list)
 * ```
 *
 * ## Deserialization
 *
 * For custom deserialization, classes to be serialized need to implement the [Deserializable] interface, and
 * their companion object need to implement the [Deserializable.From] interface, for instance, let's make the
 * `Foo` class (defined in the previous section) implement these interfaces:
 *
 * ```kotlin
 * class Foo(val content: String) : Serializable, Deserializable {
 *
 *   /*Inherits: Serializable*/
 *   override fun into(): ZBytes = content.into()
 *
 *   companion object: Deserializable.From {
 *      override fun from(zbytes: ZBytes): Foo {
 *          return Foo(zbytes.toString())
 *      }
 *   }
 * }
 * ```
 *
 * With this implementation, then the deserialization works as follows with the deserialization syntax:
 * ```kotlin
 * val foo = Foo("bar")
 * val zbytes = ZBytes.serialize<Foo>(foo).getOrThrow()
 * val deserialization = zbytes.deserialize<Foo>().getOrThrow()
 * ```
 *
 * Analogous to the serialization, we can deserialize into lists and maps of the type implementing
 * the [Deserializable] interface:
 *
 * ```kotlin
 * val list = listOf(Foo("bar"), Foo("buz"), Foo("fizz"))
 * val zbytes = ZBytes.serialize<List<Foo>>(list)
 * val deserializedList = zbytes.deserialize<List<Foo>>().getOrThrow()
 * ```
 *
 * ### Deserialization functions:
 *
 * The [deserialize] function admits an argument which by default is an emptyMap, consisting
 * of a `Map<KType, KFunction1<ZBytes, Any>>` map. This allows to specify types in a map, associating
 * functions for deserialization for each of the types in the map.
 *
 * For instance, let's stick to the previous implementation of our example Foo class, when it
 * only implemented the [Serializable] class:
 * ```kotlin
 * class Foo(val content: String) : Serializable {
 *
 *   /*Inherits: Serializable*/
 *   override fun into(): ZBytes = content.into()
 * }
 * ```
 *
 * Instead of making it implement the [Deserializable] interface as explained previously,
 * we could provide directly the deserialization function as follows:
 *
 * ```kotlin
 * fun deserializeFoo(zbytes: ZBytes): Foo {
 *   return Foo(zbytes.toString())
 * }
 *
 * val foo = Foo("bar")
 * val zbytes = ZBytes.serialize<Foo>(foo)
 * val deserialization = zbytes.deserialize<Foo>(mapOf(typeOf<Foo>() to ::deserializeFoo)).getOrThrow()
 * ```
 */
class ZBytes internal constructor(internal val bytes: ByteArray) : Serializable {

    companion object {
        fun from(serializable: Serializable) = serializable.into()
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

        /**
         * Serialize an element of type [T] into a [ZBytes].
         *
         * Supported types:
         * - [Number]: Byte, Short, Int, Long, Float, Double
         * - [String]
         * - [ByteArray]
         * - [Serializable]
         * - Lists and Maps of the above-mentioned types.
         *
         * @see ZBytes
         * @return a [Result] with the serialized [ZBytes].
         */
        inline fun <reified T> serialize(t: T): Result<ZBytes> = runCatching {
            when {
                typeOf<List<*>>().isSupertypeOf(typeOf<T>()) -> {
                    val list = t as List<*>
                    val zbytesList = list.map { it.into() }
                    return Result.success(JNIZBytes.serializeIntoList(zbytesList))
                }

                typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                    val map = t as Map<*, *>
                    val zbytesMap = map.map { (k, v) -> k.into() to v.into() }.toMap()
                    return Result.success(JNIZBytes.serializeIntoMap(zbytesMap))
                }

                typeOf<Any>().isSupertypeOf(typeOf<T>()) -> {
                    return Result.success((t as Any).into())
                }

                else -> throw IllegalArgumentException("Unsupported type '${typeOf<T>()}' for serialization.")
            }
        }
    }

    /**
     * Deserialize the [ZBytes] instance into an element of type [T].
     *
     * Supported types:
     * - [Number]: Byte, Short, Int, Long, Float, Double
     * - [String]
     * - [ByteArray]
     * - [Deserializable]
     * - Lists and Maps of the above-mentioned types.
     *
     *
     * A map of types and functions for deserialization can also be provided.
     *
     * For instance:
     * ```kotlin
     * fun deserializeFoo(zbytes: ZBytes): Foo {
     *   return Foo(zbytes.toString())
     * }
     *
     * val foo = Foo("bar")
     * val zbytes = ZBytes.serialize<Foo>(foo)
     * val deserialization = zbytes.deserialize<Foo>(mapOf(typeOf<Foo>() to ::deserializeFoo)).getOrThrow()
     * ```
     *
     * In case the provided type isn't associated with any of the functions provided in the [deserializers] map
     * (if provided), the deserialization will carry on with the default behavior.
     *
     * @see ZBytes
     * @see Deserializable
     * @return a [Result] with the deserialization.
     */
    inline fun <reified T> deserialize(
        deserializers: Map<KType, KFunction1<ZBytes, Any>> = emptyMap()
    ): Result<T> {
        val deserializer = deserializers[typeOf<T>()]
        if (deserializer != null) {
            return Result.success(deserializer(this) as T)
        }
        return when {
            typeOf<List<*>>().isSupertypeOf(typeOf<T>()) -> {
                val type = typeOf<T>().arguments.firstOrNull()?.type
                if (type != null) {
                    Result.success(JNIZBytes.deserializeIntoList(this).map { it.intoAny(type) } as T)
                } else {
                    Result.failure(IllegalArgumentException("Unsupported list type for deserialization: ${typeOf<T>()}"))
                }
            }

            typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                val keyType = typeOf<T>().arguments.getOrNull(0)?.type
                val valueType = typeOf<T>().arguments.getOrNull(1)?.type
                if (keyType != null && valueType != null) {
                    Result.success(JNIZBytes.deserializeIntoMap(this)
                        .map { (k, v) -> k.intoAny(keyType) to v.intoAny(valueType) }.toMap() as T
                    )
                } else {
                    Result.failure(IllegalArgumentException("Unsupported map type for deserialization: ${typeOf<T>()}"))
                }
            }

            typeOf<Any>().isSupertypeOf(typeOf<T>()) -> {
                Result.success(this.intoAny(typeOf<T>()) as T)
            }

            else -> Result.failure(IllegalArgumentException("Unsupported type for deserialization: ${typeOf<T>()}"))
        }
    }


    fun toByteArray() = bytes

    fun toByte(): Byte {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).get()
    }

    fun toShort(): Short {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).short
    }

    fun toInt(): Int {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun toLong(): Long {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    fun toFloat(): Float {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).float
    }

    fun toDouble(): Double {
        return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).double
    }

    override fun toString() = bytes.decodeToString()

    override fun into(): ZBytes = this

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}

fun Number.into(): ZBytes {
    return ZBytes.from(this)
}

fun String.into(): ZBytes {
    return ZBytes.from(this)
}

fun ByteArray.into(): ZBytes {
    return ZBytes(this)
}

@Throws
@PublishedApi
internal fun Any?.into(): ZBytes {
    return when (this) {
        is String -> this.into()
        is Number -> this.into()
        is ByteArray -> this.into()
        is Serializable -> this.into()
        else -> throw IllegalArgumentException("Unsupported serializable type")
    }
}

@Throws
@PublishedApi
internal fun ZBytes.intoAny(type: KType): Any {
    return when (type) {
        typeOf<String>() -> this.toString()
        typeOf<Byte>() -> this.toByte()
        typeOf<Short>() -> this.toShort()
        typeOf<Int>() -> this.toInt()
        typeOf<Long>() -> this.toLong()
        typeOf<Float>() -> this.toFloat()
        typeOf<Double>() -> this.toDouble()
        typeOf<ByteArray>() -> this.toByteArray()
        typeOf<ZBytes>() -> this
        else -> {
            when {
                typeOf<Deserializable>().isSupertypeOf(type) -> {
                    val companion = type.jvmErasure.companionObject
                    val function = companion?.declaredMemberFunctions?.find { it.name == "from" }
                    if (function != null) {
                        val result = function.call(type.jvmErasure.companionObjectInstance, this)
                        if (result != null) {
                            return result
                        } else {
                            throw Exception("The 'from' method returned null for the type '$type'.")
                        }
                    } else {
                        throw Exception("Implementation of 'from' method from the ${Deserializable.From::class} interface not found on element of type '$type'.")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported type '$type' for deserialization. " +
                        "If you are providing a generic, try using reification.")
            }

        }
    }
}
