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
import io.zenoh.protocol.DeserializationUtils.handleDeserializable
import io.zenoh.protocol.DeserializationUtils.handleDeserializableList
import io.zenoh.protocol.DeserializationUtils.handleDeserializableMap
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
class ZBytes internal constructor(internal val bytes: ByteArray) {

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

                else -> throw IllegalArgumentException("Unsupported type")
            }
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

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()

    inline fun <reified T> deserialize(
        deserializers: Map<KType, KFunction1<ZBytes, Any>> = emptyMap()
    ): Result<T> {
        var deserializer = deserializers[typeOf<T>()]
        if (deserializer != null) {
            return Result.success(deserializer(this) as T)
        }
        deserializer = DeserializationUtils.deserializationMap[typeOf<T>()]
        if (deserializer != null) {
            return Result.success(deserializer(this) as T)
        }
        return handleSerializableTypes<T>(this)
    }

    inline fun <reified T> handleSerializableTypes(bytes: ZBytes): Result<T> {
        when {
            typeOf<Serializable>().isSupertypeOf(typeOf<T>()) -> {
                return handleDeserializable(bytes, typeOf<T>()).map { it as T }
            }

            typeOf<List<Serializable>>().isSupertypeOf(typeOf<T>()) -> {
                return handleDeserializableList(typeOf<T>().arguments.first().type?.jvmErasure, bytes).map { it as T }
            }

            typeOf<Map<*, *>>().isSupertypeOf(typeOf<T>()) -> {
                val keyType = typeOf<T>().arguments[0].type?.jvmErasure
                val valueType = typeOf<T>().arguments[1].type?.jvmErasure
                return handleDeserializableMap(keyType, valueType, bytes).map { it as T }
            }
        }
        throw IllegalArgumentException("Unsupported type.")
    }

}

/**
 * Serializable interface.
 *
 * Classes implementing this interface can be serialized into a ZBytes object.
 *
 * Example:
 * ```kotlin
 * class Foo(val content: String) : Serializable {
 *
 *   /*Inherits: Serializable*/
 *   override fun into(): ZBytes = content.into()
 * }
 */
interface Serializable {
    fun into(): ZBytes
}

/**
 * Deserializable interface.
 *
 * Classes implementing these two nested interfaces can be deserialized into a ZBytes object.
 *
 * The class must be declared as [Deserializable], but it's also necessary to make the companion
 * object of the class implement the [Deserializable.From], as shown in the example below:
 *
 * ```kotlin
 * class Foo(val content: String) : Deserializable {
 *
 *   companion object: Deserializable.From {
 *      override fun from(zbytes: ZBytes): Foo {
 *          return Foo(zbytes.toString())
 *      }
 *   }
 * }
 * ```
 */
interface Deserializable {
    interface From {
        fun from(zbytes: ZBytes): Serializable
    }
}

