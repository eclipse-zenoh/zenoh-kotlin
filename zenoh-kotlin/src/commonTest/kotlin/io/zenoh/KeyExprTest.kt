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

import io.zenoh.exceptions.SessionException
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.SetIntersectionLevel
import io.zenoh.keyexpr.intoKeyExpr
import kotlin.test.*

class KeyExprTest {

    @Test
    fun creation_TryFromTest() {
        // A couple of examples of valid and invalid key expressions.
        val keyExpr = KeyExpr.tryFrom("example/test")
        assertTrue(keyExpr.isSuccess)

        val keyExpr2 = KeyExpr.tryFrom("example/test?param='test'")
        assertTrue(keyExpr2.isFailure)

        val keyExpr3 = KeyExpr.tryFrom("example/*/test")
        assertTrue(keyExpr3.isSuccess)

        val keyExpr4 = KeyExpr.tryFrom("example/!*/test")
        assertTrue(keyExpr4.isFailure)
    }

    @Test
    fun equalizationTest() {
        val keyExpr1 = KeyExpr.tryFrom("example/test").getOrThrow()
        val keyExpr2 = KeyExpr.tryFrom("example/test").getOrThrow()

        assertEquals(keyExpr1, keyExpr2)

        val keyExpr3 = KeyExpr.tryFrom("different/key/expr").getOrThrow()
        assertNotEquals(keyExpr1, keyExpr3)

        keyExpr2.close()
        assertEquals(keyExpr1, keyExpr2)

        keyExpr1.close()
        assertEquals(keyExpr1, keyExpr2)
    }

    @Test
    fun creation_autocanonizeTest() {
        val keyExpr1 = KeyExpr.autocanonize("example/**/test").getOrThrow()
        val keyExpr2 = KeyExpr.autocanonize("example/**/**/test").getOrThrow()
        assertEquals(keyExpr1, keyExpr2)
    }

    @Test
    fun toStringTest() {
        val keyExprStr = "example/test/a/b/c"
        val keyExpr = KeyExpr.tryFrom(keyExprStr).getOrThrow()
        assertEquals(keyExprStr, keyExpr.toString())
        keyExpr.close()
        assertEquals(keyExprStr, keyExpr.toString())
    }

    @Test
    fun intersectionTest() {
        val keyExprA = KeyExpr.tryFrom("example/*/test").getOrThrow()

        val keyExprB = KeyExpr.tryFrom("example/B/test").getOrThrow()
        assertTrue(keyExprA.intersects(keyExprB))

        val keyExprC = KeyExpr.tryFrom("example/B/C/test").getOrThrow()
        assertFalse(keyExprA.intersects(keyExprC))

        val keyExprA2 = KeyExpr.tryFrom("example/**").getOrThrow()
        assertTrue(keyExprA2.intersects(keyExprC))
    }

    @Test
    fun includesTest() {
        val keyExpr = KeyExpr.tryFrom("example/**").getOrThrow()
        val includedKeyExpr = KeyExpr.tryFrom("example/A/B/C/D").getOrThrow()
        assertTrue(keyExpr.includes(includedKeyExpr))

        val notIncludedKeyExpr = KeyExpr.tryFrom("C/D").getOrThrow()
        assertFalse(keyExpr.includes(notIncludedKeyExpr))
    }

    @Test
    fun sessionDeclarationTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = session.declareKeyExpr("a/b/c").getOrThrow()
        assertEquals("a/b/c", keyExpr.toString())
        session.close()
        keyExpr.close()
    }

    @Test
    fun sessionUnDeclarationTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = session.declareKeyExpr("a/b/c").getOrThrow()
        assertEquals("a/b/c", keyExpr.toString())

        val undeclare1 = session.undeclare(keyExpr)
        assertTrue(undeclare1.isSuccess)

        // Undeclaring twice a key expression shall fail.
        val undeclare2 = session.undeclare(keyExpr)
        assertTrue(undeclare2.isFailure)
        assertTrue(undeclare2.exceptionOrNull() is SessionException)

        // Undeclaring a key expr that was not declared through a session.
        val keyExpr2 = "x/y/z".intoKeyExpr().getOrThrow()
        val undeclare3 = session.undeclare(keyExpr2)
        assertTrue(undeclare3.isFailure)
        assertTrue(undeclare3.exceptionOrNull() is SessionException)

        session.close()
        keyExpr.close()
        keyExpr2.close()
    }

    @Test
    fun `relationTo returns includes test`() {
        val keyExprA = KeyExpr.tryFrom("A/**").getOrThrow()
        val keyExprB = KeyExpr.tryFrom("A/B/C").getOrThrow()

        assertEquals(SetIntersectionLevel.INCLUDES, keyExprA.relationTo(keyExprB))
    }

    @Test
    fun `relationTo returns intersect test`() {
        val keyExprA = KeyExpr.tryFrom("A/*/C/D").getOrThrow()
        val keyExprB = KeyExpr.tryFrom("A/B/C/*").getOrThrow()

        assertEquals(SetIntersectionLevel.INTERSECTS, keyExprA.relationTo(keyExprB))
    }

    @Test
    fun `relationTo returns equals test`() {
        val keyExprA = KeyExpr.tryFrom("A/B/C").getOrThrow()
        val keyExprB = KeyExpr.tryFrom("A/B/C").getOrThrow()

        assertEquals(SetIntersectionLevel.EQUALS, keyExprA.relationTo(keyExprB))
    }

    @Test
    fun `relationTo returns disjoint test`() {
        val keyExprA = KeyExpr.tryFrom("A/B/C").getOrThrow()
        val keyExprB = KeyExpr.tryFrom("D/E/F").getOrThrow()

        assertEquals(SetIntersectionLevel.DISJOINT, keyExprA.relationTo(keyExprB))
    }

    @Test
    fun `join test`() {
        val keyExprA = KeyExpr.tryFrom("A/B").getOrThrow()
        val keyExprExpected = KeyExpr.tryFrom("A/B/C/D").getOrThrow()

        val keyExprJoined = keyExprA.join("C/D").getOrThrow()
        assertEquals(keyExprExpected, keyExprJoined)
    }
}
