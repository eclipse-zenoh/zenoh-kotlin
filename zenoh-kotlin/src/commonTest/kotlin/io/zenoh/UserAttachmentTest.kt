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
import io.zenoh.bytes.ZBytes
import io.zenoh.bytes.into
import io.zenoh.config.Config
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import io.zenoh.selector.Selector
import java.time.Duration
import kotlin.test.*

class UserAttachmentTest {

    private lateinit var session: Session
    private lateinit var keyExpr: KeyExpr

    companion object {
        val payload = "test".into()
        const val keyExprString = "example/testing/attachment"
        const val attachment = "mock_attachment"
        val attachmentZBytes = ZBytes.from(attachment)
    }

    @BeforeTest
    fun setup() {
        session = Session.open(Config.default()).getOrThrow()
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
        val subscriber =
            session.declareSubscriber(keyExpr, callback = { sample -> receivedSample = sample }).getOrThrow()
        session.put(keyExpr, payload, attachment = attachmentZBytes)

        subscriber.close()

        assertNotNull(receivedSample) {
            val receivedAttachment = it.attachment!!
            assertEquals(attachment, receivedAttachment.toString())
        }
    }

    @Test
    fun publisherPutWithAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr, callback = { sample ->
            receivedSample = sample
        }).getOrThrow()

        publisher.put("test", attachment = attachmentZBytes)

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            val receivedAttachment = it.attachment!!
            assertEquals(attachment, receivedAttachment.deserialize<String>().getOrNull())
        }
    }

    @Test
    fun publisherPutWithoutAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).getOrThrow()
        val subscriber =
            session.declareSubscriber(keyExpr, callback = { sample -> receivedSample = sample }).getOrThrow()

        publisher.put("test")

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertNull(it.attachment)
        }
    }

    @Test
    fun publisherDeleteWithAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).getOrThrow()
        val subscriber =
            session.declareSubscriber(keyExpr, callback = { sample -> receivedSample = sample }).getOrThrow()

        publisher.delete(attachment = attachmentZBytes)

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            val receivedAttachment = it.attachment!!
            assertEquals(attachment, receivedAttachment.toString())
        }
    }

    @Test
    fun publisherDeleteWithoutAttachmentTest() {
        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).getOrThrow()
        val subscriber =
            session.declareSubscriber(keyExpr, callback = { sample -> receivedSample = sample }).getOrThrow()

        publisher.delete()

        publisher.close()
        subscriber.close()

        assertNotNull(receivedSample) {
            assertNull(it.attachment)
        }
    }

    @Test
    fun queryWithAttachmentTest() {
        var receivedAttachment: ZBytes? = null
        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            receivedAttachment = query.attachment
            query.reply(keyExpr, payload)
        }).getOrThrow()

        session.get(
            Selector(keyExpr),
            callback = {},
            attachment = attachmentZBytes,
            timeout = Duration.ofMillis(1000)
        ).getOrThrow()

        queryable.close()

        assertNotNull(receivedAttachment) {
            assertEquals(attachmentZBytes, it)
        }
    }

    @Test
    fun queryReplyWithAttachmentTest() {
        var reply: Reply? = null
        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            query.reply(keyExpr, payload, attachment = attachmentZBytes)
        }).getOrThrow()

        session.get(Selector(keyExpr), callback = {
            reply = it
        }, timeout = Duration.ofMillis(1000)).getOrThrow()

        queryable.close()

        assertNotNull(reply)
        val receivedAttachment = reply!!.result.getOrThrow().attachment!!
        assertEquals(attachment, receivedAttachment.toString())
    }

    @Test
    fun queryReplyWithoutAttachmentTest() {
        var reply: Reply? = null
        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            query.reply(keyExpr, payload)
        }).getOrThrow()

        session.get(Selector(keyExpr), callback = {
            reply = it
        }, timeout = Duration.ofMillis(1000)).getOrThrow()

        queryable.close()

        assertNotNull(reply)
        assertTrue(reply!!.result.isSuccess)
        assertNull(reply!!.result.getOrThrow().attachment)
    }
}
