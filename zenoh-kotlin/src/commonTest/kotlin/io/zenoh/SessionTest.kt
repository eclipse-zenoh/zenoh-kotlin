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
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.*

class SessionTest {
    
    private lateinit var testKeyExpr: KeyExpr
    @BeforeTest
    fun setUp() {
        testKeyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        testKeyExpr.close()
    }

    @Test
    fun sessionStartCloseTest() {
        val session = Session.open().getOrThrow()
        assertTrue(session.isOpen())
        session.close()
        assertFalse(session.isOpen())
    }

    @Test
    fun sessionOpeningFailure() {
        val invalidConfig = Config.from(Path.of("invalid"))
        assertFailsWith<SessionException> { Session.open(invalidConfig).getOrThrow() }
    }

    @Test
    fun sessionClose_succeedsDespiteNotFreeingAllDeclarations() {
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(testKeyExpr).with {}.wait().getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow()
        val publisher = session.declarePublisher(testKeyExpr).getOrThrow()
        session.close()

        queryable.close()
        subscriber.close()
        publisher.close()
    }

    @Test
    fun sessionClose_declarationsAreUndeclaredAfterClosingSessionTest() = runBlocking {
        val session = Session.open().getOrThrow()

        val publisher = session.declarePublisher(testKeyExpr).getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow()
        session.close()

        assertFalse(publisher.isValid())
        assertFalse(subscriber.isValid())

        assertTrue(publisher.put("Test").isFailure)
    }

    @Test
    fun sessionClose_newDeclarationsReturnNullAfterClosingSession() {
        val session = Session.open().getOrThrow()
        session.close()
        assertFailsWith<SessionException> { session.declarePublisher(testKeyExpr).getOrThrow() }
        assertFailsWith<SessionException> { session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow() }
        assertFailsWith<SessionException> { session.declareQueryable(testKeyExpr).with {}.wait().getOrThrow() }
    }

}
