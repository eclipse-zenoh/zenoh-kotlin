package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.exceptions.zCall0
import io.zenoh.jni.bytes.deserializeViaJNI
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Deserialize the [ZBytes] instance into an element of type [T].
 *
 * This function supports the following types:
 * - [Boolean]
 * - [Byte]
 * - [Short]
 * - [Int]
 * - [Long]
 * - [Float]
 * - [Double]
 * - [List]
 * - [String]
 * - [ByteArray]
 * - [Map]
 *
 * **NOTE**
 *
 * This deserialization utility can be used across the Zenoh ecosystem with Zenoh
 * versions based on other supported languages such as Rust, Python, C and C++.
 * This works when the types are equivalent (a `Byte` corresponds to an `i8` in Rust, a `Short` to an `i16`, etc).
 *
 * ### Examples
 *
 * For a Boolean:
 * ```
 * val input: Boolean = true
 * val zbytes = zSerialize(input).getOrThrow()
 * val output = zDeserialize<Boolean>(zbytes).getOrThrow()
 * check(input == output)
 * ```
 *
 * For a List:
 * ```
 * val input: List<Int> = listOf(1, 2, 3, 4, 5)
 * val zbytes = zSerialize(input).getOrThrow()
 * val output = zDeserialize<List<Int>>(zbytes).getOrThrow()
 * check(input == output)
 * ```
 *
 * For a nested list:
 * ```
 * val input: List<List<Int>> = listOf(listOf(1, 2, 3))
 * val zbytes = zSerialize(input).getOrThrow()
 * val output = zDeserialize<List<List<Int>>>(zbytes).getOrThrow()
 * check(input == output)
 * ```
 *
 * For a combined list of maps:
 * ```
 * val input: List<Map<String, Int>> = listOf(mapOf("a" to 1, "b" to 2))
 * val zbytes = zSerialize(input).getOrThrow()
 * val output = zDeserialize<List<Map<String, Int>>>(zbytes).getOrThrow()
 * check(input == output)
 * ```
 *
 * For additional examples, checkout the [ZBytes examples](https://github.com/eclipse-zenoh/zenoh-kotlin/blob/main/examples/src/main/kotlin/io.zenoh/ZBytes.kt).
 *
 * @see ZBytes
 * @return a [Result] with the deserialization.
 */
inline fun <reified T : Any> zDeserialize(zbytes: ZBytes): Result<T> =
    zDeserializeImpl(zbytes, typeOf<T>()).map { it as T }

/**
 * Implementation of [zDeserialize]: bridges the [KType] to a
 * `java.lang.reflect.Type` and calls the shared (de)serializer of the flat
 * bindings tier.
 *
 * TODO(zenoh-flat-transition): the flat deserializer does not yet support the
 * Kotlin-specific `UByte`/`UShort`/`UInt`/`ULong`/`Pair`/`Triple` types — those
 * deserialize requests fail until a KType-aware serializer lands upstream.
 */
@PublishedApi
internal fun zDeserializeImpl(zbytes: ZBytes, type: KType): Result<Any> =
    zCall0<Any>({ Unit }) { deserializeViaJNI(zbytes.toBytes(), type.javaBoxedType(), it) }
