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
import io.zenoh.prelude.SampleKind
import io.zenoh.protocol.into
import io.zenoh.query.Reply
import io.zenoh.queryable.Queryable
import io.zenoh.selector.Parameters
import io.zenoh.selector.Selector
import io.zenoh.selector.intoSelector
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.util.*
import kotlin.test.*

class GetTest {

    companion object {
        val payload = "Test".into()
        val timestamp = TimeStamp.getCurrentTime()
        val kind = SampleKind.PUT
    }

    private lateinit var session: Session
    private lateinit var selector: Selector
    private lateinit var queryable: Queryable<Unit>

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        selector = "example/testing/keyexpr".intoSelector().getOrThrow()
        queryable = session.declareQueryable(selector.keyExpr, callback = { query ->
            query.reply(query.keyExpr, payload, timestamp = timestamp)
        }).getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        selector.close()
        queryable.close()
    }

    @Test
    fun get_runsWithCallback() {
        var reply: Reply? = null
        session.get(selector, callback = {
            reply = it
        }, timeout = Duration.ofMillis(1000))

        assertNotNull(reply)
        val sample = reply!!.result.getOrThrow()
        assertEquals(payload, sample.payload)
        assertEquals(kind, sample.kind)
        assertEquals(selector.keyExpr, sample.keyExpr)
        assertEquals(timestamp, sample.timestamp)
    }

    @Test
    fun get_runsWithHandler() {
        val receiver: ArrayList<Reply> = session.get(selector, handler = TestHandler(), timeout = Duration.ofMillis(1000)).getOrThrow()

        for (reply in receiver) {
            val receivedSample = reply.result.getOrThrow()
            assertEquals(payload, receivedSample.payload)
            assertEquals(SampleKind.PUT, receivedSample.kind)
            assertEquals(timestamp, receivedSample.timestamp)
        }
    }

    @Test
    fun getWithSelectorParamsTest() {
        var receivedParams: Parameters? = null
        val queryable = session.declareQueryable(selector.keyExpr, callback = { query ->
            receivedParams = query.parameters
        }).getOrThrow()

        val params = Parameters.from("arg1=val1&arg2=val2&arg3").getOrThrow()
        val selectorWithParams = Selector(selector.keyExpr, params)
        session.get(selectorWithParams, callback = {}, timeout = Duration.ofMillis(1000))

        queryable.close()

        assertEquals(params, receivedParams)
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
