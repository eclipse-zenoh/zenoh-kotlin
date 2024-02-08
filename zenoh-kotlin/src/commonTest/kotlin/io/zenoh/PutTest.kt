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
import io.zenoh.publication.Attachment
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PutTest {

    companion object {
        const val TEST_KEY_EXP = "example/testing/keyexpr"
        const val TEST_PAYLOAD = "Hello"
    }

    @Test
    fun putTest() {
        val session = Session.open().getOrThrow()
        var receivedSample: Sample? = null
        val keyExpr = TEST_KEY_EXP.intoKeyExpr().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.res()
        val value = Value(TEST_PAYLOAD.toByteArray(), Encoding(KnownEncoding.TEXT_PLAIN))
        session.put(keyExpr, value).res()
        session.close()
        assertNotNull(receivedSample)
        assertEquals(value, receivedSample!!.value)
    }

    @Test
    fun putWithAttachmentTest() {
        val session = Session.open().getOrThrow()
        var receivedSample: Sample? = null
        val keyExpr = TEST_KEY_EXP.intoKeyExpr().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.res()
        val value = Value(TEST_PAYLOAD.toByteArray(), Encoding(KnownEncoding.TEXT_PLAIN))
        val sentPairs = arrayListOf("key1" to "value1".encodeToByteArray(), "key2" to "value2".encodeToByteArray())
        val attachment = Attachment(sentPairs)

        session.put(keyExpr, value).withAttachment(attachment).res()
        session.close()
        assertNotNull(receivedSample)
        assertEquals(value, receivedSample!!.value)
        assertNotNull(receivedSample!!.attachment)
        val receivedPairs = receivedSample!!.attachment!!.values
        assertEquals(sentPairs.size, receivedPairs.size)
        for ((index, receivedPair) in receivedPairs.withIndex()) {
            assertEquals(sentPairs[index].first, receivedPair.first)
            assertEquals(sentPairs[index].second.decodeToString(), receivedPair.second.decodeToString())
        }
    }
}
