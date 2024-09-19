package io.zenoh

import io.zenoh.exceptions.ZError
import io.zenoh.selector.Selector
import io.zenoh.selector.intoSelector
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
}
