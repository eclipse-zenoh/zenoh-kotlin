package io.zenoh

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import io.zenoh.query.ReplyError
import io.zenoh.sample.Sample
import io.zenoh.selector.intoSelector
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
        session.put(keyExpr, payload = "test".into(), encoding = Encoding.TEXT_CSV.withSchema("test_schema"))
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.TEXT_CSV.id, receivedSample!!.encoding.id)
        assertEquals("test_schema", receivedSample!!.encoding.schema)

        // Testing null schema
        receivedSample = null
        session.put(keyExpr, payload = "test2".into(), encoding = Encoding.ZENOH_STRING)
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.ZENOH_STRING.id, receivedSample!!.encoding.id)
        assertNull(receivedSample!!.encoding.schema)

        subscriber.close()
        session.close()
    }

    @Test
    fun encoding_replySuccessTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()
        val test1 = "example/testing/reply_success".intoSelector().getOrThrow()
        val test2 = "example/testing/reply_success_with_schema".intoSelector().getOrThrow()

        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            when (query.keyExpr) {
                test1.keyExpr -> query.replySuccess(
                    query.keyExpr,
                    payload = "test".into(),
                    encoding = Encoding.TEXT_CSV
                )

                test2.keyExpr -> query.replySuccess(
                    query.keyExpr,
                    payload = "test".into(),
                    encoding = Encoding.TEXT_CSV.withSchema("test_schema")
                )
            }
        }).getOrThrow()

        // Testing with null schema on a reply success scenario.
        var receivedSample: Sample? = null
        session.get(test1, callback = { reply ->
            assertTrue(reply.result.isSuccess)
            receivedSample = reply.result.getOrThrow()
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.TEXT_CSV.id, receivedSample!!.encoding.id)
        assertNull(receivedSample!!.encoding.schema)

        // Testing with non-null schema on a reply success scenario.
        receivedSample = null
        session.get(test2, callback = { reply ->
            assertTrue(reply.result.isSuccess)
            receivedSample = reply.result.getOrThrow()
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(receivedSample)
        assertEquals(Encoding.TEXT_CSV.id, receivedSample!!.encoding.id)
        assertEquals("test_schema", receivedSample!!.encoding.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_replyErrorTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/**".intoKeyExpr().getOrThrow()

        val test1 = "example/testing/reply_error".intoSelector().getOrThrow()
        val test2 = "example/testing/reply_error_with_schema".intoSelector().getOrThrow()

        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            when (query.keyExpr) {
                test1.keyExpr -> query.replyError("test".into(), Encoding.TEXT_CSV)
                test2.keyExpr -> query.replyError("test".into(), Encoding.TEXT_CSV.withSchema("test_schema"))
            }
        }).getOrThrow()

        // Testing with null schema on a reply error scenario.
        var errorMessage: ZBytes? = null
        var errorEncoding: Encoding? = null
        session.get(test1, callback = { reply ->
            assertTrue(reply.result.isFailure)
            reply.result.onFailure { error ->
                error as ReplyError
                errorMessage = error.payload
                errorEncoding = error.encoding
            }
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorMessage)
        assertEquals(Encoding.TEXT_CSV.id, errorEncoding!!.id)
        assertNull(errorEncoding!!.schema)

        Thread.sleep(200)

        // Testing with non-null schema on a reply error scenario.
        errorMessage = null
        errorEncoding = null
        session.get(test2, callback = { reply ->
            assertTrue(reply.result.isFailure)
            reply.result.onFailure { error ->
                error as ReplyError
                errorMessage = error.payload
                errorEncoding = error.encoding
            }
        }).getOrThrow()
        Thread.sleep(200)

        assertNotNull(errorMessage)
        assertEquals(Encoding.TEXT_CSV.id, errorEncoding!!.id)
        assertEquals("test_schema", errorEncoding!!.schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_queryTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val selector = "example/testing/keyexpr".intoSelector().getOrThrow()
        val encodingA = Encoding.TEXT_CSV
        val encodingB = Encoding(123, "test_schema")

        var receivedEncoding: Encoding? = null
        val queryable = session.declareQueryable(selector.keyExpr, callback = { query ->
            receivedEncoding = query.encoding
            query.close()
        }).getOrThrow()

        // Testing with null schema
        session.get(selector, callback = {}, payload = "test".into(), encoding = encodingA)
        Thread.sleep(200)

        assertNotNull(receivedEncoding)
        assertEquals(Encoding.TEXT_CSV.id, receivedEncoding!!.id)
        assertNull(receivedEncoding!!.schema)

        Thread.sleep(200)

        // Testing non-null schema
        receivedEncoding = null
        session.get(selector, callback = {}, payload = "test".into(), encoding = encodingB)
        Thread.sleep(200)

        assertNotNull(receivedEncoding)
        assertEquals(123, receivedEncoding!!.id)
        assertEquals("test_schema", receivedEncoding!!.schema)

        queryable.close()
        session.close()
    }
}
