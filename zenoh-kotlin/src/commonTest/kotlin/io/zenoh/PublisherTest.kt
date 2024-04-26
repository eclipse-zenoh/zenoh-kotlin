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
import io.zenoh.prelude.KnownEncoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.SampleKind
import io.zenoh.publication.Publisher
import io.zenoh.sample.Sample
import io.zenoh.subscriber.Subscriber
import io.zenoh.value.Value
import kotlin.test.*

class PublisherTest {

    lateinit var session: Session
    lateinit var receivedSamples: ArrayList<Sample>
    lateinit var publisher: Publisher
    lateinit var subscriber: Subscriber<Unit>
    lateinit var keyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open().getOrThrow()
        keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        publisher = session.declarePublisher(keyExpr).res().getOrThrow()
        subscriber = session.declareSubscriber(keyExpr).with { sample ->
            receivedSamples.add(sample)
        }.res().getOrThrow()
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

        val testValues = arrayListOf(
            Value("Test 1".encodeToByteArray(), Encoding(KnownEncoding.TEXT_PLAIN)),
            Value("Test 2".encodeToByteArray(), Encoding(KnownEncoding.TEXT_JSON)),
            Value("Test 3".encodeToByteArray(), Encoding(KnownEncoding.TEXT_CSV))
        )

        testValues.forEach() { value -> publisher.put(value).res() }

        assertEquals(receivedSamples.size, testValues.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.value, testValues[index])
        }
    }

    @Test
    fun deleteTest() {
        publisher.delete().res()
        assertEquals(1, receivedSamples.size)
        assertEquals(SampleKind.DELETE, receivedSamples[0].kind)
    }
}
