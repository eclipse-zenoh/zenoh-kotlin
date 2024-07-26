package io.zenoh

import io.zenoh.protocol.*

fun main() {
    /** Numeric: byte, short, int, float, double */
    val intInput = 1234
    var payload = ZBytes.from(intInput)
    var intOutput = payload.deserialize<Int>().getOrThrow()
    assert(intInput == intOutput)

    // Alternatively, `Numeric.into()`: ZBytes can be used
    payload = intInput.into()
    intOutput = payload.deserialize<Int>().getOrThrow()
    assert(intInput == intOutput)

    // Another example with float
    val floatInput = 3.1415f
    payload = ZBytes.from(floatInput)
    val floatOutput = payload.deserialize<Float>().getOrThrow()
    assert(floatInput == floatOutput)

    /** String serialization and deserialization. */
    val stringInput = "example"
    payload = ZBytes.from(stringInput)
    // Alternatively, you can also call `String.into()` to convert
    // a string into a ZBytes object:
    // payload = stringInput.into()
    var stringOutput = payload.deserialize<String>().getOrThrow()
    assert(stringInput == stringOutput)

    // For the case of strings, ZBytes::toString() is equivalent:
    stringOutput = payload.toString()
    assert(stringInput == stringOutput)

    /** ByteArray serialization and deserialization. */
    val byteArrayInput = "example".toByteArray()
    payload = ZBytes.from(byteArrayInput) // Equivalent to `byteArrayInput.into()`
    var byteArrayOutput = payload.deserialize<ByteArray>().getOrThrow()
    assert(byteArrayInput.contentEquals(byteArrayOutput))
    // Alternatively, we can directly access the bytes of property of ZBytes:
    byteArrayOutput = payload.bytes
    assert(byteArrayInput.contentEquals(byteArrayOutput))

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
    assert(inputList == outputList)

    val inputListZBytes = inputList.map { value -> value.into() }
    payload = ZBytes.serialize(inputListZBytes).getOrThrow()
    val outputListZBytes = payload.deserialize<List<ZBytes>>()
    assert(inputListZBytes == outputListZBytes)

    val inputListByteArray = inputList.map { value -> value.toByteArray() }
    payload = ZBytes.serialize(inputListByteArray).getOrThrow()
    val outputListByteArray = payload.deserialize<List<ByteArray>>().getOrThrow()
    assert(inputListByteArray == outputListByteArray)

    /**
     * Custom serialization and deserialization.
     */
    val inputMyZBytes = MyZBytes("example")
    payload = ZBytes.serialize(inputMyZBytes).getOrThrow()
    val outputMyZBytes = payload.deserialize<MyZBytes>().getOrThrow()
    assert(inputMyZBytes == outputMyZBytes)

    /** List of MyZBytes. */
    val inputListMyZBytes = inputList.map { value -> MyZBytes(value) }
    // Note that in order to perform the following serialization, MyZBytes must implement the interface IntoZBytes.
    payload = ZBytes.serialize<List<MyZBytes>>(inputListMyZBytes).getOrThrow()
    // Note that in order to perform the following deserialization, MyZBytes must implement the interface FromZBytes.Self (see below).
    val outputListMyZBytes = payload.deserialize<List<MyZBytes>>().getOrThrow()
    assert(inputListMyZBytes == outputListMyZBytes)
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
