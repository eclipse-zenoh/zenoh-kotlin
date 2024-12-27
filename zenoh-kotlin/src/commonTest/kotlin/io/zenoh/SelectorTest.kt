package io.zenoh

import io.zenoh.exceptions.ZError
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.query.Selector
import io.zenoh.query.intoSelector
import org.junit.jupiter.api.Assertions.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectorTest {

    @Test
    fun `selector from string test`() {
        "a/b/c?arg1=val1".intoSelector().getOrThrow().use { selector: Selector ->
            assertEquals("a/b/c", selector.keyExpr.toString())
            assertEquals("arg1=val1", selector.parameters.toString())
        }

        "a/b/c".intoSelector().getOrThrow().use { selector: Selector ->
            assertEquals("a/b/c", selector.keyExpr.toString())
            assertNull(selector.parameters)
        }

        assertFailsWith<ZError> { "".intoSelector().getOrThrow() }
    }

    @Test
    fun `selector to string test`() {
        "a/b/c?arg1=val1".intoSelector().getOrThrow().use { selector: Selector ->
            assertEquals("a/b/c?arg1=val1", selector.toString())
        }

        "a/b/c".intoSelector().getOrThrow().use { selector: Selector ->
            assertEquals("a/b/c", selector.toString())
        }
    }

    /**
     * Check the queryable properly receives the query's selector with and without parameters.
     */
    @Test
    fun `selector query test`() {
        val session = Zenoh.open(Config.default()).getOrThrow()
        val queryableKeyExpr = "a/b/**".intoKeyExpr().getOrThrow()

        var receivedQuerySelector: Selector? = null
        val queryable = session.declareQueryable(queryableKeyExpr, callback = { query ->
            receivedQuerySelector = query.selector
            query.close()
        }
        ).getOrThrow()

        val querySelector = "a/b/c".intoSelector().getOrThrow()
        session.get(querySelector, callback = {}).getOrThrow()
        Thread.sleep(1000)
        assertEquals(querySelector, receivedQuerySelector)


        val querySelector2 = "a/b/c?key=value".intoSelector().getOrThrow()
        session.get(querySelector2, callback = {}).getOrThrow()
        Thread.sleep(1000)
        assertEquals(querySelector2, receivedQuerySelector)

        queryable.close()
        session.close()
    }
}
