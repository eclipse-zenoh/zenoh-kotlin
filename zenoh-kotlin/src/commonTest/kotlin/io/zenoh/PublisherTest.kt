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
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @Test
    fun putTest() {
        val session = Session.open().getOrThrow()

        val receivedSamples = ArrayList<Sample>()
        val publisher = session.declarePublisher(TEST_KEY_EXP).res().getOrThrow()
        session.declareSubscriber(TEST_KEY_EXP).with { sample ->
            receivedSamples.add(sample)
        }.res()

        val testValues = arrayListOf(
            Value("Test 1".encodeToByteArray(), Encoding(KnownEncoding.TEXT_PLAIN)),
            Value("Test 2".encodeToByteArray(), Encoding(KnownEncoding.TEXT_JSON)),
            Value("Test 3".encodeToByteArray(), Encoding(KnownEncoding.TEXT_CSV))
        )

        testValues.forEach() { value -> publisher.put(value).res() }
        session.close()

        assertEquals(receivedSamples.size, testValues.size)
        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.value, testValues[index])
        }
    }

    @Test
    fun deleteTest() {
        val session = Session.open().getOrThrow()

        val receivedSamples = ArrayList<Sample>()
        session.declareSubscriber(TEST_KEY_EXP).with { sample ->
            receivedSamples.add(sample)
        }.res()

        session.declarePublisher(TEST_KEY_EXP).res().onSuccess {
            it.use { publisher ->
                publisher.delete().res()
            }
        }
        session.close()

        assertEquals(1, receivedSamples.size)
        assertEquals(SampleKind.DELETE, receivedSamples[0].kind)
    }
}
