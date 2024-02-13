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

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.KnownEncoding
import io.zenoh.query.Reply
import io.zenoh.sample.Attachment
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import java.time.Duration
import kotlin.test.*

class AttachmentTest {

    companion object {
        const val TEST_KEY_EXP = "example/testing/keyexpr"
        val value = Value("test", Encoding(KnownEncoding.TEXT_PLAIN))
        val attachmentPairs = arrayListOf("key1" to "value1".encodeToByteArray(), "key2" to "value2".encodeToByteArray())
        val attachment = Attachment(attachmentPairs)
        val keyExpr = TEST_KEY_EXP.intoKeyExpr().getOrThrow()
    }

    @Test
    fun putWithAttachmentTest() {
        var receivedSample: Sample? = null
        val session = Session.open().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.res()
        session.put(keyExpr, value).withAttachment(attachment).res()
        session.close()

        assertNotNull(receivedSample)
        assertEquals(value, receivedSample!!.value)
        assertNotNull(receivedSample!!.attachment)
        val receivedPairs = receivedSample!!.attachment!!.values
        assertEquals(attachmentPairs.size, receivedPairs.size)
        for ((index, receivedPair) in receivedPairs.withIndex()) {
            assertEquals(attachmentPairs[index].first, receivedPair.first)
            assertEquals(attachmentPairs[index].second.decodeToString(), receivedPair.second.decodeToString())
        }
    }

    @Test
    fun replyWithAttachmentTest() {
        var reply: Reply? = null
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("message").withAttachment(attachment).res()
        }.res().getOrThrow()

        val sessionB = Session.open().getOrThrow()
        sessionB.get(QueryableTest.TEST_KEY_EXP).with { reply = it }.timeout(Duration.ofMillis(1000)).res()
        Thread.sleep(1000)

        queryable.close()
        session.close()
        sessionB.close()

        assertNotNull(reply)
        assertTrue(reply is Reply.Success)
        val receivedPairs = (reply as Reply.Success).sample.attachment!!.values
        assertEquals(attachmentPairs.size, receivedPairs.size)
        for ((index, receivedPair) in receivedPairs.withIndex()) {
            assertEquals(attachmentPairs[index].first, receivedPair.first)
            assertEquals(attachmentPairs[index].second.decodeToString(), receivedPair.second.decodeToString())
        }
    }

    @Test
    fun replyWithoutAttachmentTest() {
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("message").res()
        }.res().getOrThrow()

        val sessionB = Session.open().getOrThrow()
        sessionB.get(QueryableTest.TEST_KEY_EXP).with { reply: Reply ->
            assertTrue(reply is Reply.Success)
            assertNull(reply.sample.attachment)
        }.timeout(Duration.ofMillis(1000)).res()

        queryable.close()
        session.close()
        sessionB.close()
    }
}
