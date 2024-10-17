package io.zenoh

import io.zenoh.ext.zDeserialize
import io.zenoh.ext.zSerialize

fun main() {

    /***********************************************
     * Standard serialization and deserialization. *
     ***********************************************/

    /** Numeric: byte, short, int, float, double */
    val intInput = 1234
    var payload = zSerialize(intInput).getOrThrow()
    val intOutput = zDeserialize<Int>(payload).getOrThrow()
    check(intInput == intOutput)

    // Another example with float
    val floatInput = 3.1415f
    payload = zSerialize(floatInput).getOrThrow()
    val floatOutput = zDeserialize<Float>(payload).getOrThrow()
    check(floatInput == floatOutput)

    /** String serialization and deserialization. */
    val stringInput = "example"
    payload = zSerialize(stringInput).getOrThrow()
    var stringOutput = zDeserialize<String>(payload).getOrThrow()
    check(stringInput == stringOutput)

    /** ByteArray serialization and deserialization. */
    val byteArrayInput = "example".toByteArray()
    payload = zSerialize(byteArrayInput).getOrThrow()
    val byteArrayOutput = zDeserialize<ByteArray>(payload).getOrThrow()
    check(byteArrayInput.contentEquals(byteArrayOutput))

    /**
     * List serialization and deserialization.
     *
     * Supported types: String, ByteArray, Byte, Short, Int, Long, Float and Double.
     */
    val inputList = listOf("sample1", "sample2", "sample3")
    payload = zSerialize(inputList).getOrThrow()
    val outputList = zDeserialize<List<String>>(payload).getOrThrow()
    check(inputList == outputList)

    val inputListInt = listOf(1, 2, 3)
    payload = zSerialize(inputListInt).getOrThrow()
    val outputListInt = zDeserialize<List<Int>>(payload).getOrThrow()
    check(inputListInt == outputListInt)

    val inputListByteArray = inputList.map { value -> value.toByteArray() }
    payload = zSerialize(inputListByteArray).getOrThrow()
    val outputListByteArray = zDeserialize<List<ByteArray>>(payload).getOrThrow()
    check(compareByteArrayLists(inputListByteArray, outputListByteArray))

    /** Nested lists */
    val nestedList = listOf(listOf(1, 2, 3))
    payload = zSerialize(nestedList).getOrThrow()
    val outputNestedList = zDeserialize<List<List<Int>>>(payload).getOrThrow()
    check(nestedList == outputNestedList)

    /** Combined types */
    val combinedList = listOf(mapOf("a" to 1, "b" to 2))
    payload = zSerialize(combinedList).getOrThrow()
    val outputCombinedList = zDeserialize<List<Map<String, Int>>>(payload).getOrThrow()
    check(combinedList == outputCombinedList)

    /**
     * Map serialization and deserialization.
     *
     * Maps with the following Type combinations are supported: String, ByteArray, Byte, Short, Int, Long, Float and Double.
     */
    val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
    payload = zSerialize(inputMap).getOrThrow()
    val outputMap = zDeserialize<Map<String, String>>(payload).getOrThrow()
    check(inputMap == outputMap)

    val combinedInputMap = mapOf("key1" to 1, "key2" to 2)
    payload = zSerialize(combinedInputMap).getOrThrow()
    val combinedOutputMap = zDeserialize<Map<String, Int>>(payload).getOrThrow()
    check(combinedInputMap == combinedOutputMap)
}

/** Utils for this example. */

private fun compareByteArrayLists(list1: List<ByteArray>, list2: List<ByteArray>): Boolean {
    if (list1.size != list2.size) {
        return false
    }

    for (i in list1.indices) {
        if (!list1[i].contentEquals(list2[i])) {
            return false
        }
    }

    return true
}
