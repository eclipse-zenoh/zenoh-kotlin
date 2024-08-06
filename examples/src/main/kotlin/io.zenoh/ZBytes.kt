package io.zenoh

import io.zenoh.protocol.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.typeOf

fun main() {

    /***********************************************
     * Standard serialization and deserialization. *
     ***********************************************/

    /** Numeric: byte, short, int, float, double */
    val intInput = 1234
    var payload = ZBytes.from(intInput)
    var intOutput = payload.deserialize<Int>().getOrThrow()
    check(intInput == intOutput)

    // Alternatively you can serialize into the type.
    payload = ZBytes.serialize(intInput).getOrThrow()
    intOutput = payload.deserialize<Int>().getOrThrow()
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
    byteArrayOutput = payload.toByteArray()
    check(byteArrayInput.contentEquals(byteArrayOutput))

    /** List serialization and deserialization.
     *
     * Supported types: String, ByteArray, ZBytes, Byte, Short, Int, Long, Float and Double.
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

    /**
     * Map serialization and deserialization.
     *
     * Maps with the following Type combinations are supported: String, ByteArray, ZBytes, Byte, Short, Int, Long, Float and Double.
     */
    val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
    payload = ZBytes.serialize(inputMap).getOrThrow()
    val outputMap = payload.deserialize<Map<String, String>>().getOrThrow()
    check(inputMap == outputMap)

    val combinedInputMap = mapOf("key1" to ZBytes.from("zbytes1"), "key2" to ZBytes.from("zbytes2"))
    payload = ZBytes.serialize(combinedInputMap).getOrThrow()
    val combinedOutputMap = payload.deserialize<Map<String, ZBytes>>().getOrThrow()
    check(combinedInputMap == combinedOutputMap)

    /*********************************************
     * Custom serialization and deserialization. *
     *********************************************/

    /**
     * The examples below use [MyZBytes], an example class consisting that implements the [Serializable] interface.
     *
     * In order for the serialization and deserialization to be successful on a custom class,
     * the class itself must override the `into(): ZBytes` function, but also the companion
     * object must implement the [Deserializable.From] interface.
     *
     * @see MyZBytes
     */
    val inputMyZBytes = MyZBytes("example")
    payload = ZBytes.serialize(inputMyZBytes).getOrThrow()
    val outputMyZBytes = payload.deserialize<MyZBytes>().getOrThrow()
    check(inputMyZBytes == outputMyZBytes)

    /** List of MyZBytes. */
    val inputListMyZBytes = inputList.map { value -> MyZBytes(value) }
    payload = ZBytes.serialize<List<MyZBytes>>(inputListMyZBytes).getOrThrow()
    val outputListMyZBytes = payload.deserialize<List<MyZBytes>>().getOrThrow()
    check(inputListMyZBytes == outputListMyZBytes)

    /** Map of MyZBytes. */
    val inputMapMyZBytes = inputMap.map { (k, v) -> MyZBytes(k) to MyZBytes(v)}.toMap()
    payload = ZBytes.serialize<Map<MyZBytes, MyZBytes>>(inputMapMyZBytes).getOrThrow()
    val outputMapMyZBytes = payload.deserialize<Map<MyZBytes, MyZBytes>>().getOrThrow()
    check(inputMapMyZBytes == outputMapMyZBytes)

    val combinedMap = mapOf(MyZBytes("foo") to 1, MyZBytes("bar") to 2)
    payload = ZBytes.serialize<Map<MyZBytes, Int>>(combinedMap).getOrThrow()
    val combinedOutput = payload.deserialize<Map<MyZBytes, Int>>().getOrThrow()
    check(combinedMap == combinedOutput)

    /**
     * Providing a map of deserializers.
     *
     * Alternatively, [ZBytes.deserialize] also accepts a deserializers parameter of type
     * `Map<KType, KFunction1<ByteArray, Any>>`. That is, a map of types that is associated
     * to a function receiving a ByteArray, that returns Any. This way, you can provide a series
     * of deserializer functions that extend the deserialization mechanisms we provide by default.
     *
     * For example, let's say we have a custom map serializer, with its own deserializer:
     */
    val fooMap = mapOf(Foo("foo1") to Foo("bar1"), Foo("foo2") to Foo("bar2"))
    val fooMapSerialized = ZBytes.from(serializeFooMap(fooMap))
    val deserializersMap = mapOf(typeOf<Map<Foo, Foo>>() to ::deserializeFooMap)
    val deserializedFooMap = fooMapSerialized.deserialize<Map<Foo, Foo>>(deserializersMap).getOrThrow()
    check(fooMap == deserializedFooMap)
}

class MyZBytes(val content: String) : Serializable, Deserializable {

    override fun into(): ZBytes = content.into()

    companion object : Deserializable.From {
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

/** Example class for the deserialization map examples. */
class Foo(val content: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Foo

        return content == other.content
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }
}

/** Example serializer and deserializer. */
private fun serializeFooMap(testMap: Map<Foo, Foo>): ByteArray {
    return testMap.map {
        val key = it.key.content.toByteArray()
        val keyLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array()
        val value = it.value.content.toByteArray()
        val valueLength =
            ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).array()
        keyLength + key + valueLength + value
    }.reduce { acc, bytes -> acc + bytes }
}

private fun deserializeFooMap(serializedMap: ZBytes): Map<Foo, Foo> {
    var idx = 0
    var sliceSize: Int
    val bytes = serializedMap.toByteArray()
    val decodedMap = mutableMapOf<Foo, Foo>()
    while (idx < bytes.size) {
        sliceSize = ByteBuffer.wrap(bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
            .order(ByteOrder.LITTLE_ENDIAN).int
        idx += Int.SIZE_BYTES

        val key = bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
        idx += sliceSize

        sliceSize = ByteBuffer.wrap(bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(
            ByteOrder.LITTLE_ENDIAN
        ).int
        idx += Int.SIZE_BYTES

        val value = bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
        idx += sliceSize

        decodedMap[Foo(key.decodeToString())] = Foo(value.decodeToString())
    }
    return decodedMap
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