@Throws
fun Any?.into(): ZBytes {
    return when (this) {
        is String -> this.into()
        is Number -> this.into()
        is ByteArray -> this.into()
        is Serializable -> this.into()
        else -> throw IllegalArgumentException("Unsupported type")
    }
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

@PublishedApi
internal object DeserializationUtils {
    fun handleDeserializable(bytes: ZBytes, type: KType): Result<Any?> {
        return type::class.companionObject?.declaredMemberFunctions?.find { it.name == "from" }
            ?.let {
                it.call(type::class.companionObjectInstance, bytes)
                    .let { result -> Result.success(result) }
            } ?: let {
            Result.failure(Exception("Implementation of 'from' method from the ${Serializable::class.simpleName} interface not found."))
        }
    }

    fun handleDeserializableList(
        elementType: KClass<*>?, bytes: ZBytes
    ): Result<List<Any?>> {
        val companion = elementType?.companionObject
        val function = companion?.declaredMemberFunctions?.find { it.name == "from" }
        return if (function != null) {
            Result.success(JNIZBytes.deserializeIntoList(bytes)
                .map { function.call(elementType.companionObjectInstance, it) })
        } else {
            Result.failure(Exception("Implementation of 'from' method from the ${Serializable::class} interface on the list type not found."))
        }
    }

    fun handleDeserializableMap(
        keyType: KClass<*>?,
        valueType: KClass<*>?,
        bytes: ZBytes
    ): Result<Map<Any?, Any?>> {
        if (keyType != null && valueType != null &&
            Serializable::class.java.isAssignableFrom(keyType.java) &&
            Serializable::class.java.isAssignableFrom(valueType.java)
        ) {
            val keyCompanion = keyType.companionObject
            val keyFromFunction = keyCompanion?.declaredMemberFunctions?.find { it.name == "from" }
            if (keyFromFunction == null) {
                return Result.failure(Exception("Implementation of 'from' method from the ${Serializable::class} interface on the map key type not found.'"))
            }
            val valueCompanion = valueType.companionObject
            val valueFromFunction = valueCompanion?.declaredMemberFunctions?.find { it.name == "from" }
            if (valueFromFunction == null) {
                return Result.failure(Exception("Implementation of 'from' method from the ${Serializable::class} interface on the map value type not found."))
            }
            val byteArrayMap = JNIZBytes.deserializeIntoMap(bytes)
            val deserializedMap = byteArrayMap.map { (k, v) ->
                val key = keyFromFunction.call(keyType.companionObjectInstance, k)
                val value = valueFromFunction.call(valueType.companionObjectInstance, v)
                key to value
            }.toMap()
            return Result.success(deserializedMap)
        }
        return Result.failure(Exception("Key type or map type are do not implement the ${Serializable::class} interface."))
    }

    val deserializationMap: Map<KType, KFunction1<ZBytes, Any>> = mapOf(
        // Raw type deserialization methods
        typeOf<String>() to ::string_deserialize,
        typeOf<ByteArray>() to ::bytes_deserialize,
        typeOf<ZBytes>() to ::bytes_deserialize,
        typeOf<Byte>() to ::byte_deserialize,
        typeOf<Short>() to ::short_deserialize,
        typeOf<Int>() to ::int_deserialize,
        typeOf<Long>() to ::long_deserialize,
        typeOf<Float>() to ::float_deserialize,
        typeOf<Double>() to ::double_deserialize,

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

        // Map type deserialization
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
    )

    private fun string_deserialize(zbytes: ZBytes): String {
        return zbytes.toString()
    }

    private fun bytes_deserialize(zbytes: ZBytes): ByteArray {
        return zbytes.bytes
    }

    private fun byte_deserialize(zbytes: ZBytes): Byte = zbytes.toByte()

    private fun short_deserialize(zbytes: ZBytes): Short = zbytes.toShort()

    private fun int_deserialize(zbytes: ZBytes): Int = zbytes.toInt()

    private fun long_deserialize(zbytes: ZBytes): Long = zbytes.toLong()

    private fun float_deserialize(zbytes: ZBytes): Float = zbytes.toFloat()

    private fun double_deserialize(zbytes: ZBytes): Double = zbytes.toDouble()

    // List deserialization methods
    private fun list_string_deserialize(zbytes: ZBytes): List<String> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toString() }
    }

    private fun list_bytes_deserialize(zbytes: ZBytes): List<ByteArray> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.bytes }
    }

    private fun list_zbytes_deserialize(zbytes: ZBytes): List<ZBytes> {
        return JNIZBytes.deserializeIntoList(zbytes)
    }

    private fun list_int_deserialize(zbytes: ZBytes): List<Int> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toInt() }
    }

    private fun list_long_deserialize(zbytes: ZBytes): List<Long> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toLong() }.toList()
    }

    private fun list_float_deserialize(zbytes: ZBytes): List<Float> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toFloat() }.toList()
    }

    private fun list_double_deserialize(zbytes: ZBytes): List<Double> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toDouble() }.toList()
    }

    private fun list_short_deserialize(zbytes: ZBytes): List<Short> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toShort() }.toList()
    }

    private fun list_byte_deserialize(zbytes: ZBytes): List<Byte> {
        return JNIZBytes.deserializeIntoList(zbytes).map { it.toByte() }.toList()
    }

    // Map deserialization methods

    // Map<String, _>
    private fun map_string_string_deserialize(zbytes: ZBytes): Map<String, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toString() }
            .toMap()
    }

    private fun map_string_bytes_deserialize(zbytes: ZBytes): Map<String, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.bytes }.toMap()
    }

    private fun map_string_zbytes_deserialize(zbytes: ZBytes): Map<String, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v }.toMap()
    }

    private fun map_string_int_deserialize(zbytes: ZBytes): Map<String, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toInt() }.toMap()
    }

    private fun map_string_long_deserialize(zbytes: ZBytes): Map<String, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toLong() }.toMap()
    }

    private fun map_string_float_deserialize(zbytes: ZBytes): Map<String, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toFloat() }.toMap()
    }

    private fun map_string_double_deserialize(zbytes: ZBytes): Map<String, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toDouble() }.toMap()
    }

    private fun map_string_short_deserialize(zbytes: ZBytes): Map<String, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toShort() }.toMap()
    }

    private fun map_string_byte_deserialize(zbytes: ZBytes): Map<String, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toString() to v.toByte() }.toMap()
    }

    // Map<Byte, _>
    private fun map_byte_string_deserialize(zbytes: ZBytes): Map<Byte, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toString() }.toMap()
    }

    private fun map_byte_bytes_deserialize(zbytes: ZBytes): Map<Byte, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.bytes }.toMap()
    }

    private fun map_byte_zbytes_deserialize(zbytes: ZBytes): Map<Byte, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v }.toMap()
    }

    private fun map_byte_int_deserialize(zbytes: ZBytes): Map<Byte, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toInt() }.toMap()
    }

    private fun map_byte_long_deserialize(zbytes: ZBytes): Map<Byte, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toLong() }.toMap()
    }

    private fun map_byte_float_deserialize(zbytes: ZBytes): Map<Byte, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toFloat() }.toMap()
    }

    private fun map_byte_double_deserialize(zbytes: ZBytes): Map<Byte, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toDouble() }.toMap()
    }

    private fun map_byte_short_deserialize(zbytes: ZBytes): Map<Byte, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toShort() }.toMap()
    }

    private fun map_byte_byte_deserialize(zbytes: ZBytes): Map<Byte, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toByte() to v.toByte() }.toMap()
    }

    // Map<Short, _>
    private fun map_short_string_deserialize(zbytes: ZBytes): Map<Short, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toString() }.toMap()
    }

    private fun map_short_bytes_deserialize(zbytes: ZBytes): Map<Short, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.bytes }.toMap()
    }

    private fun map_short_zbytes_deserialize(zbytes: ZBytes): Map<Short, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v }.toMap()
    }

    private fun map_short_int_deserialize(zbytes: ZBytes): Map<Short, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toInt() }.toMap()
    }

    private fun map_short_long_deserialize(zbytes: ZBytes): Map<Short, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toLong() }.toMap()
    }

    private fun map_short_float_deserialize(zbytes: ZBytes): Map<Short, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toFloat() }.toMap()
    }

    private fun map_short_double_deserialize(zbytes: ZBytes): Map<Short, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toDouble() }.toMap()
    }

    private fun map_short_short_deserialize(zbytes: ZBytes): Map<Short, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toShort() }.toMap()
    }

    private fun map_short_byte_deserialize(zbytes: ZBytes): Map<Short, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toShort() to v.toByte() }.toMap()
    }

    // Map<Int, _>
    private fun map_int_string_deserialize(zbytes: ZBytes): Map<Int, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toString() }.toMap()
    }

    private fun map_int_bytes_deserialize(zbytes: ZBytes): Map<Int, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.bytes }.toMap()
    }

    private fun map_int_zbytes_deserialize(zbytes: ZBytes): Map<Int, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v }.toMap()
    }

    private fun map_int_int_deserialize(zbytes: ZBytes): Map<Int, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toInt() }.toMap()
    }

    private fun map_int_long_deserialize(zbytes: ZBytes): Map<Int, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toLong() }.toMap()
    }

    private fun map_int_float_deserialize(zbytes: ZBytes): Map<Int, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toFloat() }.toMap()
    }

    private fun map_int_double_deserialize(zbytes: ZBytes): Map<Int, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toDouble() }.toMap()
    }

    private fun map_int_short_deserialize(zbytes: ZBytes): Map<Int, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toShort() }.toMap()
    }

    private fun map_int_byte_deserialize(zbytes: ZBytes): Map<Int, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toInt() to v.toByte() }.toMap()
    }

    // Map<Long, _>
    private fun map_long_string_deserialize(zbytes: ZBytes): Map<Long, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toString() }.toMap()
    }

    private fun map_long_bytes_deserialize(zbytes: ZBytes): Map<Long, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.bytes }.toMap()
    }

    private fun map_long_zbytes_deserialize(zbytes: ZBytes): Map<Long, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v }.toMap()
    }

    private fun map_long_int_deserialize(zbytes: ZBytes): Map<Long, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toInt() }.toMap()
    }

    private fun map_long_long_deserialize(zbytes: ZBytes): Map<Long, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toLong() }.toMap()
    }

    private fun map_long_float_deserialize(zbytes: ZBytes): Map<Long, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toFloat() }.toMap()
    }

    private fun map_long_double_deserialize(zbytes: ZBytes): Map<Long, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toDouble() }.toMap()
    }

    private fun map_long_short_deserialize(zbytes: ZBytes): Map<Long, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toShort() }.toMap()
    }

    private fun map_long_byte_deserialize(zbytes: ZBytes): Map<Long, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toLong() to v.toByte() }.toMap()
    }

    // Map<Float, _>
    private fun map_float_string_deserialize(zbytes: ZBytes): Map<Float, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toString() }.toMap()
    }

    private fun map_float_bytes_deserialize(zbytes: ZBytes): Map<Float, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.bytes }.toMap()
    }

    private fun map_float_zbytes_deserialize(zbytes: ZBytes): Map<Float, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v }.toMap()
    }

    private fun map_float_int_deserialize(zbytes: ZBytes): Map<Float, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toInt() }.toMap()
    }

    private fun map_float_long_deserialize(zbytes: ZBytes): Map<Float, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toLong() }.toMap()
    }

    private fun map_float_float_deserialize(zbytes: ZBytes): Map<Float, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toFloat() }.toMap()
    }

    private fun map_float_double_deserialize(zbytes: ZBytes): Map<Float, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toDouble() }.toMap()
    }

    private fun map_float_short_deserialize(zbytes: ZBytes): Map<Float, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toShort() }.toMap()
    }

    private fun map_float_byte_deserialize(zbytes: ZBytes): Map<Float, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toFloat() to v.toByte() }.toMap()
    }

    // Map<Double, _>
    private fun map_double_string_deserialize(zbytes: ZBytes): Map<Double, String> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toString() }.toMap()
    }

    private fun map_double_bytes_deserialize(zbytes: ZBytes): Map<Double, ByteArray> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.bytes }.toMap()
    }

    private fun map_double_zbytes_deserialize(zbytes: ZBytes): Map<Double, ZBytes> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v }.toMap()
    }

    private fun map_double_int_deserialize(zbytes: ZBytes): Map<Double, Int> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toInt() }.toMap()
    }

    private fun map_double_long_deserialize(zbytes: ZBytes): Map<Double, Long> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toLong() }.toMap()
    }

    private fun map_double_float_deserialize(zbytes: ZBytes): Map<Double, Float> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toFloat() }.toMap()
    }

    private fun map_double_double_deserialize(zbytes: ZBytes): Map<Double, Double> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toDouble() }.toMap()
    }

    private fun map_double_short_deserialize(zbytes: ZBytes): Map<Double, Short> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toShort() }.toMap()
    }

    private fun map_double_byte_deserialize(zbytes: ZBytes): Map<Double, Byte> {
        return JNIZBytes.deserializeIntoMap(zbytes).map { (k, v) -> k.toDouble() to v.toByte() }.toMap()
    }
}
