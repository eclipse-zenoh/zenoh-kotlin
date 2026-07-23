//
// Copyright (c) 2025 ZettaScale Technology
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
import io.zenoh.pubsub.AdvancedPublisher
import io.zenoh.pubsub.MatchingListener
import io.zenoh.sample.Sample
import io.zenoh.pubsub.Subscriber
import java.lang.Thread.sleep
import kotlin.test.*

/**
 * Round 1 of advanced pub/sub: [AdvancedPublisher] + [MatchingListener]
 * validated against a regular [Subscriber] on a single session (loopback).
 * The full [AdvancedPubSubTest] (advanced subscriber, sample-miss detection,
 * detect-publishers) remains `@Ignore`'d until Round 2.
 */
class AdvancedPublisherTest {

    lateinit var session: Session
    lateinit var receivedSamples: ArrayList<Sample>
    lateinit var publisher: AdvancedPublisher
    lateinit var matchingListener: MatchingListener
    lateinit var subscriber: Subscriber<Unit>
    lateinit var keyExpr: KeyExpr
    var hasMatchingSubscribers: Boolean = false

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        receivedSamples = ArrayList()
        // Declare the subscriber first, then the advanced publisher and its
        // matching listener — so the matching status is already `true` when the
        // listener is declared (no reliance on change-notification timing).
        subscriber = session.declareSubscriber(keyExpr, callback = { sample ->
            receivedSamples.add(sample)
        }).getOrThrow()
        publisher = session.declareAdvancedPublisher(keyExpr, encoding = Encoding.ZENOH_STRING).getOrThrow()
        matchingListener = publisher.declareMatchingListener(callback = { matching ->
            hasMatchingSubscribers = matching
        }).getOrThrow()
        // Give matching-status propagation a brief moment.
        sleep(100)
    }

    @AfterTest
    fun tearDown() {
        matchingListener.close()
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

        testPayloads.forEach { value -> publisher.put(value.first, encoding = value.second) }

        assertEquals(testPayloads.size, receivedSamples.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(testPayloads[index].first, sample.payload)
            assertEquals(testPayloads[index].second, sample.encoding)
        }
    }

    @Test
    fun deleteTest() {
        publisher.delete()
        assertEquals(1, receivedSamples.size)
        assertEquals(SampleKind.DELETE, receivedSamples[0].kind)
    }

    @Test
    fun matchingStatusTest() {
        // A matching subscriber exists, so both the synchronous query and the
        // listener callback must report matching.
        assertTrue(publisher.getMatchingStatus().getOrThrow())
        assertTrue(hasMatchingSubscribers)
    }

    @Test
    fun `when encoding is not provided a put should fallback to the publisher encoding`() {
        publisher.put(zSerialize("Test").getOrThrow())
        assertEquals(1, receivedSamples.size)
        assertEquals(Encoding.ZENOH_STRING, receivedSamples[0].encoding)
    }
}
