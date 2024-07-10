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

import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ZBytesTest {

    @Test
    fun deserializeStringTest() {
        val bytes = ZBytes("Hello world".toByteArray())
        val deserialized = bytes.deserialize<String>().getOrThrow()
        assertEquals("Hello world", deserialized)
    }

    @Test
    fun deserializeByteTest() {
        val bytes = ZBytes(byteArrayOf(42))
        val deserialized = bytes.deserialize<Byte>().getOrThrow()
        assertEquals(42, deserialized) // 42 + 12*256 + 3*256^2 = 199722
    }

    @Test
    fun deserializeShortTest() {
        val bytes = ZBytes(byteArrayOf(42, 12))
        val deserialized = bytes.deserialize<Short>().getOrThrow()
        assertEquals(3114, deserialized) // 42 + 12*256 = 3114
    }

    @Test
    fun deserializeIntTest() {
        val bytes = ZBytes(byteArrayOf(42, 12, 3, 0))
        val deserialized = bytes.deserialize<Int>().getOrThrow()
        assertEquals(199722, deserialized) // 42 + 12*256 + 3*256^2 = 199722
    }

    @Test
    fun deserializeLongTest() {
        val bytes = ZBytes(byteArrayOf(42, 12, 3, 0, 0, 0, 0, 1))
        val deserialized = bytes.deserialize<Long>().getOrThrow()
        assertEquals(72057594038127658, deserialized) // 42 + 12*256 + 3*256^2 + 1*256^7= 72057594038127658
    }

    @Test
    fun deserializeFloatTest() {
        val pi = 3.141516f
        val bytes: ZBytes = ByteBuffer.allocate(Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(pi)
            .array()
            .into()

        val deserialized = bytes.deserialize<Float>().getOrThrow()
        assertEquals(pi, deserialized)
    }

    @Test
    fun deserializeDoubleTest() {
        val euler = 2.71828
        val bytes: ZBytes = ByteBuffer.allocate(Double.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(euler)
            .array()
            .into()

        val deserialized = bytes.deserialize<Double>().getOrThrow()
        assertEquals(euler, deserialized)
    }
}