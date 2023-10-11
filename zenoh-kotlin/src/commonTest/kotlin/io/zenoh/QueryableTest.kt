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
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.query.Reply
import io.zenoh.queryable.Query
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryableTest {

    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        const val TEST_PAYLOAD = "Hello queryable"
    }

    /** Test validating both Queryable and get operations. */
    @Test
    fun queryable_runsWithCallback() {
        val sessionA = Session.open().getOrThrow()

        val sample = Sample(
            TEST_KEY_EXP, Value(TEST_PAYLOAD), SampleKind.PUT, TimeStamp(Date.from(Instant.now()))
        )
        val queryable = sessionA.declareQueryable(TEST_KEY_EXP).with { query ->
            query.reply(TEST_KEY_EXP).success(sample.value).withTimeStamp(sample.timestamp!!).res()
        }.res().getOrThrow()

        val sessionB = Session.open().getOrThrow()

        sessionB.get(TEST_KEY_EXP).with { reply: Reply ->
            assertTrue(reply is Reply.Success)
            assertEquals(reply.sample, sample)
        }.timeout(Duration.ofMillis(1000)).res()

        Thread.sleep(1000)

        queryable.undeclare()
        sessionA.close()
        sessionB.close()
    }

    @Test
    fun queryable_runsWithHandler() {
        val sessionA = Session.open().getOrThrow()
        val handler = QueryHandler()
        val queryable = sessionA.declareQueryable(TEST_KEY_EXP).with(handler).res().getOrThrow()

        val sessionB = Session.open().getOrThrow()
        val receivedReplies = ArrayList<Reply>()
        sessionB.get(TEST_KEY_EXP).with { reply: Reply ->
            receivedReplies.add(reply)
        }.timeout(Duration.ofMillis(1000)).res()

        Thread.sleep(1000)

        queryable.undeclare()
        sessionA.close()
        sessionB.close()

        for (receivedReply in receivedReplies) {
            assertTrue(receivedReply is Reply.Success)
        }
        assertEquals(handler.performedReplies, receivedReplies.map { reply -> (reply as Reply.Success).sample })
    }

    @Test
    fun queryableBuilder_channelHandlerIsTheDefaultHandler() {
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(TEST_KEY_EXP).res().getOrThrow()
        assertTrue(queryable.receiver is Channel<Query>)
    }

    @Test
    fun queryTest() {
        val session = Session.open().getOrThrow()
        var receivedQuery: Query? = null
        session.declareQueryable(TEST_KEY_EXP).with { query -> receivedQuery = query }.res().getOrThrow()

        session.get(TEST_KEY_EXP).res()
        session.close()

        Thread.sleep(1000)
        assertNotNull(receivedQuery)
        assertNull(receivedQuery!!.value)
    }

    @Test
    fun queryWithValueTest() {
        val session = Session.open().getOrThrow()
        var receivedQuery: Query? = null
        session.declareQueryable(TEST_KEY_EXP).with { query -> receivedQuery = query }.res().getOrThrow()

        session.get(TEST_KEY_EXP).withValue("Test value").res()
        session.close()
        Thread.sleep(1000)

        assertNotNull(receivedQuery)
        assertEquals(Value("Test value"), receivedQuery!!.value)

    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun onFinishTest() {
        val session = Session.open().getOrThrow()
        var onFinishWasCalled = false
        val queryable = session.declareQueryable(TEST_KEY_EXP).onFinish { onFinishWasCalled = true }.res().getOrThrow()
        queryable.undeclare()
        assertTrue(onFinishWasCalled)
        assertTrue(queryable.receiver!!.isClosedForReceive)
        session.close()
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

    override fun onFinish() {}

    fun reply(query: Query) {
        val payload = "Hello queryable $counter!"
        counter++
        val sample = Sample(
            query.keyExpr, Value(payload), SampleKind.PUT, TimeStamp(Date.from(Instant.now()))
        )
        performedReplies.add(sample)
        query.reply(query.keyExpr).success(sample.value).withTimeStamp(sample.timestamp!!).res()
    }
}
