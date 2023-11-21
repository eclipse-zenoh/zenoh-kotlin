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
import io.zenoh.query.Reply
import io.zenoh.queryable.Queryable
import io.zenoh.sample.Sample
import io.zenoh.selector.Selector
import io.zenoh.value.Value
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class GetTest {

    companion object {
        const val TEST_KEY_EXP = "example/testing/keyexpr"
        const val TEST_KEY_EXP_WILD = "example/testing/*"
        const val TEST_PAYLOAD = "Hello"
    }

    @Test
    fun get_runsWithCallback() {
        val sessionA = Session.open()

        val value = Value(TEST_PAYLOAD)
        val timeStamp = TimeStamp.getCurrentTime()
        val kind = SampleKind.PUT
        val keyExpr = TEST_KEY_EXP.intoKeyExpr()
        val queryable = sessionA.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr)
                .success(value)
                .withTimeStamp(timeStamp)
                .withKind(kind)
                .res()
        }.res()

        val sessionB = Session.open()

        sessionB.get(keyExpr).with { reply: Reply ->
            assertTrue(reply is Reply.Success)
            assertEquals(value, reply.sample.value)
            assertEquals(kind, reply.sample.kind)
            assertEquals(keyExpr, reply.sample.keyExpr)
            assertEquals(timeStamp, reply.sample.timestamp)
        }.timeout(Duration.ofMillis(1000)).res()

        Thread.sleep(1000)

        queryable.undeclare()
        sessionA.close()
        sessionB.close()
    }

    @Test
    fun getWithSelectorParamsTest() {
        val session = Session.open()

        var receivedParams = ""
        val keyExpr = TEST_KEY_EXP.intoKeyExpr()
        val queryable = session.declareQueryable(keyExpr).with { it.use { query ->
            receivedParams = query.parameters
        }}.res()

        val params = "arg1=val1,arg2=val2"
        val selector = Selector(keyExpr, params)
        session.get(selector).res()

        queryable.close()
        session.close()

        assertEquals(params, receivedParams)
    }

    @Test
    fun get_runsWithHandler() {
        val sessionA = Session.open()
        val repliedSamples: ArrayList<Sample> = ArrayList()
        val queryablesAmount = 3
        val declaredQueryables: ArrayList<Queryable<Unit>> = ArrayList()

        val value = Value(TEST_PAYLOAD)
        val timestamp = TimeStamp.getCurrentTime()
        val kind = SampleKind.PUT

        for (i in 1..queryablesAmount) {
            val keyExpr = KeyExpr.tryFrom(TEST_KEY_EXP + i.toString())
            val queryable = sessionA.declareQueryable(keyExpr).with { it.use { query ->
                    query.reply(keyExpr)
                        .success(value)
                        .withTimeStamp(timestamp)
                        .withKind(kind)
                        .res()
                    }
                }
                .res()

            declaredQueryables.add(queryable)
            repliedSamples.add(Sample(keyExpr, value, kind, timestamp))
        }

        val sessionB = Session.open()
        val receiver: ArrayList<Reply> =
            sessionB.get(TEST_KEY_EXP_WILD.intoKeyExpr())
                .with(GetHandler())
                .timeout(Duration.ofMillis(1000))
                .res()!!

        Thread.sleep(1000)
        declaredQueryables.forEach { queryable -> queryable.undeclare() }
        sessionA.close()
        sessionB.close()

        assertEquals(queryablesAmount, receiver.size)
        for (reply in receiver) {
            reply as Reply.Success
            val receivedSample = reply.sample
            assertEquals(value, receivedSample.value)
            assertEquals(SampleKind.PUT, receivedSample.kind)
            assertEquals(timestamp, receivedSample.timestamp)
        }
    }

    @Test
    fun get_runsWithBlockingQueue() {
        val sessionA = Session.open()

        val queryablesAmount = 3
        val declaredQueryables: ArrayList<Queryable<Unit>> = ArrayList()

        val value = Value(TEST_PAYLOAD)
        val timestamp = TimeStamp.getCurrentTime()
        val kind = SampleKind.PUT

        for (i in 1..queryablesAmount) {
            val keyExpr = (TEST_KEY_EXP + i.toString()).intoKeyExpr()
            val queryable = sessionA.declareQueryable(keyExpr).with { it.use { query ->
                    query.reply(keyExpr)
                        .success(value)
                        .withTimeStamp(timestamp)
                        .withKind(kind)
                        .res()
                    }
                }
                .res()

            declaredQueryables.add(queryable)
        }

        val receivedReplies = ArrayList<Reply>(0)

        val sessionB = Session.open()
        val receiver = sessionB.get(TEST_KEY_EXP_WILD.intoKeyExpr()).res()!!

        Thread.sleep(1000)

        val iterator = receiver.iterator()
        while (iterator.hasNext()) {
            try {
                val reply = iterator.next().get()
                receivedReplies.add(reply)
            } catch (e: Exception) {
                break
            }
        }

        sessionB.close()

        declaredQueryables.forEach { queryable -> queryable.undeclare() }
        sessionA.close()

        assertEquals(queryablesAmount, receivedReplies.size)
        for (reply in receivedReplies) {
            assert(reply is Reply.Success)
            val receivedSample = (reply as Reply.Success).sample
            assertEquals(value, receivedSample.value)
            assertEquals(SampleKind.PUT, receivedSample.kind)
            assertEquals(timestamp, receivedSample.timestamp)
        }
    }
}

/** A dummy handler for get operations. */
private class GetHandler : Handler<Reply, ArrayList<Reply>> {

    val performedReplies: ArrayList<Reply> = ArrayList()

    override fun handle(t: Reply) {
        performedReplies.add(t)
    }

    override fun receiver(): ArrayList<Reply> {
        return performedReplies
    }

    override fun onClose() {}
}
