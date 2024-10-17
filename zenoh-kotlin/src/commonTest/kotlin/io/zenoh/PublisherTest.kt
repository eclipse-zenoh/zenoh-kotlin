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

import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.sample.SampleKind
import io.zenoh.ext.zSerialize
import io.zenoh.pubsub.Publisher
import io.zenoh.sample.Sample
import io.zenoh.pubsub.Subscriber
import kotlin.test.*

class PublisherTest {

    lateinit var session: Session
    lateinit var receivedSamples: ArrayList<Sample>
    lateinit var publisher: Publisher
    lateinit var subscriber: Subscriber<Unit>
    lateinit var keyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        publisher = session.declarePublisher(keyExpr, encoding = Encoding.ZENOH_STRING).getOrThrow()
        subscriber = session.declareSubscriber(keyExpr, callback = { sample ->
            receivedSamples.add(sample)
        }).getOrThrow()
        receivedSamples = ArrayList()
    }

    @AfterTest
    fun tearDown() {
        publisher.close()
        subscriber.close()
        session.close()
        keyExpr.close()
    }

    @Test
    fun putTest() {

        val testPayloads = arrayListOf(
            Pair(zSerialize("Test 1").getOrThrow(), Encoding.TEXT_PLAIN),
            Pair(zSerialize("Test 2").getOrThrow(), Encoding.TEXT_JSON),
            Pair(zSerialize("Test 3").getOrThrow(), Encoding.TEXT_CSV),
        )

        testPayloads.forEach() { value -> publisher.put(value.first, encoding = value.second) }

        assertEquals(receivedSamples.size, testPayloads.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.payload, testPayloads[index].first)
            assertEquals(sample.encoding, testPayloads[index].second)
        }
    }

    @Test
    fun deleteTest() {
        publisher.delete()
        assertEquals(1, receivedSamples.size)
        assertEquals(SampleKind.DELETE, receivedSamples[0].kind)
    }

    @Test
    fun `when encoding is not provided a put should fallback to the publisher encoding`() {
        publisher.put(zSerialize("Test").getOrThrow())
        assertEquals(1, receivedSamples.size)
        assertEquals(Encoding.ZENOH_STRING, receivedSamples[0].encoding)
    }
}
