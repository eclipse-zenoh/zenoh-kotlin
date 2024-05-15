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
import io.zenoh.selector.Selector
import io.zenoh.value.Value
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.util.*
import kotlin.test.*

class GetTest {

    companion object {
        val value = Value("Test")
        val timestamp = TimeStamp.getCurrentTime()
        val kind = SampleKind.PUT
    }

    private lateinit var session: Session
    private lateinit var keyExpr: KeyExpr
    private lateinit var queryable: Queryable<Unit>

    @BeforeTest
    fun setUp() {
        session = Session.open().getOrThrow()
        keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(query.keyExpr)
                .success(value)
                .withTimeStamp(timestamp)
                .withKind(kind)
                .res()
        }.res().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        keyExpr.close()
        queryable.close()
    }

    @Test
    fun get_runsWithCallback() {
        var reply: Reply? = null
        session.get(keyExpr).with { reply = it }.timeout(Duration.ofMillis(1000)).res()

        assertTrue(reply is Reply.Success)
        val sample = (reply as Reply.Success).sample
        assertEquals(value, sample.value)
        assertEquals(kind, sample.kind)
        assertEquals(keyExpr, sample.keyExpr)
        assertEquals(timestamp, sample.timestamp)
    }

    @Test
    fun get_runsWithHandler() {
        val receiver: ArrayList<Reply> = session.get(keyExpr).with(TestHandler())
            .timeout(Duration.ofMillis(1000)).res().getOrThrow()!!

        for (reply in receiver) {
            reply as Reply.Success
            val receivedSample = reply.sample
            assertEquals(value, receivedSample.value)
            assertEquals(SampleKind.PUT, receivedSample.kind)
            assertEquals(timestamp, receivedSample.timestamp)
        }
    }

    @Test
    fun getWithSelectorParamsTest() {
        var receivedParams = String()
        var receivedParamsMap = mapOf<String, String?>()
        val queryable = session.declareQueryable(keyExpr).with { it.use { query ->
            receivedParams = query.parameters
            receivedParamsMap = query.selector.parametersStringMap().getOrThrow()
        }}.res().getOrThrow()

        val params = "arg1=val1&arg2=val2&arg3"
        val paramsMap = mapOf("arg1" to "val1", "arg2" to "val2", "arg3" to "")
        val selector = Selector(keyExpr, params)
        session.get(selector).with {}.timeout(Duration.ofMillis(1000)).res()

        queryable.close()

        assertEquals(params, receivedParams)
        assertEquals(paramsMap, receivedParamsMap)
    }
}

/** A dummy handler for get operations. */
private class TestHandler : Handler<Reply, ArrayList<Reply>> {

    val performedReplies: ArrayList<Reply> = ArrayList()

    override fun handle(t: Reply) {
        performedReplies.add(t)
    }

    override fun receiver(): ArrayList<Reply> {
        return performedReplies
    }

    override fun onClose() {}
}
