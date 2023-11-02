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
import io.zenoh.keyexpr.intoKeyExpr
import org.junit.Assert.assertThrows
import kotlin.test.*

class KeyExprTest {

    init {
        Zenoh.load()
    }

    @Test
    fun creation_TryFromTest() {
        // A couple of examples of valid and invalid key expressions.

        KeyExpr.tryFrom("example/test") // Should not throw exception

        assertThrows(Exception::class.java) { KeyExpr.tryFrom("example/test?param='test'") }

        KeyExpr.tryFrom("example/*/test") // Should not throw exception

        assertThrows(Exception::class.java) { KeyExpr.tryFrom("example/!*/test") }
    }

    @Test
    fun equalizationTest() {
        val keyExpr1 = KeyExpr.tryFrom("example/test")
        val keyExpr2 = KeyExpr.tryFrom("example/test")

        assertEquals(keyExpr1, keyExpr2)

        val keyExpr3 = KeyExpr.tryFrom("different/key/expr")
        assertNotEquals(keyExpr1, keyExpr3)

        keyExpr2.close()
        assertNotEquals(keyExpr1, keyExpr2)

        keyExpr1.close()
        assertNotEquals(keyExpr1, keyExpr2)
    }

    @Test
    fun creation_autocanonizeTest() {
        val keyExpr1 = KeyExpr.autocanonize("example/**/test")
        val keyExpr2 = KeyExpr.autocanonize("example/**/**/test")
        assertEquals(keyExpr1, keyExpr2)
    }

    @Test
    fun toStringTest() {
        val keyExprStr = "example/test/a/b/c"
        val keyExpr = KeyExpr.tryFrom(keyExprStr)

        assertEquals(keyExprStr, keyExpr.toString())

        keyExpr.close()
        assertTrue(keyExpr.toString().isEmpty())
    }

    @Test
    fun intersectionTest() {
        val keyExprA = KeyExpr.tryFrom("example/*/test")

        val keyExprB = KeyExpr.tryFrom("example/B/test")
        assertTrue(keyExprA.intersects(keyExprB))

        val keyExprC = KeyExpr.tryFrom("example/B/C/test")
        assertFalse(keyExprA.intersects(keyExprC))

        val keyExprA2 = KeyExpr.tryFrom("example/**")
        assertTrue(keyExprA2.intersects(keyExprC))
    }

    @Test
    fun includesTest() {
        val keyExpr = KeyExpr.tryFrom("example/**")
        val includedKeyExpr = KeyExpr.tryFrom("example/A/B/C/D")
        assertTrue(keyExpr.includes(includedKeyExpr))

        val notIncludedKeyExpr = KeyExpr.tryFrom("C/D")
        assertFalse(keyExpr.includes(notIncludedKeyExpr))
    }

    @Test
    fun sessionDeclarationTest() {
        val session = Session.open()
        val keyExpr = session.declareKeyExpr("a/b/c").res()
        assertEquals("a/b/c", keyExpr.toString())
        session.close()
        keyExpr.close()
    }

    @Test
    fun sessionUnDeclarationTest() {
        val session = Session.open()
        val keyExpr = session.declareKeyExpr("a/b/c").res()
        assertEquals("a/b/c", keyExpr.toString())

        session.undeclare(keyExpr).res() // Should not throw exception

        // Undeclaring a key expr that was not declared through a session.
        val keyExpr2 = "x/y/z".intoKeyExpr()
        assertThrows(SessionException::class.java) {session.undeclare(keyExpr2).res()}

        session.close()
        keyExpr.close()
        keyExpr2.close()
    }

    @Test
    fun keyExprIsValidAfterClosingSession() {
        val session = Session.open()
        val keyExpr = session.declareKeyExpr("a/b/c").res()
        session.close()

        assertTrue(keyExpr.isValid())
        assertFalse(keyExpr.toString().isEmpty()) // An operation such as toString that goes through JNI is still valid.
    }
}
