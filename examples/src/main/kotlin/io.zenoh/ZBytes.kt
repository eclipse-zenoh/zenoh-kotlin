package io.zenoh

import io.zenoh.protocol.*

fun main() {

    /** Numeric: byte, short, int, float, double */
    val intInput = 1234
    var payload = ZBytes.from(intInput)
    var intOutput = payload.deserialize<Int>().getOrThrow()
    check(intInput == intOutput)

    // Alternatively, `Numeric.into()`: ZBytes can be used
    payload = intInput.into()
    intOutput = payload.deserialize<Int>().getOrThrow()
    check(intInput == intOutput)

    // Another example with float
    val floatInput = 3.1415f
    payload = ZBytes.from(floatInput)
    val floatOutput = payload.deserialize<Float>().getOrThrow()
    check(floatInput == floatOutput)

    /** String serialization and deserialization. */
    val stringInput = "example"
    payload = ZBytes.from(stringInput)
    // Alternatively, you can also call `String.into()` to convert
    // a string into a ZBytes object:
    // payload = stringInput.into()
    var stringOutput = payload.deserialize<String>().getOrThrow()
    check(stringInput == stringOutput)

    // For the case of strings, ZBytes::toString() is equivalent:
    stringOutput = payload.toString()
    check(stringInput == stringOutput)

    /** ByteArray serialization and deserialization. */
    val byteArrayInput = "example".toByteArray()
    payload = ZBytes.from(byteArrayInput) // Equivalent to `byteArrayInput.into()`
    var byteArrayOutput = payload.deserialize<ByteArray>().getOrThrow()
    check(byteArrayInput.contentEquals(byteArrayOutput))
    // Alternatively, we can directly access the bytes of property of ZBytes:
    byteArrayOutput = payload.bytes
    check(byteArrayInput.contentEquals(byteArrayOutput))

    /** List serialization and deserialization.
     *
     * Supported types:
     * - `List<ZBytes>`
     * - `List<ByteArray>`
     * - `List<String>`
     */
    val inputList = listOf("sample1", "sample2", "sample3")
    payload = ZBytes.serialize(inputList).getOrThrow()
    val outputList = payload.deserialize<List<String>>().getOrThrow()
    check(inputList == outputList)

    val inputListZBytes = inputList.map { value -> value.into() }
    payload = ZBytes.serialize(inputListZBytes).getOrThrow()
    val outputListZBytes = payload.deserialize<List<ZBytes>>().getOrThrow()
    check(inputListZBytes == outputListZBytes)

    val inputListByteArray = inputList.map { value -> value.toByteArray() }
    payload = ZBytes.serialize(inputListByteArray).getOrThrow()
    val outputListByteArray = payload.deserialize<List<ByteArray>>().getOrThrow()
    check(compareByteArrayLists(inputListByteArray, outputListByteArray))

    /** Map serialization and deserialization.
     *
     * Supported types:
     * - `Map<ZBytes, ZBytes>`
     * - `Map<ByteArray, ByteArray>`
     * - `Map<String, String>`
     */
    val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
    payload = ZBytes.serialize(inputMap).getOrThrow()
    val outputMap = payload.deserialize<Map<String, String>>().getOrThrow()
    check(inputMap == outputMap)

    /**
     * Custom serialization and deserialization.
     */
    val inputMyZBytes = MyZBytes("example")
    payload = ZBytes.serialize(inputMyZBytes).getOrThrow()
    val outputMyZBytes = payload.deserialize<MyZBytes>().getOrThrow()
    check(inputMyZBytes == outputMyZBytes)

    /** List of MyZBytes. */
    val inputListMyZBytes = inputList.map { value -> MyZBytes(value) }
    // Note that in order to perform the following serialization, MyZBytes must implement the interface IntoZBytes.
    payload = ZBytes.serialize<List<MyZBytes>>(inputListMyZBytes).getOrThrow()
    // Note that in order to perform the following deserialization, MyZBytes must implement the interface FromZBytes.Self (see below).
    val outputListMyZBytes = payload.deserialize<List<MyZBytes>>().getOrThrow()
    check(inputListMyZBytes == outputListMyZBytes)

    /** Map of MyZBytes. */
    val inputMapMyZBytes = inputMap.map { (k, v) -> MyZBytes(k) to MyZBytes(v)}.toMap()
    payload = ZBytes.serialize<Map<MyZBytes, MyZBytes>>(inputMapMyZBytes).getOrThrow()
    val outputMapMyZBytes = payload.deserialize<Map<MyZBytes, MyZBytes>>().getOrThrow()
    check(inputMapMyZBytes == outputMapMyZBytes)

}

class MyZBytes(val content: String) : Serializable {

    override fun into(): ZBytes = content.into()

    companion object : Serializable.From {
        override fun from(zbytes: ZBytes): MyZBytes {
            return MyZBytes(zbytes.toString())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MyZBytes

        return content == other.content
    }

    override fun hashCode(): Int {
        return content.hashCode()
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
