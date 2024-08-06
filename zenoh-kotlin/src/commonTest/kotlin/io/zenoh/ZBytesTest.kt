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

import io.zenoh.protocol.Deserializable
import io.zenoh.protocol.Serializable
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.typeOf
import kotlin.test.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

data class SimpleTestCase<T>(
    val originalItem: T
)

data class ListTestCase<T>(
    val originalList: List<T>
)

data class MapTestCase<K, V>(
    val originalMap: Map<K, V>,
)

class ZBytesTests {

    companion object {
        @JvmStatic
        fun simpleTestCases(): List<SimpleTestCase<*>> {
            return listOf(
                SimpleTestCase(1.toByte()),
                SimpleTestCase(1.toShort()),
                SimpleTestCase(1),
                SimpleTestCase(1L),
                SimpleTestCase(1.0f),
                SimpleTestCase(1.0),
                SimpleTestCase("value1"),
                SimpleTestCase(byteArrayOf(1, 2, 3)),
                SimpleTestCase(MyZBytes("foo"))
            )
        }

        @JvmStatic
        fun listTestCases(): List<ListTestCase<*>> {
            return listOf(
                // Byte Lists
                ListTestCase(listOf(1.toByte(), 2.toByte(), 3.toByte())),
                // Short Lists
                ListTestCase(listOf(1.toShort(), 2.toShort(), 3.toShort())),
                // Int Lists
                ListTestCase(listOf(1, 2, 3)),
                // Long Lists
                ListTestCase(listOf(1L, 2L, 3L)),
                // Float Lists
                ListTestCase(listOf(1.0f, 2.0f, 3.0f)),
                // Double Lists
                ListTestCase(listOf(1.0, 2.0, 3.0)),
                // String Lists
                ListTestCase(listOf("value1", "value2", "value3")),
                // ByteArray Lists
                ListTestCase(listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))),
                // MyZBytes Lists
                ListTestCase(listOf(MyZBytes("foo"), MyZBytes("bar")))
            )
        }

        @JvmStatic
        fun mapTestCases(): List<MapTestCase<*, *>> {
            return listOf(
                // Byte Keys
                MapTestCase(mapOf(1.toByte() to "value1", 2.toByte() to "value2")),
                MapTestCase(mapOf(1.toByte() to 1.toByte(), 2.toByte() to 2.toByte())),
                MapTestCase(mapOf(1.toByte() to 1.toShort(), 2.toByte() to 2.toShort())),
                MapTestCase(mapOf(1.toByte() to 1, 2.toByte() to 2)),
                MapTestCase(mapOf(1.toByte() to 1L, 2.toByte() to 2L)),
                MapTestCase(mapOf(1.toByte() to 1.0f, 2.toByte() to 2.0f)),
                MapTestCase(mapOf(1.toByte() to 1.0, 2.toByte() to 2.0)),
                MapTestCase(mapOf(1.toByte() to byteArrayOf(1, 2, 3), 2.toByte() to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1.toByte() to MyZBytes("foo"), 2.toByte() to MyZBytes("bar"))),

                // Short Keys
                MapTestCase(mapOf(1.toShort() to "value1", 2.toShort() to "value2")),
                MapTestCase(mapOf(1.toShort() to 1.toByte(), 2.toShort() to 2.toByte())),
                MapTestCase(mapOf(1.toShort() to 1.toShort(), 2.toShort() to 2.toShort())),
                MapTestCase(mapOf(1.toShort() to 1, 2.toShort() to 2)),
                MapTestCase(mapOf(1.toShort() to 1L, 2.toShort() to 2L)),
                MapTestCase(mapOf(1.toShort() to 1.0f, 2.toShort() to 2.0f)),
                MapTestCase(mapOf(1.toShort() to 1.0, 2.toShort() to 2.0)),
                MapTestCase(mapOf(1.toShort() to byteArrayOf(1, 2, 3), 2.toShort() to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1.toShort() to MyZBytes("foo"), 2.toShort() to MyZBytes("bar"))),

                // Int Keys
                MapTestCase(mapOf(1 to "value1", 2 to "value2")),
                MapTestCase(mapOf(1 to 1.toByte(), 2 to 2.toByte())),
                MapTestCase(mapOf(1 to 1.toShort(), 2 to 2.toShort())),
                MapTestCase(mapOf(1 to 1, 2 to 2)),
                MapTestCase(mapOf(1 to 1L, 2 to 2L)),
                MapTestCase(mapOf(1 to 1.0f, 2 to 2.0f)),
                MapTestCase(mapOf(1 to 1.0, 2 to 2.0)),
                MapTestCase(mapOf(1 to byteArrayOf(1, 2, 3), 2 to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1 to MyZBytes("foo"), 2 to MyZBytes("bar"))),

                // Long Keys
                MapTestCase(mapOf(1L to "value1", 2L to "value2")),
                MapTestCase(mapOf(1L to 1.toByte(), 2L to 2.toByte())),
                MapTestCase(mapOf(1L to 1.toShort(), 2L to 2.toShort())),
                MapTestCase(mapOf(1L to 1, 2L to 2)),
                MapTestCase(mapOf(1L to 1L, 2L to 2L)),
                MapTestCase(mapOf(1L to 1.0f, 2L to 2.0f)),
                MapTestCase(mapOf(1L to 1.0, 2L to 2.0)),
                MapTestCase(mapOf(1L to byteArrayOf(1, 2, 3), 2L to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1L to MyZBytes("foo"), 2L to MyZBytes("bar"))),

                // Float Keys
                MapTestCase(mapOf(1.0f to "value1", 2.0f to "value2")),
                MapTestCase(mapOf(1.0f to 1.toByte(), 2.0f to 2.toByte())),
                MapTestCase(mapOf(1.0f to 1.toShort(), 2.0f to 2.toShort())),
                MapTestCase(mapOf(1.0f to 1, 2.0f to 2)),
                MapTestCase(mapOf(1.0f to 1L, 2.0f to 2L)),
                MapTestCase(mapOf(1.0f to 1.0f, 2.0f to 2.0f)),
                MapTestCase(mapOf(1.0f to 1.0, 2.0f to 2.0)),
                MapTestCase(mapOf(1.0f to byteArrayOf(1, 2, 3), 2.0f to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1.0f to MyZBytes("foo"), 2.0f to MyZBytes("bar"))),

                // Double Keys
                MapTestCase(mapOf(1.0 to "value1", 2.0 to "value2")),
                MapTestCase(mapOf(1.0 to 1.toByte(), 2.0 to 2.toByte())),
                MapTestCase(mapOf(1.0 to 1.toShort(), 2.0 to 2.toShort())),
                MapTestCase(mapOf(1.0 to 1, 2.0 to 2)),
                MapTestCase(mapOf(1.0 to 1L, 2.0 to 2L)),
                MapTestCase(mapOf(1.0 to 1.0f, 2.0 to 2.0f)),
                MapTestCase(mapOf(1.0 to 1.0, 2.0 to 2.0)),
                MapTestCase(mapOf(1.0 to byteArrayOf(1, 2, 3), 2.0 to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(1.0 to MyZBytes("foo"), 2.0 to MyZBytes("bar"))),

                // String Keys
                MapTestCase(mapOf("key1" to "value1", "key2" to "value2")),
                MapTestCase(mapOf("key1" to 1.toByte(), "key2" to 2.toByte())),
                MapTestCase(mapOf("key1" to 1.toShort(), "key2" to 2.toShort())),
                MapTestCase(mapOf("key1" to 1, "key2" to 2)),
                MapTestCase(mapOf("key1" to 1L, "key2" to 2L)),
                MapTestCase(mapOf("key1" to 1.0f, "key2" to 2.0f)),
                MapTestCase(mapOf("key1" to 1.0, "key2" to 2.0)),
                MapTestCase(mapOf("key1" to byteArrayOf(1, 2, 3), "key2" to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf("key1" to MyZBytes("foo"), "key2" to MyZBytes("bar"))),

                // ByteArray Keys
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to "value1", byteArrayOf(4, 5, 6) to "value2")),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.toByte(), byteArrayOf(4, 5, 6) to 2.toByte())),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.toShort(), byteArrayOf(4, 5, 6) to 2.toShort())),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1, byteArrayOf(4, 5, 6) to 2)),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1L, byteArrayOf(4, 5, 6) to 2L)),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.0f, byteArrayOf(4, 5, 6) to 2.0f)),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to 1.0, byteArrayOf(4, 5, 6) to 2.0)),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6) to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(byteArrayOf(1, 2, 3) to MyZBytes("foo"), byteArrayOf(4, 5, 6) to MyZBytes("bar"))),

                // MyZBytes (Serializable and Deserializable) Keys
                MapTestCase(mapOf(MyZBytes("foo") to "value1", MyZBytes("bar") to "value2")),
                MapTestCase(mapOf(MyZBytes("foo") to 1.toByte(), MyZBytes("bar") to 2.toByte())),
                MapTestCase(mapOf(MyZBytes("foo") to 1.toShort(), MyZBytes("bar") to 2.toShort())),
                MapTestCase(mapOf(MyZBytes("foo") to 1, MyZBytes("bar") to 2)),
                MapTestCase(mapOf(MyZBytes("foo") to 1L, MyZBytes("bar") to 2L)),
                MapTestCase(mapOf(MyZBytes("foo") to 1.0f, MyZBytes("bar") to 2.0f)),
                MapTestCase(mapOf(MyZBytes("foo") to 1.0, MyZBytes("bar") to 2.0)),
                MapTestCase(mapOf(MyZBytes("foo") to byteArrayOf(1, 2, 3), MyZBytes("bar") to byteArrayOf(4, 5, 6))),
                MapTestCase(mapOf(MyZBytes("foo") to MyZBytes("foo"), MyZBytes("bar") to MyZBytes("bar")))
            )
        }
    }

    @ParameterizedTest
    @MethodSource("simpleTestCases")
    inline fun <reified T> serializationAndDeserialization_simpleTest(testCase: SimpleTestCase<T>) {
        val originalItem = testCase.originalItem

        val bytes = ZBytes.serialize(originalItem).getOrThrow()

        val deserializedItem = bytes.deserialize<T>().getOrThrow()

        if (originalItem is ByteArray) {
            assertArrayEquals(originalItem, deserializedItem as ByteArray)
        } else {
            assertEquals(originalItem, deserializedItem)
        }
    }

    @ParameterizedTest
    @MethodSource("listTestCases")
    inline fun <reified T> serializationAndDeserialization_listTest(testCase: ListTestCase<T>) {
        val originalList = testCase.originalList

        val bytes = ZBytes.serialize(originalList).getOrThrow()

        val deserializedList = bytes.deserialize<List<T>>().getOrThrow()

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
    inline fun <reified K, reified V> serializationAndDeserialization_mapTest(testCase: MapTestCase<K, V>) {
        val originalMap = testCase.originalMap

        val bytes = ZBytes.serialize(originalMap).getOrThrow()
        val deserializedMap = bytes.deserialize<Map<K, V>>().getOrThrow()

        assertEquals(originalMap, deserializedMap)
    }

//    @Test
//    fun customDeserializerTest() {
//        val stringMap = mapOf("key1" to "value1", "key2" to "value2")
//        val zbytesMap = stringMap.map { (k, v) -> k.into() to v.into() }.toMap()
//        val zbytesListOfPairs = stringMap.map { (k, v) -> k.into() to v.into() }
//        val intMap = mapOf(1 to 10, 2 to 20, 3 to 30)
//        val zbytesList = listOf(1.into(), 2.into(), 3.into())
//
//        val serializedBytes = serializeZBytesMap(zbytesMap)
//
//        val customDeserializers = mapOf(
//            typeOf<Map<ZBytes, ZBytes>>() to ::deserializeIntoZBytesMap,
//            typeOf<Map<String, String>>() to ::deserializeIntoStringMap,
//            typeOf<Map<Int, Int>>() to ::deserializeIntoIntMap,
//            typeOf<List<ZBytes>>() to ::deserializeIntoZBytesList,
//            typeOf<List<Pair<ZBytes, ZBytes>>>() to ::deserializeIntoListOfPairs,
//        )
//
//        val deserializedMap = serializedBytes.deserialize<Map<ZBytes, ZBytes>>(customDeserializers).getOrThrow()
//        assertEquals(zbytesMap, deserializedMap)
//
//        val deserializedMap2 = serializedBytes.deserialize<Map<String, String>>(customDeserializers).getOrThrow()
//        assertEquals(stringMap, deserializedMap2)
//
//        val intMapBytes = serializeIntoIntMap(intMap)
//        val deserializedMap3 = intMapBytes.deserialize<Map<Int, Int>>(customDeserializers).getOrThrow()
//        assertEquals(intMap, deserializedMap3)
//
//        val serializedZBytesList = serializeZBytesList(zbytesList)
//        val deserializedList = serializedZBytesList.deserialize<List<ZBytes>>(customDeserializers).getOrThrow()
//        assertEquals(zbytesList, deserializedList)
//
//        val serializedZBytesPairList = serializeZBytesMap(zbytesListOfPairs.toMap())
//        val deserializedZBytesPairList =
//            serializedZBytesPairList.deserialize<List<Pair<ZBytes, ZBytes>>>(customDeserializers).getOrThrow()
//        assertEquals(zbytesListOfPairs, deserializedZBytesPairList)
//    }
//
//
//    /*--------- Serializers and deserializers for testing purposes. ----------*/
//
//    private fun serializeZBytesMap(testMap: Map<ZBytes, ZBytes>): ZBytes {
//        return testMap.map {
//            val key = it.key.bytes
//            val keyLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array()
//            val value = it.value.bytes
//            val valueLength =
//                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).array()
//            keyLength + key + valueLength + value
//        }.reduce { acc, bytes -> acc + bytes }.into()
//    }
//
//    private fun deserializeIntoZBytesMap(serializedMap: ZBytes): Map<ZBytes, ZBytes> {
//        var idx = 0
//        var sliceSize: Int
//        val decodedMap = mutableMapOf<ZBytes, ZBytes>()
//        while (idx < serializedMap.bytes.size) {
//            sliceSize = ByteBuffer.wrap(serializedMap.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
//                .order(ByteOrder.LITTLE_ENDIAN).int
//            idx += Int.SIZE_BYTES
//
//            val key = serializedMap.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
//            idx += sliceSize
//
//            sliceSize = ByteBuffer.wrap(serializedMap.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(
//                ByteOrder.LITTLE_ENDIAN
//            ).int
//            idx += Int.SIZE_BYTES
//
//            val value = serializedMap.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
//            idx += sliceSize
//
//            decodedMap[key.into()] = value.into()
//        }
//        return decodedMap
//    }
//
//    private fun serializeIntoIntMap(intMap: Map<Int, Int>): ZBytes {
//        val zBytesMap = intMap.map { (k, v) -> k.into() to v.into() }.toMap()
//        return serializeZBytesMap(zBytesMap)
//    }
//
//    private fun deserializeIntoStringMap(serializerMap: ZBytes): Map<String, String> {
//        return deserializeIntoZBytesMap(serializerMap).map { (k, v) -> k.toString() to v.toString() }.toMap()
//    }
//
//    private fun deserializeIntoIntMap(serializerMap: ZBytes): Map<Int, Int> {
//        return deserializeIntoZBytesMap(serializerMap).map { (k, v) ->
//            k.deserialize<Int>().getOrThrow() to v.deserialize<Int>().getOrThrow()
//        }.toMap()
//    }
//
//    private fun serializeZBytesList(list: List<ZBytes>): ZBytes {
//        return list.map {
//            val item = it.bytes
//            val itemLength =
//                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(item.size).array()
//            itemLength + item
//        }.reduce { acc, bytes -> acc + bytes }.into()
//    }
//
//    private fun deserializeIntoZBytesList(serializedList: ZBytes): List<ZBytes> {
//        var idx = 0
//        var sliceSize: Int
//        val decodedList = mutableListOf<ZBytes>()
//        while (idx < serializedList.bytes.size) {
//            sliceSize = ByteBuffer.wrap(serializedList.bytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
//                .order(ByteOrder.LITTLE_ENDIAN).int
//            idx += Int.SIZE_BYTES
//
//            val item = serializedList.bytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
//            idx += sliceSize
//
//            decodedList.add(item.into())
//        }
//        return decodedList
//    }
//
//    private fun deserializeIntoListOfPairs(serializedList: ZBytes): List<Pair<ZBytes, ZBytes>> {
//        return deserializeIntoZBytesMap(serializedList).map { (k, v) -> k to v }
//    }
}

/**
 * Custom class for the tests. The purpose of this class is to test
 * the proper functioning of the serialization and deserialization for
 * a class implementing the [Serializable] and the [Deserializable] interface.
 */
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
