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

import io.zenoh.jni.JNIZBytes
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ZBytesTest {

    @Test
    fun deserializeStringTest() {
        val bytes = ZBytes("Hello world".toByteArray())
        val deserialized = bytes.deserialize<String>().getOrThrow()
        assertEquals("Hello world", deserialized)
    }

    @Test
    fun deserializeByteTest() {
        val bytes = ZBytes(byteArrayOf(42))
        val deserialized = bytes.deserialize<Byte>().getOrThrow()
        assertEquals(42, deserialized) // 42 + 12*256 + 3*256^2 = 199722
    }

    @Test
    fun deserializeShortTest() {
        val bytes = ZBytes(byteArrayOf(42, 12))
        val deserialized = bytes.deserialize<Short>().getOrThrow()
        assertEquals(3114, deserialized) // 42 + 12*256 = 3114
    }

    @Test
    fun deserializeIntTest() {
        val bytes = ZBytes(byteArrayOf(42, 12, 3, 0))
        val deserialized = bytes.deserialize<Int>().getOrThrow()
        assertEquals(199722, deserialized) // 42 + 12*256 + 3*256^2 = 199722
    }

    @Test
    fun deserializeLongTest() {
        val bytes = ZBytes(byteArrayOf(42, 12, 3, 0, 0, 0, 0, 1))
        val deserialized = bytes.deserialize<Long>().getOrThrow()
        assertEquals(72057594038127658, deserialized) // 42 + 12*256 + 3*256^2 + 1*256^7= 72057594038127658
    }

    @Test
    fun deserializeFloatTest() {
        val pi = 3.141516f
        val bytes: ZBytes = ByteBuffer.allocate(Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(pi)
            .array()
            .into()

        val deserialized = bytes.deserialize<Float>().getOrThrow()
        assertEquals(pi, deserialized)
    }

    @Test
    fun deserializeDoubleTest() {
        val euler = 2.71828
        val bytes: ZBytes = ByteBuffer.allocate(Double.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(euler)
            .array()
            .into()

        val deserialized = bytes.deserialize<Double>().getOrThrow()
        assertEquals(euler, deserialized)
    }

    @Test
    fun serializeAndDeserializeMapViaJNITest() {
        Zenoh.load()
        val originalMap = mapOf("key1".toByteArray() to "value1".toByteArray(), "key2".toByteArray() to "value2".toByteArray())
        val bytes = JNIZBytes.serializeIntoMapViaJNI(originalMap)
        val deserializedMap = JNIZBytes.deserializeIntoMapViaJNI(bytes)
        assertTrue(compareByteArrayMaps(originalMap, deserializedMap))
    }


    @Test
    fun deserializeIntoByteArrayMapViaJNITest() {
        Zenoh.load()
        val originalMap = mapOf("key1".toByteArray() to "value1".toByteArray(), "key2".toByteArray() to "value2".toByteArray())
        val zbytes: ZBytes = JNIZBytes.serializeIntoMapViaJNI(originalMap).into()
        val deserializedMap = zbytes.deserialize<Map<ByteArray, ByteArray>>().getOrThrow()
        assertTrue(compareByteArrayMaps(originalMap, deserializedMap))
    }

    @Test
    fun deserializeIntoStringMapViaJNITest() {
        Zenoh.load()
        val originalMap = mapOf("key1" to "value1", "key2" to "value2")
        val byteArrayMap = originalMap.map { (key, value) -> key.toByteArray() to value.toByteArray() }.toMap()
        val zbytes: ZBytes = JNIZBytes.serializeIntoMapViaJNI(byteArrayMap).into()
        val deserializedMap = zbytes.deserialize<Map<String, String>>().getOrThrow()
        assertEquals(originalMap, deserializedMap)
    }

    fun compareByteArrayMaps(
        map1: Map<ByteArray, ByteArray>,
        map2: Map<ByteArray, ByteArray>
    ): Boolean {
        if (map1.size != map2.size) return false

        for ((key1, value1) in map1) {
            val key2 = map2.keys.find { it.contentEquals(key1) }
            if (key2 == null) return false
            if (!map2[key2]!!.contentEquals(value1)) return false
        }
        return true
    }

    @Test
    fun serializeIntoListViaJNITest() {
        Zenoh.load()
        val list = listOf("value1", "value2", "value3")
        val zbytes: ZBytes = JNIZBytes.serializeIntoListViaJNI(list.map { it.toByteArray() }).into()

        val deserializedList = zbytes.deserialize<List<String>>().getOrThrow()
        assertEquals(list, deserializedList)
    }

    @Test
    fun customDeserializerTest() {
        val originalMap = mapOf("key1".into() to "value1".into(), "key2".into() to "value2".into())
        val serializedMap = serializeMap(originalMap)
        val bytes: ZBytes = serializedMap.into()

        val deserializedMap = bytes.deserialize<Map<ZBytes, ZBytes>>(mapOf(Map::class.java to ::deserializeMap)).getOrThrow()
        assertEquals(originalMap, deserializedMap)
    }

    fun serializeMap(testMap: Map<ZBytes, ZBytes>): ByteArray {
        return testMap.map {
            val key = it.key.bytes
            val keyLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array()
            val value = it.value.bytes
            val valueLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).array()
            keyLength + key + valueLength + value
        }.reduce { acc, bytes -> acc + bytes }
    }

    fun deserializeMap(serializedMap: ByteArray): Map<ZBytes, ZBytes> {
        var idx = 0
        var sliceSize: Int
        val decodedMap = mutableMapOf<ZBytes, ZBytes>()
        while (idx < serializedMap.size) {
            sliceSize = ByteBuffer.wrap(serializedMap.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val key = serializedMap.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            sliceSize = ByteBuffer.wrap(serializedMap.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(
                ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val value = serializedMap.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            decodedMap[key.into()] = value.into()
        }
        return decodedMap
    }
}