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

import io.zenoh.jni.decodeAttachment
import io.zenoh.jni.encodeAttachment
import io.zenoh.jni.toByteArray
import io.zenoh.jni.toInt
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.KnownEncoding
import io.zenoh.prelude.SampleKind
import io.zenoh.query.Reply
import io.zenoh.sample.Attachment
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import java.time.Duration
import kotlin.test.*

class UserAttachmentTest {

    companion object {
        val value = Value("test", Encoding(KnownEncoding.TEXT_PLAIN))
        val keyExpr = "example/testing/attachment".intoKeyExpr().getOrThrow()
        val attachmentPairs = arrayListOf(
            "key1" to "value1", "key2" to "value2", "key3" to "value3", "repeatedKey" to "value1", "repeatedKey" to "value2"
        )
        val attachment =
            Attachment(attachmentPairs.map { it.first.encodeToByteArray() to it.second.encodeToByteArray() })
    }

    private fun assertAttachmentOk(attachment: Attachment?) {
        assertNotNull(attachment)
        val receivedPairs = attachment.values
        assertEquals(attachmentPairs.size, receivedPairs.size)
        for ((index, receivedPair) in receivedPairs.withIndex()) {
            assertEquals(attachmentPairs[index].first, receivedPair.first.decodeToString())
            assertEquals(attachmentPairs[index].second, receivedPair.second.decodeToString())
        }
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
        assertAttachmentOk(receivedSample!!.attachment)
    }

    @Test
    fun publisherPutWithAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).res().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.res()

        publisher.put("test").withAttachment(attachment).res()
        session.close()

        assertAttachmentOk(receivedSample!!.attachment!!)
    }

    @Test
    fun publisherPutWithoutAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).res().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.res()
        publisher.put("test").res()
        session.close()

        assertNotNull(receivedSample)
        assertNull(receivedSample!!.attachment)
    }

    @Test
    fun publisherDeleteWithAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).res().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.res()

        publisher.delete().withAttachment(attachment).res()
        session.close()

        assertAttachmentOk(receivedSample!!.attachment!!)
    }

    @Test
    fun publisherDeleteWithoutAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedSample: Sample? = null
        val publisher = session.declarePublisher(keyExpr).res().getOrThrow()
        session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.res()

        publisher.delete().res()
        session.close()

        assertNotNull(receivedSample)
        assertNull(receivedSample!!.attachment)
    }

    @Test
    fun queryWithAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedAttachment: Attachment? = null

        val queryable = session.declareQueryable(keyExpr).with { query ->
            receivedAttachment = query.attachment
            query.reply(keyExpr).success("test").res()
        }.res().getOrThrow()

        session.get(keyExpr).with {}.withAttachment(attachment).timeout(Duration.ofMillis(1000)).res()
        Thread.sleep(1000)

        queryable.close()
        session.close()
        assertAttachmentOk(receivedAttachment)
    }

    @Test
    fun queryReplyWithAttachmentTest() {
        val session = Session.open().getOrThrow()

        var receivedAttachment: Attachment? = null

        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("test").withAttachment(attachment).res()
        }.res().getOrThrow()

        session.get(keyExpr).with { reply ->
            (reply as Reply.Success)
            receivedAttachment = reply.sample.attachment
        }.timeout(Duration.ofMillis(1000)).res()

        Thread.sleep(1000)

        queryable.close()
        session.close()
        assertAttachmentOk(receivedAttachment)
    }

    @Test
    fun queryReplyWithoutAttachmentTest() {
        var reply: Reply? = null
        val session = Session.open().getOrThrow()
        val queryable = session.declareQueryable(keyExpr).with { query ->
            query.reply(keyExpr).success("test").res()
        }.res().getOrThrow()

        session.get(keyExpr).with {
            reply = it
        }.timeout(Duration.ofMillis(1000)).res()

        Thread.sleep(1000)

        queryable.close()
        session.close()

        assertNotNull(reply)
        assertTrue(reply is Reply.Success)
        assertNull((reply as Reply.Success).sample.attachment)
    }

    @Test
    fun encodeAndDecodeNumbersTest() {
        val numbers: List<Int> = arrayListOf(0, 1, -1, 12345, -12345, 123567, 123456789, -123456789)

        for (number in numbers) {
            val bytes = number.toByteArray()
            val decodedNumber: Int = bytes.toInt()
            assertEquals(number, decodedNumber)
        }
    }

    @Test
    fun encodeAndDecodeAttachmentTest() {
        val encodedAttachment = encodeAttachment(attachment)
        val decodedAttachment = decodeAttachment(encodedAttachment)

        assertAttachmentOk(decodedAttachment)
    }
}
