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

import io.zenoh.handlers.Handler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.prelude.QoS
import io.zenoh.query.Reply
import io.zenoh.queryable.Query
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.*

class QueryableTest {

    companion object {
        const val testPayload = "Hello queryable"
    }

    private lateinit var session: Session
    private lateinit var testKeyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open().getOrThrow()
        testKeyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        testKeyExpr.close()
    }

    /** Test validating both Queryable and get operations. */
    @Test
    fun queryable_runsWithCallback() = runBlocking {
        val sample = Sample(
            testKeyExpr,
            Value(testPayload),
            SampleKind.PUT,
            TimeStamp(Date.from(Instant.now())),
            QoS.default()
        )
        val queryable = session.declareQueryable(testKeyExpr).with { query ->
            query.reply(testKeyExpr).success(sample.value).withTimeStamp(sample.timestamp!!).res()
        }.res().getOrThrow()

        var reply: Reply? = null
        val delay = Duration.ofMillis(1000)
        withTimeout(delay) {
            session.get(testKeyExpr).with { reply = it }.timeout(delay).res()
        }

        assertTrue(reply is Reply.Success)
        assertEquals((reply as Reply.Success).sample, sample)

        queryable.close()
    }

    @Test
    fun queryable_runsWithHandler() = runBlocking {
        val handler = QueryHandler()
        val queryable = session.declareQueryable(testKeyExpr).with(handler).res().getOrThrow()

        delay(500)

        val receivedReplies = ArrayList<Reply>()
        session.get(testKeyExpr).with { reply: Reply ->
            receivedReplies.add(reply)
        }.res()

        delay(500)

        queryable.close()
        assertTrue(receivedReplies.all { it is Reply.Success })
        assertEquals(handler.performedReplies.size, receivedReplies.size)
    }

    @Test
    fun queryableBuilder_channelHandlerIsTheDefaultHandler() = runBlocking {
        val queryable = session.declareQueryable(testKeyExpr).res().getOrThrow()
        assertTrue(queryable.receiver is Channel<Query>)
        queryable.close()
    }

    @Test
    fun queryTest() = runBlocking {
        var receivedQuery: Query? = null
        val queryable = session.declareQueryable(testKeyExpr).with { query -> receivedQuery = query }.res().getOrThrow()

        session.get(testKeyExpr).res()

        delay(1000)
        queryable.close()
        assertNotNull(receivedQuery)
        assertNull(receivedQuery!!.value)
    }

    @Test
    fun queryWithValueTest() = runBlocking {
        var receivedQuery: Query? = null
        val queryable = session.declareQueryable(testKeyExpr).with { query -> receivedQuery = query }.res().getOrThrow()

        session.get(testKeyExpr).withValue("Test value").res()

        delay(1000)
        queryable.close()
        assertNotNull(receivedQuery)
        assertEquals(Value("Test value"), receivedQuery!!.value)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun onCloseTest() = runBlocking {
        var onCloseWasCalled = false
        val queryable = session.declareQueryable(testKeyExpr).onClose { onCloseWasCalled = true }.res().getOrThrow()
        queryable.undeclare()

        assertTrue(onCloseWasCalled)
        assertTrue(queryable.receiver!!.isClosedForReceive)
    }
}

/** A dummy handler that replies "Hello queryable" followed by the count of replies performed. */
private class QueryHandler : Handler<Query, QueryHandler> {

    private var counter = 0

    val performedReplies: ArrayList<Sample> = ArrayList()

    override fun handle(t: Query) {
        reply(t)
    }

    override fun receiver(): QueryHandler {
        return this
    }

    override fun onClose() {}

    fun reply(query: Query) {
        val payload = "Hello queryable $counter!"
        counter++
        val sample = Sample(
            query.keyExpr,
            Value(payload),
            SampleKind.PUT,
            TimeStamp(Date.from(Instant.now())),
            QoS.default()
        )
        performedReplies.add(sample)
        query.reply(query.keyExpr).success(sample.value).withTimeStamp(sample.timestamp!!).res()
    }
}
