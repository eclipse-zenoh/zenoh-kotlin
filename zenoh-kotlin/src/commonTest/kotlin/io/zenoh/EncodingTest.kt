package io.zenoh

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlin.test.*

class EncodingTest {

    @Test
    fun encoding_subscriberTest() {
        val session = Session.open().getOrThrow()
        val keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()

        // Testing non null schema
        var receivedSample: Sample? = null
        val subscriber = session.declareSubscriber(keyExpr).with { sample ->
            receivedSample = sample
        }.res().getOrThrow()
        var value = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))
        session.put(keyExpr, value).res()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertEquals("test_schema", receivedSample!!.value.encoding.schema)

        // Testing null schema
        receivedSample = null
        value = Value("test2", Encoding(Encoding.ID.ZENOH_STRING, null))
        session.put(keyExpr, value).res()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.ZENOH_STRING, receivedSample!!.value.encoding.id)
        assertNull(receivedSample!!.value.encoding.schema)

        subscriber.close()
        session.close()
    }

    @Test
    fun encoding_replySuccessTest() {
        val session = Session.open().getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()
        val test1 = "example/testing/reply_success".intoKeyExpr().getOrThrow()
        val test2 = "example/testing/reply_success_with_schema".intoKeyExpr().getOrThrow()

        val testValueA = Value("test", Encoding(Encoding.ID.TEXT_CSV, null))
        val testValueB = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))

        val queryable = session.declareQueryable(keyExpr).with { query ->
            when (query.keyExpr) {
                test1 -> query.reply(query.keyExpr).success(testValueA).res()
                test2 -> query.reply(query.keyExpr).success(testValueB).res()
            }
        }.res().getOrThrow()

        // Testing with null schema on a reply success scenario.
        var receivedSample: Sample? = null
        session.get(test1).with { reply ->
            assertTrue(reply is Reply.Success)
            receivedSample = reply.sample
        }.res().getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertNull(receivedSample!!.value.encoding.schema)

        // Testing with non-null schema on a reply success scenario.
        receivedSample = null
        session.get(test2).with { reply ->
            assertTrue(reply is Reply.Success)
            receivedSample = reply.sample
        }.res().getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ID.TEXT_CSV, receivedSample!!.value.encoding.id)
        assertEquals("test_schema", receivedSample!!.value.encoding.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_replyErrorTest() {
        val session = Session.open().getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()

        val test1 = "example/testing/reply_error".intoKeyExpr().getOrThrow()
        val test2 = "example/testing/reply_error_with_schema".intoKeyExpr().getOrThrow()

        val testValueA = Value("test", Encoding(Encoding.ID.TEXT_CSV, null))
        val testValueB = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))

        val queryable = session.declareQueryable(keyExpr).with { query ->
            when (query.keyExpr) {
                test1 -> query.reply(query.keyExpr).error(testValueA).res()
                test2 -> query.reply(query.keyExpr).error(testValueB).res()
            }
        }.res().getOrThrow()

        // Testing with null schema on a reply error scenario.
        var errorValue: Value? = null
        session.get(test1).with { reply ->
            assertTrue(reply is Reply.Error)
            errorValue = reply.error
        }.res().getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorValue)
        assertEquals(Encoding.ID.TEXT_CSV, errorValue!!.encoding.id)
        assertNull(errorValue!!.encoding.schema)

        // Testing with non-null schema on a reply error scenario.
        errorValue = null
        session.get(test2).with { reply ->
            assertTrue(reply is Reply.Error)
            errorValue = reply.error
        }.res().getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorValue)
        assertEquals(Encoding.ID.TEXT_CSV, errorValue!!.encoding.id)
        assertEquals("test_schema", errorValue!!.encoding.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_queryTest() {
        val session = Session.open().getOrThrow()
        val keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
        val testValueA = Value("test", Encoding(Encoding.ID.TEXT_CSV, null))
        val testValueB = Value("test", Encoding(Encoding.ID.TEXT_CSV, "test_schema"))

        var receivedValue: Value? = null
        val queryable = session.declareQueryable(keyExpr).with { query ->
            receivedValue = query.value
            query.close()
        }.res().getOrThrow()

        // Testing with null schema
        session.get(keyExpr).withValue(testValueA).res()
        Thread.sleep(200)

        assertNotNull(receivedValue)
        assertEquals(Encoding.ID.TEXT_CSV, receivedValue!!.encoding.id)
        assertNull(receivedValue!!.encoding.schema)

        // Testing non-null schema
        receivedValue = null
        session.get(keyExpr).withValue(testValueB).res()
        Thread.sleep(200)

        assertNotNull(receivedValue)
        assertEquals(Encoding.ID.TEXT_CSV, receivedValue!!.encoding.id)
        assertEquals("test_schema", receivedValue!!.encoding.schema)

        queryable.close()
        session.close()
    }
}
