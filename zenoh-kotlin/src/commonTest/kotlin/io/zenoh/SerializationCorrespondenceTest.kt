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

package io.zenoh

import io.zenoh.bytes.ZBytes
import io.zenoh.ext.zDeserialize
import io.zenoh.ext.zSerialize
import io.zenoh.jni.JniErrorHandler
import io.zenoh.jni.bytes.serializeViaJNIKType
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Correspondence test for the pure-Kotlin serializer ([io.zenoh.jni.bytes.SerializationCodec],
 * reached through `zSerialize`/`zDeserialize`) against the native zenoh-ext
 * serializer ([serializeViaJNIKType]) — the same pattern as
 * `ParametersCorrespondenceTest` in zenoh-java. For each value the pure output
 * must be **byte-identical** to the native one, and the pure deserializer must
 * round-trip the native bytes. Inputs are edge values plus a seeded-`Random`
 * corpus, so runs are reproducible.
 */
class SerializationCorrespondenceTest {

    private val boom = JniErrorHandler<ByteArray> { je ->
        throw AssertionError("native serializer error: $je")
    }

    /** Pure bytes == native bytes, and pure round-trips the native bytes. */
    private inline fun <reified T : Any> assertCorresponds(value: T) {
        val pure = zSerialize(value).getOrThrow().toBytes()
        val native = serializeViaJNIKType(value, typeOf<T>(), boom)
        assertContentEquals(native, pure, "serialize mismatch for <$value>")
        val back = zDeserialize<T>(ZBytes.from(native)).getOrThrow()
        assertEquals(value, back, "round-trip mismatch for <$value>")
    }

    // ── Golden vectors from the zenoh serialization RFC (no native lib) ──────

    @Test
    fun goldenVectorsMatchTheRfc() {
        assertContentEquals(byteArrayOf(134.toByte(), 214.toByte(), 18, 0), zSerialize(1234566).getOrThrow().toBytes())
        assertContentEquals(byteArrayOf(4, 116, 101, 115, 116), zSerialize("test").getOrThrow().toBytes())
        // (u16 500, f32 1234.0, "test") — but the Kotlin surface has no bare tuple
        // serializer for mixed arity; validate the scalars individually instead.
        assertContentEquals(byteArrayOf(244.toByte(), 1), zSerialize(500.toUShort()).getOrThrow().toBytes())
        assertContentEquals(byteArrayOf(0, 64, 154.toByte(), 68), zSerialize(1234.0f).getOrThrow().toBytes())
    }

    // ── Scalars ─────────────────────────────────────────────────────────────

    @Test
    fun scalarsMatchNative() {
        val rng = Random(20260721)
        assertCorresponds(true); assertCorresponds(false)
        for (edge in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) assertCorresponds(edge)
        for (edge in listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE)) assertCorresponds(edge)
        assertCorresponds(Byte.MIN_VALUE); assertCorresponds(Byte.MAX_VALUE)
        assertCorresponds(Short.MIN_VALUE); assertCorresponds(Short.MAX_VALUE)
        assertCorresponds(3.1415f); assertCorresponds(Float.MIN_VALUE); assertCorresponds(Float.MAX_VALUE)
        assertCorresponds(2.718281828); assertCorresponds(Double.MAX_VALUE)
        repeat(200) {
            assertCorresponds(rng.nextInt())
            assertCorresponds(rng.nextLong())
            assertCorresponds(rng.nextInt().toShort())
            assertCorresponds(rng.nextInt().toByte())
            assertCorresponds(Float.fromBits(rng.nextInt()))
            assertCorresponds(Double.fromBits(rng.nextLong()))
        }
    }

    @Test
    fun unsignedMatchNative() {
        val rng = Random(20260722)
        assertCorresponds(UByte.MIN_VALUE); assertCorresponds(UByte.MAX_VALUE)
        assertCorresponds(UShort.MIN_VALUE); assertCorresponds(UShort.MAX_VALUE)
        assertCorresponds(UInt.MIN_VALUE); assertCorresponds(UInt.MAX_VALUE)
        assertCorresponds(ULong.MIN_VALUE); assertCorresponds(ULong.MAX_VALUE)
        repeat(200) {
            assertCorresponds(rng.nextInt().toUByte())
            assertCorresponds(rng.nextInt().toUShort())
            assertCorresponds(rng.nextInt().toUInt())
            assertCorresponds(rng.nextLong().toULong())
        }
    }

    @Test
    fun stringsAndBytesMatchNative() {
        val rng = Random(20260723)
        for (s in listOf("", "test", "ключ", "→∑≈", "a b\tc\n")) assertCorresponds(s)
        assertContentEquals(
            serializeViaJNIKType(byteArrayOf(1, 2, 3, 4), typeOf<ByteArray>(), boom),
            zSerialize(byteArrayOf(1, 2, 3, 4)).getOrThrow().toBytes(),
        )
        repeat(100) {
            val s = buildString { repeat(rng.nextInt(0, 32)) { append(rng.nextInt(0x20, 0x7E).toChar()) } }
            assertCorresponds(s)
        }
    }

    // ── Containers, tuples, nesting ─────────────────────────────────────────

    @Test
    fun containersAndTuplesMatchNative() {
        val rng = Random(20260724)
        assertCorresponds(listOf(1, 2, 3, 4, 5))
        assertCorresponds(emptyList<Int>())
        assertCorresponds(listOf("a", "bb", "ccc"))
        assertCorresponds(listOf(true, false, true))
        assertCorresponds(mapOf("a" to 1, "b" to 2))
        assertCorresponds(mapOf("x" to 10uL, "y" to 20uL))
        assertCorresponds(1 to 2.5)
        assertCorresponds(Triple(1, 2.5, listOf(true, false)))
        // Nested
        assertCorresponds(listOf(mapOf("a" to 1uL), mapOf("b" to 2uL)))
        assertCorresponds(mapOf("k" to listOf(1, 2, 3)))
        assertCorresponds(1 to (2.5 to false))
        repeat(100) {
            assertCorresponds(List(rng.nextInt(0, 8)) { rng.nextInt() })
            assertCorresponds((0 until rng.nextInt(0, 6)).associate { "k$it" to rng.nextLong() })
            assertCorresponds(rng.nextInt() to rng.nextLong().toULong())
        }
    }

    // ── Perf comparison (pure Kotlin vs per-element JNI) ────────────────────

    @Test
    fun perfPureVsJni() {
        val warmup = 20_000
        val n = 200_000L
        // Small payloads — where per-element JNI chatter dominates.
        benchPair("Int", 42, warmup, n)
        benchPair("List<Int>(4)", listOf(1, 2, 3, 4), warmup, n)
        benchPair("Map<String,Int>(2)", mapOf("a" to 1, "b" to 2), warmup, n)
    }

    private inline fun <reified T : Any> benchPair(label: String, value: T, warmup: Int, n: Long) {
        val kt = typeOf<T>()
        repeat(warmup) { zSerialize(value); serializeViaJNIKType(value, kt, boom) }
        val pureNs = time(n) { zSerialize(value) }
        val jniNs = time(n) { serializeViaJNIKType(value, kt, boom) }
        println(
            "serialize %-20s pure=%8.1f ns/op   jni=%9.1f ns/op   speedup=%.1fx"
                .format(label, pureNs, jniNs, jniNs / pureNs)
        )
    }

    private inline fun time(n: Long, body: () -> Unit): Double {
        val start = System.nanoTime()
        var i = 0L
        while (i < n) { body(); i++ }
        return (System.nanoTime() - start).toDouble() / n
    }
}
