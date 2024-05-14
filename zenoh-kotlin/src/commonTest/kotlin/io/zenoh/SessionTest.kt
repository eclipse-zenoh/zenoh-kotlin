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
import io.zenoh.sample.Sample
import kotlinx.coroutines.runBlocking
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
    fun sessionClose_succeedsDespiteNotFreeingAllDeclarations() {
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(testKeyExpr).with {}.res().getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr).with {}.res().getOrThrow()
        val publisher = session.declarePublisher(testKeyExpr).res().getOrThrow()
        session.close()

        queryable.close()
        subscriber.close()
        publisher.close()
    }

    @Test
    fun sessionClose_declarationsAreAliveAfterClosingSessionTest() = runBlocking {
        val session = Session.open().getOrThrow()
        var receivedSample: Sample? = null

        val publisher = session.declarePublisher(testKeyExpr).res().getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr).with { sample -> receivedSample = sample }.res().getOrThrow()
        session.close()

        assertTrue(publisher.isValid())
        assertTrue(subscriber.isValid())

        publisher.put("Test").res()
        assertNotNull(receivedSample)
        assertEquals("Test", receivedSample!!.value.payload.decodeToString())

        subscriber.close()
        publisher.close()
    }

    @Test
    fun sessionClose_newDeclarationsReturnNullAfterClosingSession() {
        val session = Session.open().getOrThrow()
        session.close()
        assertFailsWith<SessionException> { session.declarePublisher(testKeyExpr).res().getOrThrow() }
        assertFailsWith<SessionException> { session.declareSubscriber(testKeyExpr).with {}.res().getOrThrow() }
        assertFailsWith<SessionException> { session.declareQueryable(testKeyExpr).with {}.res().getOrThrow() }
    }

}
