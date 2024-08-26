package io.zenoh

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import io.zenoh.selector.intoSelector
import io.zenoh.value.Value
import kotlin.test.*

class EncodingTest {

    @Test
    fun encoding_subscriberTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()

        // Testing non null schema
        var receivedSample: Sample? = null
        val subscriber = session.declareSubscriber(keyExpr, callback = { sample ->
            receivedSample = sample
        }).getOrThrow()
        var value = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))
        session.put(keyExpr, value)
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertEquals("test_schema", receivedSample!!.value.encoding.schema)

        // Testing null schema
        receivedSample = null
        value = Value("test2", Encoding(Encoding.ID.ZENOH_STRING, null))
        session.put(keyExpr, value)
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.ZENOH_STRING, receivedSample!!.value.encoding.id)
        assertNull(receivedSample!!.value.encoding.schema)

        subscriber.close()
        session.close()
    }

    @Test
    fun encoding_replySuccessTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()
        val test1 = "example/testing/reply_success".intoSelector().getOrThrow()
        val test2 = "example/testing/reply_success_with_schema".intoSelector().getOrThrow()

        val testValueA = Value("test", Encoding(Encoding.ID.TEXT_CSV, null))
        val testValueB = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))

        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            when (query.keyExpr) {
                test1.keyExpr -> query.replySuccess(query.keyExpr, value = testValueA)
                test2.keyExpr -> query.replySuccess(query.keyExpr, value = testValueB)
            }
        }).getOrThrow()

        // Testing with null schema on a reply success scenario.
        var receivedSample: Sample? = null
        session.get(test1, callback = { reply ->
            assertTrue(reply is Reply.Success)
            receivedSample = reply.sample
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertNull(receivedSample!!.value.encoding.schema)

        // Testing with non-null schema on a reply success scenario.
        receivedSample = null
        session.get(test2, callback = { reply ->
            assertTrue(reply is Reply.Success)
            receivedSample = reply.sample
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertEquals("test_schema", receivedSample!!.value.encoding.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_replyErrorTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()

        val test1 = "example/testing/reply_error".intoSelector().getOrThrow()
        val test2 = "example/testing/reply_error_with_schema".intoSelector().getOrThrow()

        val testValueA = Value("test", Encoding(Encoding.ID.TEXT_CSV, null))
        val testValueB = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))

        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            when (query.keyExpr) {
                test1.keyExpr -> query.replyError(testValueA)
                test2.keyExpr -> query.replyError(testValueB)
            }
        }).getOrThrow()

        // Testing with null schema on a reply error scenario.
        var errorValue: Value? = null
        session.get(test1, callback = { reply ->
            assertTrue(reply is Reply.Error)
            errorValue = reply.error
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorValue)
        assertEquals(Encoding.ID.TEXT_CSV, errorValue!!.encoding.id)
        assertNull(errorValue!!.encoding.schema)

        // Testing with non-null schema on a reply error scenario.
        errorValue = null
        session.get(test2, callback = { reply ->
            assertTrue(reply is Reply.Error)
            errorValue = reply.error
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorValue)
        assertEquals(Encoding.ID.TEXT_CSV, errorValue!!.encoding.id)
        assertEquals("test_schema", errorValue!!.encoding.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_queryTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val selector = "example/testing/keyexpr".intoSelector().getOrThrow()
        val encodingA = Encoding(Encoding.ID.TEXT_CSV, null)
        val encodingB = Encoding(Encoding.ID.TEXT_CSV, "test_schema")

        var receivedEncoding: Encoding? = null
        val queryable = session.declareQueryable(selector.keyExpr, callback = { query ->
            receivedEncoding = query.encoding
            query.close()
        }).getOrThrow()

        // Testing with null schema
        session.get(selector, callback = {}, value = Value("test", encodingA))
        Thread.sleep(200)

        assertNotNull(receivedEncoding)
        assertEquals(Encoding.ID.TEXT_CSV, receivedEncoding!!.id)
        assertNull(receivedEncoding!!.schema)

        // Testing non-null schema
        receivedEncoding = null
        session.get(selector, callback = {}, value = Value("test", encodingB))
        Thread.sleep(200)

        assertNotNull(receivedEncoding)
        assertEquals(Encoding.ID.TEXT_CSV, receivedEncoding!!.id)
        assertEquals("test_schema", receivedEncoding!!.schema)

        queryable.close()
        session.close()
    }
}
