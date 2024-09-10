//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh

import io.zenoh.protocol.IntoZBytes
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import org.junit.jupiter.api.Assertions.assertArrayEquals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue

data class SimpleTestCase<T: Any>(
    val originalItem: T,
    val clazz: KClass<T>
)

data class ListTestCase<T: Any>(
    val originalList: List<T>,
    val itemclazz: KClass<T>
)

data class MapTestCase<K: Any, V: Any>(
    val originalMap: Map<K, V>,
    val keyclazz: KClass<K>,
    val valueclazz: KClass<V>,
)

class ZBytesTests {

    companion object {
        @JvmStatic
        fun simpleTestCases(): List<SimpleTestCase<*>> {
            return listOf(
                SimpleTestCase(1.toByte(), Byte::class),
                SimpleTestCase(1.toShort(), Short::class),
                SimpleTestCase(1, Int::class),
                SimpleTestCase(1L, Long::class),
                SimpleTestCase(1.0f, Float::class),
                SimpleTestCase(1.0, Double::class),
                SimpleTestCase("value1", String::class),
                SimpleTestCase(byteArrayOf(1, 2, 3), ByteArray::class),
            )
        }

        @JvmStatic
        fun listTestCases(): List<ListTestCase<*>> {
            return listOf(
                // Byte Lists
                ListTestCase(listOf(1.toByte(), 2.toByte(), 3.toByte()), Byte::class),
                // Short Lists
                ListTestCase(listOf(1.toShort(), 2.toShort(), 3.toShort()), Short::class),
                // Int Lists
                ListTestCase(listOf(1, 2, 3), Int::class),
                // Long Lists
                ListTestCase(listOf(1L, 2L, 3L), Long::class),
                // Float Lists
                ListTestCase(listOf(1.0f, 2.0f, 3.0f), Float::class),
                // Double Lists
                ListTestCase(listOf(1.0, 2.0, 3.0), Double::class),
                // String Lists
                ListTestCase(listOf("value1", "value2", "value3"), String::class),
                // ByteArray Lists
                ListTestCase(listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)), ByteArray::class),
            )
        }

        @JvmStatic
        fun mapTestCases(): List<MapTestCase<*, *>> {
            return listOf(
                // Byte Keys
                MapTestCase(mapOf(1.toByte() to "value1", 2.toByte() to "value2"), Byte::class, String::class),
                MapTestCase(mapOf(1.toByte() to 1.toByte(), 2.toByte() to 2.toByte()), Byte::class, Byte::class),
                MapTestCase(mapOf(1.toByte() to 1.toShort(), 2.toByte() to 2.toShort()), Byte::class, Short::class),
                MapTestCase(mapOf(1.toByte() to 1, 2.toByte() to 2), Byte::class, Int::class),
                MapTestCase(mapOf(1.toByte() to 1L, 2.toByte() to 2L), Byte::class, Long::class),
                MapTestCase(mapOf(1.toByte() to 1.0f, 2.toByte() to 2.0f), Byte::class, Float::class),
                MapTestCase(mapOf(1.toByte() to 1.0, 2.toByte() to 2.0), Byte::class, Double::class),
                MapTestCase(mapOf(1.toByte() to byteArrayOf(1, 2, 3), 2.toByte() to byteArrayOf(4, 5, 6)), Byte::class, ByteArray::class),

                // Short Keys
                MapTestCase(mapOf(1.toShort() to "value1", 2.toShort() to "value2"), Short::class, String::class),
                MapTestCase(mapOf(1.toShort() to 1.toByte(), 2.toShort() to 2.toByte()), Short::class, Byte::class),
                MapTestCase(mapOf(1.toShort() to 1.toShort(), 2.toShort() to 2.toShort()), Short::class, Short::class),
                MapTestCase(mapOf(1.toShort() to 1, 2.toShort() to 2), Short::class, Int::class),
                MapTestCase(mapOf(1.toShort() to 1L, 2.toShort() to 2L), Short::class, Long::class),
                MapTestCase(mapOf(1.toShort() to 1.0f, 2.toShort() to 2.0f), Short::class, Float::class),
                MapTestCase(mapOf(1.toShort() to 1.0, 2.toShort() to 2.0), Short::class, Double::class),
                MapTestCase(mapOf(1.toShort() to byteArrayOf(1, 2, 3), 2.toShort() to byteArrayOf(4, 5, 6)), Short::class, ByteArray::class),

                // Int Keys
                MapTestCase(mapOf(1 to "value1", 2 to "value2"), Int::class, String::class),
                MapTestCase(mapOf(1 to 1.toByte(), 2 to 2.toByte()), Int::class, Byte::class),
                MapTestCase(mapOf(1 to 1.toShort(), 2 to 2.toShort()), Int::class, Short::class),
                MapTestCase(mapOf(1 to 1, 2 to 2), Int::class, Int::class),
                MapTestCase(mapOf(1 to 1L, 2 to 2L), Int::class, Long::class),
                MapTestCase(mapOf(1 to 1.0f, 2 to 2.0f), Int::class, Float::class),
                MapTestCase(mapOf(1 to 1.0, 2 to 2.0), Int::class, Double::class),
                MapTestCase(mapOf(1 to byteArrayOf(1, 2, 3), 2 to byteArrayOf(4, 5, 6)), Int::class, ByteArray::class),

                // Long Keys
                MapTestCase(mapOf(1L to "value1", 2L to "value2"), Long::class, String::class),
                MapTestCase(mapOf(1L to 1.toByte(), 2L to 2.toByte()), Long::class, Byte::class),
                MapTestCase(mapOf(1L to 1.toShort(), 2L to 2.toShort()), Long::class, Short::class),
                MapTestCase(mapOf(1L to 1, 2L to 2), Long::class, Int::class),
                MapTestCase(mapOf(1L to 1L, 2L to 2L), Long::class, Long::class),
                MapTestCase(mapOf(1L to 1.0f, 2L to 2.0f), Long::class, Float::class),
                MapTestCase(mapOf(1L to 1.0, 2L to 2.0), Long::class, Double::class),
                MapTestCase(mapOf(1L to byteArrayOf(1, 2, 3), 2L to byteArrayOf(4, 5, 6)), Long::class, ByteArray::class),

                // Float Keys
                MapTestCase(mapOf(1.0f to "value1", 2.0f to "value2"), Float::class, String::class),
                MapTestCase(mapOf(1.0f to 1.toByte(), 2.0f to 2.toByte()), Float::class, Byte::class),
                MapTestCase(mapOf(1.0f to 1.toShort(), 2.0f to 2.toShort()), Float::class, Short::class),
                MapTestCase(mapOf(1.0f to 1, 2.0f to 2), Float::class, Int::class),
                MapTestCase(mapOf(1.0f to 1L, 2.0f to 2L), Float::class, Long::class),
                MapTestCase(mapOf(1.0f to 1.0f, 2.0f to 2.0f), Float::class, Float::class),
                MapTestCase(mapOf(1.0f to 1.0, 2.0f to 2.0), Float::class, Double::class),
                MapTestCase(mapOf(1.0f to byteArrayOf(1, 2, 3), 2.0f to byteArrayOf(4, 5, 6)), Float::class, ByteArray::class),

                // Double Keys
                MapTestCase(mapOf(1.0 to "value1", 2.0 to "value2"), Double::class, String::class),
                MapTestCase(mapOf(1.0 to 1.toByte(), 2.0 to 2.toByte()), Double::class, Byte::class),
                MapTestCase(mapOf(1.0 to 1.toShort(), 2.0 to 2.toShort()), Double::class, Short::class),
                MapTestCase(mapOf(1.0 to 1, 2.0 to 2), Double::class, Int::class),
                MapTestCase(mapOf(1.0 to 1L, 2.0 to 2L), Double::class, Long::class),
                MapTestCase(mapOf(1.0 to 1.0f, 2.0 to 2.0f), Double::class, Float::class),
                MapTestCase(mapOf(1.0 to 1.0, 2.0 to 2.0), Double::class, Double::class),
                MapTestCase(mapOf(1.0 to byteArrayOf(1, 2, 3), 2.0 to byteArrayOf(4, 5, 6)), Double::class, ByteArray::class),

                // String Keys
                MapTestCase(mapOf("key1" to "value1", "key2" to "value2"), String::class, String::class),
                MapTestCase(mapOf("key1" to 1.toByte(), "key2" to 2.toByte()), String::class, Byte::class),
                MapTestCase(mapOf("key1" to 1.toShort(), "key2" to 2.toShort()), String::class, Short::class),
                MapTestCase(mapOf("key1" to 1, "key2" to 2), String::class, Int::class),
                MapTestCase(mapOf("key1" to 1L, "key2" to 2L), String::class, Long::class),
                MapTestCase(mapOf("key1" to 1.0f, "key2" to 2.0f), String::class, Float::class),
                MapTestCase(mapOf("key1" to 1.0, "key2" to 2.0), String::class, Double::class),
                MapTestCase(mapOf("key1" to byteArrayOf(1, 2, 3), "key2" to byteArrayOf(4, 5, 6)), String::class, ByteArray::class),

                // ByteArray Keys
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to "value1", byteArrayOf(4, 5, 6) to "value2"), ByteArray::class, String::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.toByte(), byteArrayOf(4, 5, 6) to 2.toByte()), ByteArray::class, Byte::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.toShort(), byteArrayOf(4, 5, 6) to 2.toShort()), ByteArray::class, Short::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1, byteArrayOf(4, 5, 6) to 2), ByteArray::class, Int::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1L, byteArrayOf(4, 5, 6) to 2L), ByteArray::class, Long::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.0f, byteArrayOf(4, 5, 6) to 2.0f), ByteArray::class, Float::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.0, byteArrayOf(4, 5, 6) to 2.0), ByteArray::class, Double::class),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6) to byteArrayOf(4, 5, 6)), ByteArray::class, ByteArray::class),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("simpleTestCases")
    fun <T: Any> serializationAndDeserialization_simpleTest(testCase: SimpleTestCase<T>) {
        val originalItem = testCase.originalItem
        val clazz = testCase.clazz

        val bytes = ZBytes.serialize(originalItem, clazz = clazz).getOrThrow()
        val deserializedItem = bytes.deserialize(clazz = clazz).getOrThrow()

        if (originalItem is ByteArray) {
            assertArrayEquals(originalItem, deserializedItem as ByteArray)
        } else {
            assertEquals(originalItem, deserializedItem)
        }
    }

    @ParameterizedTest
    @MethodSource("listTestCases")
    fun <T: Any> serializationAndDeserialization_listTest(testCase: ListTestCase<T>) {
        val originalList = testCase.originalList
        val itemClass = testCase.itemclazz

        val bytes = ZBytes.serialize(originalList).getOrThrow()

        val deserializedList = bytes.deserialize(clazz = List::class, arg1clazz = itemClass).getOrThrow()

        if (originalList.isNotEmpty() && originalList[0] is ByteArray) {
            originalList.forEachIndexed { index, value ->
                assertArrayEquals(value as ByteArray, deserializedList[index] as ByteArray)
            }
        } else {
            assertEquals(originalList, deserializedList)
        }
    }

    @ParameterizedTest
    @MethodSource("mapTestCases")
    fun <K : Any, V : Any> serializationAndDeserialization_mapTest(testCase: MapTestCase<K, V>) {
        val originalMap = testCase.originalMap
        val keyClass = testCase.keyclazz
        val valueClass = testCase.valueclazz

        val bytes = ZBytes.serialize(originalMap).getOrThrow()

        val deserializedMap = bytes.deserialize(
            clazz = Map::class,
            arg1clazz = keyClass,
            arg2clazz = valueClass
        ).getOrThrow()

        if (keyClass == ByteArray::class && valueClass != ByteArray::class) {
            val map1 = originalMap.map { (k, v) -> (k as ByteArray).toList() to v }.toMap()
            val map2 = originalMap.map { (k, v) -> (k as ByteArray).toList() to v }.toMap()
            assertEquals(map1, map2)
            return
        }

        if (keyClass != ByteArray::class && valueClass == ByteArray::class) {
            val map1 = originalMap.map { (k, v) -> k to (v as ByteArray).toList() }.toMap()
            val map2 = originalMap.map { (k, v) -> k to (v as ByteArray).toList() }.toMap()
            assertEquals(map1, map2)
            return
        }

        if (keyClass == ByteArray::class && valueClass == ByteArray::class) {
            val map1 = originalMap.map { (k, v) -> (k as ByteArray).toList() to (v as ByteArray).toList() }.toMap()
            val map2 = originalMap.map { (k, v) -> (k as ByteArray).toList() to (v as ByteArray).toList() }.toMap()
            assertEquals(map1, map2)
            return
        }

        assertEquals(originalMap, deserializedMap)
    }

    @Test
    fun deserializationWithMapOfDeserializationFunctionsTest() {
        val stringMap = mapOf("key1" to "value1", "key2" to "value2")
        val zbytesMap = stringMap.map { (k, v) -> k.into() to v.into() }.toMap()
        val zbytesListOfPairs = stringMap.map { (k, v) -> k.into() to v.into() }
        val intMap = mapOf(1 to 10, 2 to 20, 3 to 30)
        val zbytesList = listOf(1.into(), 2.into(), 3.into())

        val serializedBytes = serializeZBytesMap(zbytesMap)

        val customDeserializers = mapOf(
            typeOf<Map<ZBytes, ZBytes>>() to ::deserializeIntoZBytesMap,
            typeOf<Map<String, String>>() to ::deserializeIntoStringMap,
            typeOf<Map<Int, Int>>() to ::deserializeIntoIntMap,
            typeOf<List<ZBytes>>() to ::deserializeIntoZBytesList,
            typeOf<List<Pair<ZBytes, ZBytes>>>() to ::deserializeIntoListOfPairs,
        )

        val deserializedMap = serializedBytes.deserialize<Map<ZBytes, ZBytes>>(customDeserializers).getOrThrow()
        assertEquals(zbytesMap, deserializedMap)

        val deserializedMap2 = serializedBytes.deserialize<Map<String, String>>(customDeserializers).getOrThrow()
        assertEquals(stringMap, deserializedMap2)

        val intMapBytes = serializeIntoIntMap(intMap)
        val deserializedMap3 = intMapBytes.deserialize<Map<Int, Int>>(customDeserializers).getOrThrow()
        assertEquals(intMap, deserializedMap3)

        val serializedZBytesList = serializeZBytesList(zbytesList)
        val deserializedList = serializedZBytesList.deserialize<List<ZBytes>>(customDeserializers).getOrThrow()
        assertEquals(zbytesList, deserializedList)

        val serializedZBytesPairList = serializeZBytesMap(zbytesListOfPairs.toMap())
        val deserializedZBytesPairList =
            serializedZBytesPairList.deserialize<List<Pair<ZBytes, ZBytes>>>(customDeserializers).getOrThrow()
        assertEquals(zbytesListOfPairs, deserializedZBytesPairList)
    }

    /**
     * A series of tests to verify the correct functioning of the [ZBytes.deserialize] function.
     *
     * The [ZBytes.deserialize] function with reification can not be tested in a parametrized fashion because
     * it uses reified parameters which causes the testing framework (designed for Java) to fail to properly
     * set up the tests.
     */
    @Test
    fun serializationAndDeserializationWithReification() {
        /***********************************************
         * Standard serialization and deserialization. *
         ***********************************************/

        /** Numeric: byte, short, int, float, double */
        val intInput = 1234
        var payload = ZBytes.from(intInput)
        val intOutput = payload.deserialize<Int>().getOrThrow()
        assertEquals(intInput, intOutput)

        // Another example with float
        val floatInput = 3.1415f
        payload = ZBytes.from(floatInput)
        val floatOutput = payload.deserialize<Float>().getOrThrow()
        assertEquals(floatInput, floatOutput)

        /** String serialization and deserialization. */
        val stringInput = "example"
        payload = ZBytes.from(stringInput)
        val stringOutput = payload.deserialize<String>().getOrThrow()
        assertEquals(stringInput, stringOutput)

        /** ByteArray serialization and deserialization. */
        val byteArrayInput = "example".toByteArray()
        payload = ZBytes.from(byteArrayInput) // Equivalent to `byteArrayInput.into()`
        val byteArrayOutput = payload.deserialize<ByteArray>().getOrThrow()
        assertTrue(byteArrayInput.contentEquals(byteArrayOutput))

        val inputList = listOf("sample1", "sample2", "sample3")
        payload = ZBytes.serialize(inputList).getOrThrow()
        val outputList = payload.deserialize<List<String>>().getOrThrow()
        assertEquals(inputList, outputList)

        val inputListZBytes = inputList.map { value -> value.into() }
        payload = ZBytes.serialize(inputListZBytes).getOrThrow()
        val outputListZBytes = payload.deserialize<List<ZBytes>>().getOrThrow()
        assertEquals(inputListZBytes, outputListZBytes)

        val inputListByteArray = inputList.map { value -> value.toByteArray() }
        payload = ZBytes.serialize(inputListByteArray).getOrThrow()
        val outputListByteArray = payload.deserialize<List<ByteArray>>().getOrThrow()
        assertTrue(compareByteArrayLists(inputListByteArray, outputListByteArray))

        val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        payload = ZBytes.serialize(inputMap).getOrThrow()
        val outputMap = payload.deserialize<Map<String, String>>().getOrThrow()
        assertEquals(inputMap, outputMap)

        val combinedInputMap = mapOf("key1" to ZBytes.from("zbytes1"), "key2" to ZBytes.from("zbytes2"))
        payload = ZBytes.serialize(combinedInputMap).getOrThrow()
        val combinedOutputMap = payload.deserialize<Map<String, ZBytes>>().getOrThrow()
        assertEquals(combinedInputMap, combinedOutputMap)

        /*********************************************
         * Custom serialization and deserialization. *
         *********************************************/

        /**
         * Providing a map of deserializers.
         */
        val fooMap = mapOf(Foo("foo1") to Foo("bar1"), Foo("foo2") to Foo("bar2"))
        val fooMapSerialized = ZBytes.from(serializeFooMap(fooMap))
        val deserializersMap = mapOf(typeOf<Map<Foo, Foo>>() to ::deserializeFooMap)
        val deserializedFooMap = fooMapSerialized.deserialize<Map<Foo, Foo>>(deserializersMap).getOrThrow()
        assertEquals(fooMap, deserializedFooMap)
    }

    /*****************
     * Testing utils *
     *****************/

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


    /**********************************************************************************
     * Serializers and deserializers for testing the functionality of deserialization *
     * with deserializer functions.                                                   *
     **********************************************************************************/

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

    private fun serializeZBytesMap(testMap: Map<ZBytes, ZBytes>): ZBytes {
        return testMap.map {
            val key = it.key.bytes
            val keyLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array()
            val value = it.value.bytes
            val valueLength =
                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).array()
            keyLength + key + valueLength + value
        }.reduce { acc, bytes -> acc + bytes }.into()
    }

    private fun deserializeIntoZBytesMap(serializedMap: ZBytes): Map<ZBytes, ZBytes> {
        var idx = 0
        var sliceSize: Int
        val decodedMap = mutableMapOf<ZBytes, ZBytes>()
        while (idx < serializedMap.bytes.size) {
            sliceSize = ByteBuffer.wrap(serializedMap.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
                .order(ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val key = serializedMap.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            sliceSize = ByteBuffer.wrap(serializedMap.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(
                ByteOrder.LITTLE_ENDIAN
            ).int
            idx += Int.SIZE_BYTES

            val value = serializedMap.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            decodedMap[key.into()] = value.into()
        }
        return decodedMap
    }

    private fun serializeIntoIntMap(intMap: Map<Int, Int>): ZBytes {
        val zBytesMap = intMap.map { (k, v) -> k.into() to v.into() }.toMap()
        return serializeZBytesMap(zBytesMap)
    }

    private fun deserializeIntoStringMap(serializerMap: ZBytes): Map<String, String> {
        return deserializeIntoZBytesMap(serializerMap).map { (k, v) -> k.toString() to v.toString() }.toMap()
    }

    private fun deserializeIntoIntMap(serializerMap: ZBytes): Map<Int, Int> {
        return deserializeIntoZBytesMap(serializerMap).map { (k, v) ->
            k.deserialize<Int>().getOrThrow() to v.deserialize<Int>().getOrThrow()
        }.toMap()
    }

    private fun serializeZBytesList(list: List<ZBytes>): ZBytes {
        return list.map {
            val item = it.bytes
            val itemLength =
                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(item.size).array()
            itemLength + item
        }.reduce { acc, bytes -> acc + bytes }.into()
    }

    private fun deserializeIntoZBytesList(serializedList: ZBytes): List<ZBytes> {
        var idx = 0
        var sliceSize: Int
        val decodedList = mutableListOf<ZBytes>()
        while (idx < serializedList.bytes.size) {
            sliceSize = ByteBuffer.wrap(serializedList.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
                .order(ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val item = serializedList.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            decodedList.add(item.into())
        }
        return decodedList
    }

    private fun deserializeIntoListOfPairs(serializedList: ZBytes): List<Pair<ZBytes, ZBytes>> {
        return deserializeIntoZBytesMap(serializedList).map { (k, v) -> k to v }
    }
}
