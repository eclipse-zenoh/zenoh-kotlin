package io.zenoh

import io.zenoh.bytes.*
import io.zenoh.ext.zDeserialize
import io.zenoh.ext.zSerialize

fun main() {

    /***********************************************
     * Standard serialization and deserialization. *
     ***********************************************/

    /** Numeric: byte, short, int, float, double */
    val intInput = 1234
    var payload = ZBytes.from(intInput)
    var intOutput = zDeserialize<Int>(payload).getOrThrow()
    check(intInput == intOutput)

    // Alternatively you can serialize into the type.
    payload = zSerialize(intInput).getOrThrow()
    intOutput = zDeserialize<Int>(payload).getOrThrow()
    check(intInput == intOutput)

    // Alternatively, `Numeric.into()`: ZBytes can be used
    payload = intInput.into()
    intOutput = zDeserialize<Int>(payload).getOrThrow()
    check(intInput == intOutput)

    // Another example with float
    val floatInput = 3.1415f
    payload = ZBytes.from(floatInput)
    val floatOutput = zDeserialize<Float>(payload).getOrThrow()
    check(floatInput == floatOutput)

    /** String serialization and deserialization. */
    val stringInput = "example"
    payload = ZBytes.from(stringInput)
    // Alternatively, you can also call `String.into()` to convert
    // a string into a ZBytes object:
    // payload = stringInput.into()
    var stringOutput = zDeserialize<String>(payload).getOrThrow()
    check(stringInput == stringOutput)

    // For the case of strings, ZBytes::toString() is equivalent:
    stringOutput = payload.toString()
    check(stringInput == stringOutput)

    /** ByteArray serialization and deserialization. */
    val byteArrayInput = "example".toByteArray()
    payload = ZBytes.from(byteArrayInput) // Equivalent to `byteArrayInput.into()`
    var byteArrayOutput = zDeserialize<ByteArray>(payload).getOrThrow()
    check(byteArrayInput.contentEquals(byteArrayOutput))
    // Alternatively, we can directly access the bytes of property of ZBytes:
    byteArrayOutput = payload.toByteArray()
    check(byteArrayInput.contentEquals(byteArrayOutput))

    /** List serialization and deserialization.
     *
     * Supported types: String, ByteArray, ZBytes, Byte, Short, Int, Long, Float and Double.
     */
    val inputList = listOf("sample1", "sample2", "sample3")
    payload = zSerialize(inputList).getOrThrow()
    val outputList = zDeserialize<List<String>>(payload).getOrThrow()
    check(inputList == outputList)

    val inputListZBytes = inputList.map { value -> value.into() }
    payload = zSerialize(inputListZBytes).getOrThrow()
    val outputListZBytes = zDeserialize<List<ZBytes>>(payload).getOrThrow()
    check(inputListZBytes == outputListZBytes)

    val inputListByteArray = inputList.map { value -> value.toByteArray() }
    payload = zSerialize(inputListByteArray).getOrThrow()
    val outputListByteArray = zDeserialize<List<ByteArray>>(payload).getOrThrow()
    check(compareByteArrayLists(inputListByteArray, outputListByteArray))

    /**
     * Map serialization and deserialization.
     *
     * Maps with the following Type combinations are supported: String, ByteArray, ZBytes, Byte, Short, Int, Long, Float and Double.
     */
    val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
    payload = zSerialize(inputMap).getOrThrow()
    val outputMap = zDeserialize<Map<String, String>>(payload).getOrThrow()
    check(inputMap == outputMap)

    val combinedInputMap = mapOf("key1" to ZBytes.from("zbytes1"), "key2" to ZBytes.from("zbytes2"))
    payload = zSerialize(combinedInputMap).getOrThrow()
    val combinedOutputMap = zDeserialize<Map<String, ZBytes>>(payload).getOrThrow()
    check(combinedInputMap == combinedOutputMap)

    /*********************************************
     * Custom serialization and deserialization. *
     *********************************************/

    /**
     * The examples below use [MyZBytes], an example class that implements the [IntoZBytes] interface.
     *
     * In order for the serialization to be successful on a custom class,
     * the class itself must override the `into(): ZBytes` function.
     *
     * Regarding deserialization for custom objects, for the time being (this API will be expanded to
     * provide further utilities) you need to manually convert the ZBytes into the type you want.
     *
     * @see MyZBytes
     */
    val inputMyZBytes = MyZBytes("example")
    payload = zSerialize(inputMyZBytes).getOrThrow()
    val outputMyZBytes = MyZBytes.from(payload)
    check(inputMyZBytes == outputMyZBytes)

    /** List of MyZBytes. */
    val inputListMyZBytes = inputList.map { value -> MyZBytes(value) }
    payload = zSerialize<List<MyZBytes>>(inputListMyZBytes).getOrThrow()
    val outputListMyZBytes = zDeserialize<List<ZBytes>>(payload).getOrThrow().map { zbytes -> MyZBytes.from(zbytes) }
    check(inputListMyZBytes == outputListMyZBytes)

    /** Map of MyZBytes. */
    val inputMapMyZBytes = inputMap.map { (k, v) -> MyZBytes(k) to MyZBytes(v) }.toMap()
    payload = zSerialize<Map<MyZBytes, MyZBytes>>(inputMapMyZBytes).getOrThrow()
    val outputMapMyZBytes = zDeserialize<Map<ZBytes, ZBytes>>(payload).getOrThrow()
        .map { (key, value) -> MyZBytes.from(key) to MyZBytes.from(value) }.toMap()
    check(inputMapMyZBytes == outputMapMyZBytes)

}

data class MyZBytes(val content: String) : IntoZBytes {

    override fun into(): ZBytes = content.into()

    companion object {
        fun from(zbytes: ZBytes): MyZBytes {
            return MyZBytes(zbytes.toString())
        }
    }
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
