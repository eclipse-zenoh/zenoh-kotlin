package io.zenoh

import io.zenoh.query.Parameters
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.*

class ParametersTest {

    @Test
    fun `should create empty Parameters from empty string`() {
        val result = Parameters.from("")
        val emptyParams = result.getOrThrow()

        assertTrue(result.isSuccess)
        assertTrue(emptyParams.isEmpty())
    }

    @Test
    fun `should parse Parameters from formatted string`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertEquals("1", parameters.get("a"))
        assertEquals("2", parameters.get("b"))
        assertEquals("3|4|5", parameters.get("c"))
        assertEquals("6", parameters.get("d"))
    }

    @Test
    fun `should return list of values split by separator`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertEquals(listOf("3", "4", "5"), parameters.values("c"))
    }

    @Test
    fun `contains key test`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertTrue { parameters.containsKey("a") }
        assertFalse { parameters.containsKey("e") }
    }

    @Test
    fun `get test`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertEquals("1", parameters.get("a"))
        assertEquals("2", parameters.get("b"))
        assertEquals("3|4|5", parameters.get("c"))
        assertEquals("6", parameters.get("d"))
    }

    @Test
    fun `getOrDefault test`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertEquals("1", parameters.get("a"))
        assertEquals("None", parameters.getOrDefault("e", "None"))
    }

    @Test
    fun `toMap test`() {
        val parameters =  Parameters.from("a=1;b=2;c=3|4|5;d=6").getOrThrow()

        assertEquals(mapOf("a" to "1", "b" to "2", "c" to "3|4|5", "d" to "6"), parameters.toMap())
    }

    @Test
    fun `insert should return previously contained value`() {
        val parameters = Parameters.from("a=1").getOrThrow()
        val oldValue = parameters.insert("a", "3")
        assertEquals("1", oldValue)
    }

    @Test
    fun `insert should return null if not already present`() {
        val parameters = Parameters.empty()
        val oldValue = parameters.insert("a", "1")
        assertNull(oldValue)
    }

    @Test
    fun `remove should return old value if present`() {
        val parameters = Parameters.from("a=1").getOrThrow()
        val oldValue = parameters.remove("a")
        assertEquals("1", oldValue)
    }

    @Test
    fun `remove should return null if not already present`() {
        val parameters = Parameters.empty()
        val oldValue = parameters.remove("a")
        assertNull(oldValue)
    }

    @Test
    fun `extend test`() {
        val parameters = Parameters.from("a=1;b=2").getOrThrow()
        parameters.extend(Parameters.from("c=3;d=4").getOrThrow())

        assertEquals(Parameters.from("a=1;b=2;c=3;d=4").getOrThrow(), parameters)

        parameters.extend(mapOf("e" to "5"))
        assertEquals(Parameters.from("a=1;b=2;c=3;d=4;e=5").getOrThrow(), parameters)
    }

    @Test
    fun `extend overwrites conflicting keys test`() {
        val parameters = Parameters.from("a=1;b=2").getOrThrow()
        parameters.extend(Parameters.from("b=3;d=4").getOrThrow())

        assertEquals(Parameters.from("a=1;b=3;d=4").getOrThrow(), parameters)
    }

    @Test
    fun `to string test`() {
        val parameters = Parameters.from(mapOf("a" to "1", "b" to "2", "c" to "1|2|3", "d" to "4"))
        assertEquals("a=1;b=2;c=1|2|3;d=4", parameters.toString())
    }

    @Test
    fun `empty Parameters to string test`() {
        val parameters = Parameters.empty()
        assertEquals("", parameters.toString())
    }

}
