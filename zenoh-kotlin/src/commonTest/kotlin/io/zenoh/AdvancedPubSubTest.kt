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
import io.zenoh.ext.HeartbeatMode
import io.zenoh.ext.MissDetectionConfig
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.sample.SampleKind
import io.zenoh.ext.zSerialize
import io.zenoh.pubsub.AdvancedPublisher
import io.zenoh.sample.Sample
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.pubsub.MatchingListener
import io.zenoh.pubsub.SampleMissListener
import io.zenoh.pubsub.Subscriber
import kotlin.test.*

class AdvancedPubSubTest {

    lateinit var session: Session
    lateinit var receivedSamples: ArrayList<Sample>

    lateinit var publisher: AdvancedPublisher
    lateinit var matchingListener: MatchingListener
    var hasMatchingSubscribers: Boolean = false

    lateinit var subscriber: AdvancedSubscriber<Unit>
    lateinit var matchingSubscriber: Subscriber<Unit>
    var matchingSamples: ArrayList<Sample> = arrayListOf<Sample>()

    lateinit var sampleMissListener: SampleMissListener
    var sampleMisses: Long = 0

    lateinit var keyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()

        val missDetectionConfig = MissDetectionConfig(HeartbeatMode.PeriodicHeartbeat(100))

        publisher = session.declareAdvancedPublisher(
            keyExpr,
            encoding = Encoding.ZENOH_STRING,
            sampleMissDetection = missDetectionConfig,
            publisherDetection = true).getOrThrow()
        matchingListener = publisher.declareMatchingListener(callback = { matching -> hasMatchingSubscribers = matching  }).getOrThrow()

        subscriber = session.declareAdvancedSubscriber(
            keyExpr,
            callback = { sample -> receivedSamples.add(sample) },
            subscriberDetection = true
        ).getOrThrow()
        matchingSubscriber = subscriber.declareDetectPublishersSubscriber(callback = {sample -> matchingSamples.add(sample)}, history = true).getOrThrow()
        sampleMissListener = subscriber.declareSampleMissListener(callback = {miss -> sampleMisses++}).getOrThrow()

        receivedSamples = ArrayList()
    }

    @AfterTest
    fun tearDown() {
        assertTrue(matchingSamples.count() != 0)
        assertTrue(hasMatchingSubscribers)

        matchingListener.close()
        publisher.close()

        matchingSubscriber.close()
        sampleMissListener.close()
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
