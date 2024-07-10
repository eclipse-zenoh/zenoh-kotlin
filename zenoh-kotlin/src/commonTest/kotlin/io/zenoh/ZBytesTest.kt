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
import kotlin.test.*

class ZBytesTest {

    @Test
    fun deserializeStringTest() {
        val bytes = ZBytes("Hello world".toByteArray())
        val deserialized = bytes.deserialize<String>().getOrThrow()
        assertEquals("Hello world", deserialized)
    }

    @Test
    fun deserializeIntTest() {
        val bytes = ZBytes(byteArrayOf(42, 12, 3, 0))
        val deserialized = bytes.deserialize<Int>().getOrThrow()
        assertEquals(199722, deserialized) // 42 + 12*256 + 3*256^2 = 199722
    }
}