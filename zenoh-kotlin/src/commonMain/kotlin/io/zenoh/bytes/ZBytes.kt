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

package io.zenoh.bytes

import io.zenoh.exceptions.ZError
import io.zenoh.jni.JNIZBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KClass
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
 * - List of [IntoZBytes]
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
 * - [IntoZBytes]
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
 * For custom serialization, classes to be serialized need to implement the [IntoZBytes] interface.
 * For instance:
 *
 * ```kotlin
 * class Foo(val content: String) : IntoZBytes {
 *
 *   /*Inherits: IntoZBytes*/
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
 * Implementing the [IntoZBytes] interface on a class enables the possibility of serializing lists and maps
 * of that type, for instance:
 * ```kotlin
 * val list = listOf(Foo("bar"), Foo("buz"), Foo("fizz"))
 * val zbytes = ZBytes.serialize<List<Foo>>(list)
 * ```
 *
 * ## Deserialization
 *
 * In order for the serialization to be successful on a custom class,
 * it must override the `into(): ZBytes` function from the [IntoZBytes] interface.
 *
 * Regarding deserialization for custom objects, for the time being (this API will be expanded to
 * provide further utilities) you need to manually convert the ZBytes into the type you want.
 *
 * ```kotlin
 * val inputFoo = Foo("example")
 * payload = ZBytes.serialize(inputFoo).getOrThrow()
 * val outputFoo = Foo.from(payload)
 * check(inputFoo == outputFoo)
 *
 * // List of Foo.
 * val inputListFoo = inputList.map { value -> Foo(value) }
 * payload = ZBytes.serialize<List<Foo>>(inputListFoo).getOrThrow()
 * val outputListFoo = payload.deserialize<List<ZBytes>>().getOrThrow().map { zbytes -> Foo.from(zbytes) }
 * check(inputListFoo == outputListFoo)
 *
 * // Map of Foo.
 * val inputMapFoo = inputMap.map { (k, v) -> Foo(k) to Foo(v) }.toMap()
 * payload = ZBytes.serialize<Map<Foo, Foo>>(inputMapFoo).getOrThrow()
 * val outputMapFoo = payload.deserialize<Map<ZBytes, ZBytes>>().getOrThrow()
 *     .map { (key, value) -> Foo.from(key) to Foo.from(value) }.toMap()
 * check(inputMapFoo == outputMapFoo)
 * ```
 *
 * ### Deserialization functions:
 *
 * The [deserialize] function admits an argument which by default is an emptyMap, consisting
 * of a `Map<KType, KFunction1<ZBytes, Any>>` map. This allows to specify types in a map, associating
 * functions for deserialization for each of the types in the map.
 *
 * For instance, let's stick to the previous implementation of our example Foo class.
 * We could provide directly the deserialization function as follows:
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
class ZBytes internal constructor(internal val bytes: ByteArray) : IntoZBytes {

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
        inline fun <reified T: Any> serialize(t: T): Result<ZBytes> = runCatching {
            return serialize(t, T::class)
        }

        fun <T: Any> serialize(t: T, clazz: KClass<T>): Result<ZBytes> = runCatching {
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
                    return Result.success(JNIZBytes.serializeIntoMap(zbytesMap))
                }

                typeOf<Any>().isSupertypeOf(type) -> {
                    return Result.success((t as Any).into())
                }

                else -> throw IllegalArgumentException("Unsupported type '$type' for serialization.")
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
     * @return a [Result] with the deserialization.
     */
    inline fun <reified T: Any> deserialize(
        deserializers: Map<KType, KFunction1<ZBytes, Any>> = emptyMap()
    ): Result<T> {
        val type = typeOf<T>()
        val deserializer = deserializers[type]
        if (deserializer != null) {
            return Result.success(deserializer(this) as T)
        }
        when {
            typeOf<List<*>>().isSupertypeOf(type) -> {
                val itemsClass = type.arguments.firstOrNull()?.type?.jvmErasure
                return deserialize(T::class, arg1clazz = itemsClass)
            }
            typeOf<Map<*, *>>().isSupertypeOf(type) -> {
                val keyClass = type.arguments.getOrNull(0)?.type?.jvmErasure
                val valueClass = type.arguments.getOrNull(1)?.type?.jvmErasure
                return deserialize(T::class, arg1clazz = keyClass, arg2clazz = valueClass)
            }
            typeOf<Any>().isSupertypeOf(type) -> {
                return deserialize(T::class)
            }
        }
        throw IllegalArgumentException("Unsupported type for deserialization: '$type'.")
    }

    /**
     * Deserialize the [ZBytes] into an element of class [clazz].
     *
     * It's generally preferable to use the [ZBytes.deserialize] function with reification, however
     * this function is exposed for cases when reification needs to be avoided.
     *
     * Example:
     * ```kotlin
     * val list = listOf("value1", "value2", "value3")
     * val zbytes = ZBytes.serialize(list).getOrThrow()
     * val deserializedList = zbytes.deserialize(clazz = List::class, arg1clazz = String::class).getOrThrow()
     * check(list == deserializedList)
     * ```
     *
     * Supported types:
     * - [Number]: Byte, Short, Int, Long, Float, Double
     * - [String]
     * - [ByteArray]
     * - Lists and Maps of the above-mentioned types.
     *
     * @see [ZBytes.deserialize]
     *
     *
     * @param clazz: the [KClass] of the type to be serialized.
     * @param arg1clazz Optional first nested parameter of the provided clazz, for instance when trying to deserialize
     *  into a `List<String>`, arg1clazz should be set to `String::class`, when trying to deserialize into a
     *  `Map<Int, String>`, arg1clazz should be set to `Int::class`. Can be null if providing a basic type.
     * @param arg2clazz Optional second nested parameter of the provided clazz, to be used for the cases of maps.
     *  For instance, when trying to deserialize into a `Map<Int, String>`, arg2clazz should be set to `String::class`.
     *  Can be null if providing a basic type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> deserialize(
        clazz: KClass<T>,
        arg1clazz: KClass<*>? = null,
        arg2clazz: KClass<*>? = null,
    ): Result<T> {
        val type: KType = when (clazz) {
            List::class -> typeOf<List<*>>()
            Map::class -> typeOf<Map<*, *>>()
            else -> clazz.createType()
        }
        return when {
            typeOf<List<*>>().isSupertypeOf(type) -> {
                val typeElement = arg1clazz?.createType()
                if (typeElement != null) {
                    Result.success(JNIZBytes.deserializeIntoList(this).map { it.intoAny(typeElement) } as T)
                } else {
                    Result.failure(IllegalArgumentException("Unsupported list type for deserialization: $type"))
                }
            }

            typeOf<Map<*, *>>().isSupertypeOf(type) -> {
                val keyType = arg1clazz?.createType()
                val valueType = arg2clazz?.createType()
                if (keyType != null && valueType != null) {
                    Result.success(JNIZBytes.deserializeIntoMap(this)
                        .map { (k, v) -> k.intoAny(keyType) to v.intoAny(valueType) }.toMap() as T
                    )
                } else {
                    Result.failure(IllegalArgumentException("Unsupported map type for deserialization: $type"))
                }
            }

            typeOf<Any>().isSupertypeOf(type) -> {
                Result.success(this.intoAny(type) as T)
            }

            else -> Result.failure(IllegalArgumentException("Unsupported type for deserialization: $type"))
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

@Throws(ZError::class)
internal fun Any?.into(): ZBytes {
    return when (this) {
        is String -> this.into()
        is Number -> this.into()
        is ByteArray -> this.into()
        is IntoZBytes -> this.into()
        else -> throw IllegalArgumentException("Unsupported serializable type")
    }
}

@Throws(ZError::class)
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
        else -> throw IllegalArgumentException("Unsupported type '$type' for deserialization.")
    }
}
