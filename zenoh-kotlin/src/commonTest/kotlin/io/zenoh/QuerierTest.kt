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

import io.zenoh.annotations.Unstable
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.ZBytes
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.qos.QoS
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import io.zenoh.sample.SampleKind
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ntp.TimeStamp
import java.lang.Thread.sleep
import java.time.Instant
import java.util.*
import kotlin.test.*

class QuerierTest {

    companion object {
        val testPayload = ZBytes.from("Hello queryable")
    }

    private lateinit var session: Session
    private lateinit var testKeyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        testKeyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        testKeyExpr.close()
    }

    /** Test validating both Queryable and get operations. */
    @OptIn(Unstable::class)
    @Test
    fun querier_runsWithCallback() = runBlocking {
        val sample = Sample(
            testKeyExpr,
            testPayload,
            Encoding.default(),
            SampleKind.PUT,
            TimeStamp(Date.from(Instant.now())),
            QoS.defaultRequest
        )
        val examplePayload = ZBytes.from("Example payload")
        val exampleAttachment = ZBytes.from("Example attachment")

        val queryable = session.declareQueryable(testKeyExpr, callback = { query ->
            assertEquals(exampleAttachment, query.attachment)
            assertEquals(examplePayload, query.payload)
            query.reply(testKeyExpr, payload = sample.payload, timestamp = sample.timestamp)
        }).getOrThrow()

        val querier = session.declareQuerier(testKeyExpr).getOrThrow()

        var receivedReply: Reply? = null
        querier.get(
            callback = { reply -> receivedReply = reply},
            payload = examplePayload,
            attachment = exampleAttachment
        )
        sleep(1000)

        assertEquals(sample, receivedReply?.result?.getOrThrow())

        queryable.close()
        querier.close()
    }
}
