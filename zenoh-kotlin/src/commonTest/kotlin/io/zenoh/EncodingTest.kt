package io.zenoh

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.ZBytes
import io.zenoh.bytes.into
import io.zenoh.query.ReplyError
import io.zenoh.sample.Sample
import io.zenoh.query.intoSelector
import kotlin.test.*

class EncodingTest {

    private val without_schema = Encoding.TEXT_CSV
    private val with_schema = Encoding.APPLICATION_JSON.withSchema("test_schema")

    @Test
    fun encoding_subscriberTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val keyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()

        // Testing non null schema
        var receivedSample: Sample? = null
        val subscriber = session.declareSubscriber(keyExpr, callback = { sample ->
            receivedSample = sample
        }).getOrThrow()
        session.put(
            keyExpr,
            payload = "test".into(),
            encoding = with_schema
        )
        Thread.sleep(200)

        assertEquals(receivedSample?.encoding, with_schema)

        // Testing null schema
        receivedSample = null
        session.put(keyExpr, payload = "test2".into(), encoding = without_schema)
        Thread.sleep(200)

        assertEquals(receivedSample?.encoding, without_schema)

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
                test1.keyExpr -> query.reply(
                    query.keyExpr,
                    payload = "test".into(),
                    encoding = without_schema
                )

                test2.keyExpr -> query.reply(
                    query.keyExpr,
                    payload = "test".into(),
                    encoding = with_schema
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

        assertEquals(receivedSample?.encoding, without_schema)

        // Testing with non-null schema on a reply success scenario.
        receivedSample = null
        session.get(test2, callback = { reply ->
            assertTrue(reply.result.isSuccess)
            receivedSample = reply.result.getOrThrow()
        }).getOrThrow()
        Thread.sleep(200)

        assertEquals(receivedSample?.encoding, with_schema)

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
                test1.keyExpr -> query.replyErr("test".into(), without_schema)
                test2.keyExpr -> query.replyErr("test".into(), with_schema)
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
        assertEquals(errorEncoding, without_schema)

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
        assertEquals(errorEncoding, with_schema)

        queryable.close()
        session.close()
    }

    @Test
    fun encoding_queryTest() {
        val session = Session.open(Config.default()).getOrThrow()
        val selector = "example/testing/keyexpr".intoSelector().getOrThrow()

        var receivedEncoding: Encoding? = null
        val queryable = session.declareQueryable(selector.keyExpr, callback = { query ->
            receivedEncoding = query.encoding
            query.close()
        }).getOrThrow()

        // Testing with null schema
        session.get(selector, callback = {}, payload = "test".into(), encoding = without_schema)
        Thread.sleep(200)

        assertEquals(receivedEncoding, without_schema)

        Thread.sleep(200)

        // Testing non-null schema
        receivedEncoding = null
        session.get(selector, callback = {}, payload = "test".into(), encoding = with_schema)
        Thread.sleep(200)

        assertEquals(receivedEncoding, with_schema)

        queryable.close()
        session.close()
    }
}
