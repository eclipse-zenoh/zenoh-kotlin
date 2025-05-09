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

import io.zenoh.exceptions.ZError
import io.zenoh.ext.zSerialize
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
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
        val session = Session.open(Config.default()).getOrThrow()
        assertFalse(session.isClosed())
        session.close()
        assertTrue(session.isClosed())
    }

    @Test
    fun sessionClose_succeedsDespiteNotFreeingAllDeclarations() {
        val session = Session.open(Config.default()).getOrThrow()
        val queryable = session.declareQueryable(testKeyExpr, callback = {}).getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow()
        val publisher = session.declarePublisher(testKeyExpr).getOrThrow()
        session.close()

        queryable.close()
        subscriber.close()
        publisher.close()
    }

    @Test
    fun sessionClose_declarationsAreUndeclaredAfterClosingSessionTest() = runBlocking {
        val session = Session.open(Config.default()).getOrThrow()

        val publisher = session.declarePublisher(testKeyExpr).getOrThrow()
        val subscriber = session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow()
        session.close()

        assertFalse(publisher.isValid())
        assertFalse(subscriber.isValid())

        assertTrue(publisher.put(zSerialize("Test").getOrThrow()).isFailure)
    }

    @Test
    fun sessionClose_newDeclarationsReturnNullAfterClosingSession() {
        val session = Session.open(Config.default()).getOrThrow()
        session.close()
        assertFailsWith<ZError> { session.declarePublisher(testKeyExpr).getOrThrow() }
        assertFailsWith<ZError> { session.declareSubscriber(testKeyExpr, callback = {}).getOrThrow() }
        assertFailsWith<ZError> { session.declareQueryable(testKeyExpr, callback = {}).getOrThrow() }
    }

}
