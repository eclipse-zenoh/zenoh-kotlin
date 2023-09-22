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
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.sample.Sample
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SessionTest {

    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @Test
    fun sessionStartCloseTest() {
        val session = Session.open().getOrThrow()
        assertTrue(session.isOpen())
        session.close()
        assertFalse(session.isOpen())
    }

    @Test
    fun sessionStop_stopUnopenedSessionIsNoOp() {
        val session = Session.open().getOrThrow()
        session.close()
    }

    @Test
    fun sessionClose_doesNotThrowExceptionWhenStoppingSessionWithActiveDeclarations() {
        val session = Session.open().getOrThrow()
        session.declarePublisher(TEST_KEY_EXP)
        session.close()
    }

    @Test
    fun sessionDeclare_sessionIsOpenFromInitialization() {
        val session = Session.open().getOrThrow()
        assertTrue(session.isOpen())
        session.close()
    }

    @Test
    fun sessionClose_succeedsDespiteNotFreeingAllDeclarations() {
        val session = Session.open().getOrThrow()
        session.declareQueryable(TEST_KEY_EXP).with {}.res()
        session.declareSubscriber(TEST_KEY_EXP).with {}.res()
        session.declarePublisher(TEST_KEY_EXP).res()
        session.close()
    }

    @Test
    fun sessionClose_declarationsAreAliveAfterClosingSessionTest() {
        val session = Session.open().getOrThrow()
        var receivedSample: Sample? = null

        val publisher = session.declarePublisher(TEST_KEY_EXP).res().getOrThrow()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with { sample -> receivedSample = sample }.res().getOrThrow()
        session.close()

        assertTrue(publisher.isValid())
        assertTrue(subscriber.isValid())

        publisher.put("Test").res()
        Thread.sleep(1000)
        assertNotNull(receivedSample)
        assertEquals("Test", receivedSample!!.value.payload.decodeToString())

        subscriber.close()
        publisher.close()
    }

    @Test
    fun sessionClose_newDeclarationsReturnNullAfterClosingSession() {
        val session = Session.open().getOrThrow()
        session.close()
        assertFailsWith<SessionException> { session.declarePublisher(TEST_KEY_EXP).res().getOrThrow() }
        assertFailsWith<SessionException> { session.declareSubscriber(TEST_KEY_EXP).with {}.res().getOrThrow() }
        assertFailsWith<SessionException> { session.declareQueryable(TEST_KEY_EXP).with {}.res().getOrThrow() }
    }

}
