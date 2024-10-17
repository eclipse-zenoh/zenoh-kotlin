////
//// Copyright (c) 2023 ZettaScale Technology
////
//// This program and the accompanying materials are made available under the
//// terms of the Eclipse Public License 2.0 which is available at
//// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
//// which is available at https://www.apache.org/licenses/LICENSE-2.0.
////
//// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
////
//// Contributors:
////   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
////
//
package io.zenoh

import io.zenoh.ext.zDeserialize
import io.zenoh.ext.zSerialize

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class ZBytesTests {

    /**
     * A series of tests to verify the correct functioning of the [zDeserialize] function.
     *
     * The [zDeserialize] function with reification can not be tested in a parametrized fashion because
     * it uses reified parameters which causes the testing framework (designed for Java) to fail to properly
     * set up the tests.
     */

    /***********************************************
     * Standard serialization and deserialization. *
     ***********************************************/

    @Test
    fun `test int serialization and deserialization`() {
        val intInput = 1234
        val payload = zSerialize(intInput).getOrThrow()
        val intOutput = zDeserialize<Int>(payload).getOrThrow()
        assertEquals(intInput, intOutput)
    }

    @Test
    fun `test float serialization and deserialization`() {
        val floatInput = 3.1415f
        val payload = zSerialize(floatInput).getOrThrow()
        val floatOutput = zDeserialize<Float>(payload).getOrThrow()
        assertEquals(floatInput, floatOutput)
    }

    @Test
    fun `test string serialization and deserialization`() {
        val stringInput = "example"
        val payload = zSerialize(stringInput).getOrThrow()
        val stringOutput = zDeserialize<String>(payload).getOrThrow()
        assertEquals(stringInput, stringOutput)
    }

    @Test
    fun `test byte array serialization and deserialization`() {
        val byteArrayInput = "example".toByteArray()
        val payload = zSerialize(byteArrayInput).getOrThrow()
        val byteArrayOutput = zDeserialize<ByteArray>(payload).getOrThrow()
        assertTrue(byteArrayInput.contentEquals(byteArrayOutput))
    }

    @Test
    fun `test list of strings serialization and deserialization`() {
        val inputList = listOf("sample1", "sample2", "sample3")
        val payload = zSerialize(inputList).getOrThrow()
        val outputList = zDeserialize<List<String>>(payload).getOrThrow()
        assertEquals(inputList, outputList)
    }

    @Test
    fun `test list of byte arrays serialization and deserialization`() {
        val inputListByteArray = listOf("sample1", "sample2", "sample3").map { it.toByteArray() }
        val payload = zSerialize(inputListByteArray).getOrThrow()
        val outputListByteArray = zDeserialize<List<ByteArray>>(payload).getOrThrow()
        assertTrue(compareByteArrayLists(inputListByteArray, outputListByteArray))
    }

    @Test
    fun `test map of strings serialization and deserialization`() {
        val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        val payload = zSerialize(inputMap).getOrThrow()
        val outputMap = zDeserialize<Map<String, String>>(payload).getOrThrow()
        assertEquals(inputMap, outputMap)
    }

    /**********************************************
     * Additional test cases for new Kotlin types *
     **********************************************/

    @Test
    fun `test boolean serialization and deserialization`() {
        val booleanInput = true
        val payload = zSerialize(booleanInput).getOrThrow()
        val booleanOutput = zDeserialize<Boolean>(payload).getOrThrow()
        assertEquals(booleanInput, booleanOutput)
    }

    @Test
    fun `test UByte serialization and deserialization`() {
        val ubyteInput: UByte = 100u
        val payload = zSerialize(ubyteInput).getOrThrow()
        val ubyteOutput = zDeserialize<UByte>(payload).getOrThrow()
        assertEquals(ubyteInput, ubyteOutput)
    }

    @Test
    fun `test UShort serialization and deserialization`() {
        val ushortInput: UShort = 300u
        val payload = zSerialize(ushortInput).getOrThrow()
        val ushortOutput = zDeserialize<UShort>(payload).getOrThrow()
        assertEquals(ushortInput, ushortOutput)
    }

    @Test
    fun `test UInt serialization and deserialization`() {
        val uintInput: UInt = 123456789u
        val payload = zSerialize(uintInput).getOrThrow()
        val uintOutput = zDeserialize<UInt>(payload).getOrThrow()
        assertEquals(uintInput, uintOutput)
    }

    @Test
    fun `test ULong serialization and deserialization`() {
        val ulongInput: ULong = 9876543210uL
        val payload = zSerialize(ulongInput).getOrThrow()
        val ulongOutput = zDeserialize<ULong>(payload).getOrThrow()
        assertEquals(ulongInput, ulongOutput)
    }

    @Test
    fun `test Pair serialization and deserialization`() {
        val pairInput = Pair(42, 0.5)
        val payload = zSerialize(pairInput).getOrThrow()
        val pairOutput = zDeserialize<Pair<Int, Double>>(payload).getOrThrow()
        assertEquals(pairInput, pairOutput)
    }

    @Test
    fun `test Triple serialization and deserialization`() {
        val tripleInput = Triple(42, 0.5, listOf(true, false))
        val payload = zSerialize(tripleInput).getOrThrow()
        val tripleOutput = zDeserialize<Triple<Int, Double, List<Boolean>>>(payload).getOrThrow()
        assertEquals(tripleInput, tripleOutput)
    }

    /**********************************************
     * Tests for collections with new types       *
     **********************************************/

    @Test
    fun `test list of booleans serialization and deserialization`() {
        val listBooleanInput = listOf(true, false, true)
        val payload = zSerialize(listBooleanInput).getOrThrow()
        val listBooleanOutput = zDeserialize<List<Boolean>>(payload).getOrThrow()
        assertEquals(listBooleanInput, listBooleanOutput)
    }

    @Test
    fun `test map of string to ULong serialization and deserialization`() {
        val mapStringULongInput = mapOf("key1" to 1uL, "key2" to 2uL, "key3" to 3uL)
        val payload = zSerialize(mapStringULongInput).getOrThrow()
        val mapStringULongOutput = zDeserialize<Map<String, ULong>>(payload).getOrThrow()
        assertEquals(mapStringULongInput, mapStringULongOutput)
    }

    @Test
    fun `test list of maps serialization and deserialization`() {
        val listOfMapsInput = listOf(
            mapOf("key1" to 1uL, "key2" to 2uL),
            mapOf("key3" to 3uL, "key4" to 4uL)
        )
        val payload = zSerialize(listOfMapsInput).getOrThrow()
        val listOfMapsOutput = zDeserialize<List<Map<String, ULong>>>(payload).getOrThrow()
        assertEquals(listOfMapsInput, listOfMapsOutput)
    }

    @Test
    fun `test map of string to list of int serialization and deserialization`() {
        val mapOfListInput = mapOf("numbers" to listOf(1, 2, 3, 4, 5))
        val payload = zSerialize(mapOfListInput).getOrThrow()
        val mapOfListOutput = zDeserialize<Map<String, List<Int>>>(payload).getOrThrow()
        assertEquals(mapOfListInput, mapOfListOutput)
    }

    @Test
    fun `test nested pairs serialization and deserialization`() {
        val pairInput = Pair(42, Pair(0.5, true))
        val payload = zSerialize(pairInput).getOrThrow()
        val pairOutput = zDeserialize<Pair<Int, Pair<Double, Boolean>>>(payload).getOrThrow()
        assertEquals(pairInput, pairOutput)
    }

    /*****************
     * Testing utils *
     *****************/

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
}
