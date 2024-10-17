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

/**
 * //TODO: update documentation
 *
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
 * val zbytes: ZBytes = zSerialize<Int>(exampleInt).getOrThrow()
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
 * val zbytes = zSerialize<List<Int>>(myList).getOrThrow()
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
 * val zbytes = zSerialize<Map<String, Int>>(myMap).getOrThrow()
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
 * val deserializedInt = zDeserializeInt>(zbytes).getOrThrow()
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
 * payload = serialize(inputList).getOrThrow()
 * val outputList = zDeserializeList<String>>(payload).getOrThrow()
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
 * payload = serialize(inputMap).getOrThrow()
 * val outputMap = zDeserializeMap<String, String>>(payload).getOrThrow()
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
 * val serialization = zSerialize<Foo>(foo).getOrThrow()
 * ```
 *
 * Implementing the [IntoZBytes] interface on a class enables the possibility of serializing lists and maps
 * of that type, for instance:
 * ```kotlin
 * val list = listOf(Foo("bar"), Foo("buz"), Foo("fizz"))
 * val zbytes = zSerialize<List<Foo>>(list)
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
 * payload = serialize(inputFoo).getOrThrow()
 * val outputFoo = Foo.from(payload)
 * check(inputFoo == outputFoo)
 *
 * // List of Foo.
 * val inputListFoo = inputList.map { value -> Foo(value) }
 * payload = zSerialize<List<Foo>>(inputListFoo).getOrThrow()
 * val outputListFoo = zDeserializeList<ZBytes>>(payload).getOrThrow().map { zbytes -> Foo.from(zbytes) }
 * check(inputListFoo == outputListFoo)
 *
 * // Map of Foo.
 * val inputMapFoo = inputMap.map { (k, v) -> Foo(k) to Foo(v) }.toMap()
 * payload = zSerialize<Map<Foo, Foo>>(inputMapFoo).getOrThrow()
 * val outputMapFoo = zDeserializeMap<ZBytes, ZBytes>>(payload).getOrThrow()
 *     .map { (key, value) -> Foo.from(key) to Foo.from(value) }.toMap()
 * check(inputMapFoo == outputMapFoo)
 * ```
 */
class ZBytes internal constructor(internal val bytes: ByteArray) : IntoZBytes {

    override fun into(): ZBytes = this

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}

internal fun ByteArray.into(): ZBytes {
    return ZBytes(this)
}
