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

import io.zenoh.prelude.KnownEncoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.SampleKind
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlin.test.Test
import kotlin.test.assertEquals

class PublisherTest {

    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr()
    }

    @Test
    fun putTest() {
        val session = Session.open()

        val receivedSamples = ArrayList<Sample>()
        val publisher = session.declarePublisher(TEST_KEY_EXP).res()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with { sample ->
            receivedSamples.add(sample)
        }.res()

        val testValues = arrayListOf(
            Value("Test 1".encodeToByteArray(), Encoding(KnownEncoding.TEXT_PLAIN)),
            Value("Test 2".encodeToByteArray(), Encoding(KnownEncoding.TEXT_JSON)),
            Value("Test 3".encodeToByteArray(), Encoding(KnownEncoding.TEXT_CSV))
        )

        testValues.forEach() { value -> publisher.put(value).res() }
        subscriber.undeclare()
        publisher.undeclare()
        session.close()

        assertEquals(receivedSamples.size, testValues.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.value, testValues[index])
        }
    }

    @Test
    fun writeTest() {
        val session = Session.open()
        val receivedSamples = ArrayList<Sample>()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with { sample ->
            receivedSamples.add(sample)
        }.res()

        val testSamples = arrayListOf(
            Sample(TEST_KEY_EXP, Value("Test PUT"), SampleKind.PUT, null),
            Sample(TEST_KEY_EXP, Value("Test DELETE"), SampleKind.DELETE, null),
        )

        val publisher = session.declarePublisher(TEST_KEY_EXP).res()
            publisher.write(testSamples[0].kind, testSamples[0].value).res()
            publisher.write(testSamples[1].kind, testSamples[1].value).res()

        subscriber.undeclare()
        publisher.undeclare()
        session.close()
        assertEquals(testSamples.size, receivedSamples.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample, testSamples[index])
        }
    }

    @Test
    fun deleteTest() {
        val session = Session.open()

        val receivedSamples = ArrayList<Sample>()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with { sample ->
            receivedSamples.add(sample)
        }.res()

        val publisher = session.declarePublisher(TEST_KEY_EXP).res()
        publisher.delete().res()

        publisher.undeclare()
        subscriber.undeclare()
        session.close()

        assertEquals(1, receivedSamples.size)
        assertEquals(SampleKind.DELETE, receivedSamples[0].kind)
    }
}
