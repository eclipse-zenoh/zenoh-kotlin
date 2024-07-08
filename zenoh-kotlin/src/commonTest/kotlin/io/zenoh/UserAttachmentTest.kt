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
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import java.time.Duration
import kotlin.test.*

class UserAttachmentTest {

    private lateinit var session: Session
    private lateinit var keyExpr: KeyExpr

    companion object {
        val value = Value("test", Encoding(Encoding.ID.TEXT_PLAIN))
        const val keyExprString = "example/testing/attachment"
        const val attachment = "mock_attachment"
        val attachmentBytes = attachment.toByteArray()
    }

    @BeforeTest
    fun setup() {
        session = Session.open().getOrThrow()
        keyExpr = keyExprString.intoKeyExpr().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        keyExpr.close()
    }

    @Test
    fun putWithAttachmentTest() {
        var receivedSample: Sample? = null
        val subscriber = session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.wait().getOrThrow()
        session.put(keyExpr, value).withAttachment(attachmentBytes).wait()

        subscriber.close()

        assertNotNull(receivedSample) {
            assertEquals(attachment, it.attachment!!.decodeToString())
        }
    }

    @Test
    fun publisherPutWithAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).wait().getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.wait().getOrThrow()

        publisher.put("test").withAttachment(attachmentBytes).wait()

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertEquals(attachment, it.attachment!!.decodeToString())
        }
    }

    @Test
    fun publisherPutWithoutAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).wait().getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.wait().getOrThrow()

        publisher.put("test").wait()

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertNull(it.attachment)
        }
    }

    @Test
    fun publisherDeleteWithAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).wait().getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.wait().getOrThrow()

        publisher.delete().withAttachment(attachmentBytes).wait()

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertEquals(attachment, it.attachment!!.decodeToString())
        }
    }

    @Test
    fun publisherDeleteWithoutAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).wait().getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr).with { sample -> receivedSample = sample }.wait().getOrThrow()

        publisher.delete().wait()

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertNull(it.attachment)
        }
    }

    @Test
    fun queryWithAttachmentTest() {
        var receivedAttachment: ByteArray? = null
        val queryable = session.declareQueryable(keyExpr).with { query ->
            receivedAttachment = query.attachment
            query.reply(keyExpr).success("test").wait()
        }.wait().getOrThrow()

        session.get(keyExpr).with {}.withAttachment(attachmentBytes).timeout(Duration.ofMillis(1000)).wait().getOrThrow()

        queryable.close()

        assertNotNull(receivedAttachment) {
            assertEquals(attachment, it.decodeToString())
        }
    }

    @Test
    fun queryReplyWithAttachmentTest() {
        var reply: Reply? = null
        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("test").attachment(attachmentBytes).wait()
        }.wait().getOrThrow()

        session.get(keyExpr).with {
            if (it is Reply.Success) {
                reply = it
            }
        }.timeout(Duration.ofMillis(1000)).wait().getOrThrow()

        queryable.close()

        assertNotNull(reply) {
            assertEquals(attachment, (it as Reply.Success).sample.attachment!!.decodeToString())
        }
    }

    @Test
    fun queryReplyWithoutAttachmentTest() {
        var reply: Reply? = null
        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("test").wait()
        }.wait().getOrThrow()

        session.get(keyExpr).with {
            reply = it
        }.timeout(Duration.ofMillis(1000)).wait().getOrThrow()

        queryable.close()

        assertNotNull(reply) {
            assertTrue(it is Reply.Success)
            assertNull(it.sample.attachment)
        }
    }
}
