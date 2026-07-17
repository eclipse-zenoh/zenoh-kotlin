package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.exceptions.zCall0
import io.zenoh.jni.bytes.serializeViaJNI
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * Serializes an element of type [T] into a [ZBytes].
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
 * This serialization utility can be used across the Zenoh ecosystem with Zenoh
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
 * @return a [Result] with the serialized [ZBytes].
 */
inline fun <reified T : Any> zSerialize(t: T): Result<ZBytes> = zSerializeImpl(t, typeOf<T>())

/**
 * Implementation of [zSerialize]: bridges the [KType] to a `java.lang.reflect.Type`
 * and calls the shared (de)serializer of the flat bindings tier.
 *
 * TODO(zenoh-flat-transition): the flat serializer does not yet support the
 * Kotlin-specific `UByte`/`UShort`/`UInt`/`ULong`/`Pair`/`Triple` types — those
 * serialize requests fail until a KType-aware serializer lands upstream.
 */
@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal fun zSerializeImpl(t: Any, type: KType): Result<ZBytes> =
    zCall0({ ByteArray(0) }) { serializeViaJNI(t, type.javaBoxedType(), it) }
        .map { ZBytes.from(it) }

/**
 * The [java.lang.reflect.Type] of this [KType], with a top-level primitive
 * boxed to its wrapper class: `typeOf<Int>().javaType` is the primitive
 * `int`, but the shared serializer works on reference types
 * (`java.lang.Integer`, …) — as the generic positions (where boxing is
 * inherent) already do.
 */
@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal fun KType.javaBoxedType(): Type {
    val jt = javaType
    return if (jt is Class<*> && jt.isPrimitive) jt.kotlin.javaObjectType else jt
}
