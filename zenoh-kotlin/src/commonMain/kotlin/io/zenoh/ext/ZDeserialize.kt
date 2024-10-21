package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.jni.JNIZBytes.deserializeViaJNI
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
 * - [UByte]
 * - [UShort]
 * - [UInt]
 * - [ULong]
 * - [List]
 * - [String]
 * - [ByteArray]
 * - [Map]
 * - [Pair]
 * - [Triple]
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
inline fun <reified T: Any> zDeserialize(zbytes: ZBytes): Result<T> = runCatching {
    deserializeViaJNI(zbytes, typeOf<T>()) as T
}
