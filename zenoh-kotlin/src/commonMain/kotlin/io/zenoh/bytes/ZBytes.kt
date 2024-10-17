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

package io.zenoh.bytes

/**
 * The ZBytes class (Zenoh bytes) represents the bytes received through the Zenoh network.
 *
 * To serialize a type into a ZBytes it must be done through [io.zenoh.ext.zSerialize], while for deserialization
 * [io.zenoh.ext.zDeserialize] must be called.
 *
 * Supported types:
 * - Boolean
 * - Byte
 * - Short
 * - Int
 * - Long
 * - Float
 * - Double
 * - List
 * - Map
 *
 * Lists and Maps support nested types, that is, you could have any combination of the above types, for instance
 * List of Lists, List of Maps, Maps of Maps...
 *
 * Checkout the example below from the ZBytes example:
 *
 * ```kotlin
 * /***********************************************
 *  * Standard serialization and deserialization. *
 * ***********************************************/
 *
 * /** Numeric: byte, short, int, float, double */
 * val intInput = 1234
 * var payload = zSerialize(intInput).getOrThrow()
 * val intOutput = zDeserialize<Int>(payload).getOrThrow()
 * check(intInput == intOutput)
 *
 * // Another example with float
 * val floatInput = 3.1415f
 * payload = zSerialize(floatInput).getOrThrow()
 * val floatOutput = zDeserialize<Float>(payload).getOrThrow()
 * check(floatInput == floatOutput)
 *
 * /** String serialization and deserialization. */
 * val stringInput = "example"
 * payload = zSerialize(stringInput).getOrThrow()
 * var stringOutput = zDeserialize<String>(payload).getOrThrow()
 * check(stringInput == stringOutput)
 *
 * /** ByteArray serialization and deserialization. */
 * val byteArrayInput = "example".toByteArray()
 * payload = zSerialize(byteArrayInput).getOrThrow()
 * val byteArrayOutput = zDeserialize<ByteArray>(payload).getOrThrow()
 * check(byteArrayInput.contentEquals(byteArrayOutput))
 *
 * /**
 *  * List serialization and deserialization.
 *  *
 *  * Supported types: String, ByteArray, Byte, Short, Int, Long, Float and Double.
 * */
 * val inputList = listOf("sample1", "sample2", "sample3")
 * payload = zSerialize(inputList).getOrThrow()
 * val outputList = zDeserialize<List<String>>(payload).getOrThrow()
 * check(inputList == outputList)
 *
 * val inputListInt = listOf(1, 2, 3)
 * payload = zSerialize(inputListInt).getOrThrow()
 * val outputListInt = zDeserialize<List<Int>>(payload).getOrThrow()
 * check(inputListInt == outputListInt)
 *
 * val inputListByteArray = inputList.map { value -> value.toByteArray() }
 * payload = zSerialize(inputListByteArray).getOrThrow()
 * val outputListByteArray = zDeserialize<List<ByteArray>>(payload).getOrThrow()
 * check(compareByteArrayLists(inputListByteArray, outputListByteArray))
 *
 * /** Nested lists */
 * val nestedList = listOf(listOf(1, 2, 3))
 * payload = zSerialize(nestedList).getOrThrow()
 * val outputNestedList = zDeserialize<List<List<Int>>>(payload).getOrThrow()
 * check(nestedList == outputNestedList)
 *
 * /** Combined types */
 * val combinedList = listOf(mapOf("a" to 1, "b" to 2))
 * payload = zSerialize(combinedList).getOrThrow()
 * val outputCombinedList = zDeserialize<List<Map<String, Int>>>(payload).getOrThrow()
 * check(combinedList == outputCombinedList)
 *
 * /**
 *  * Map serialization and deserialization.
 *  *
 *  * Maps with the following Type combinations are supported: String, ByteArray, Byte, Short, Int, Long, Float and Double.
 *  */
 * val inputMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
 * payload = zSerialize(inputMap).getOrThrow()
 * val outputMap = zDeserialize<Map<String, String>>(payload).getOrThrow()
 * check(inputMap == outputMap)
 *
 * val combinedInputMap = mapOf("key1" to 1, "key2" to 2)
 * payload = zSerialize(combinedInputMap).getOrThrow()
 * val combinedOutputMap = zDeserialize<Map<String, Int>>(payload).getOrThrow()
 * check(combinedInputMap == combinedOutputMap)
 * ```
 */
class ZBytes internal constructor(internal val bytes: ByteArray) : IntoZBytes {

    companion object {
        fun from(string: String) = ZBytes(string.encodeToByteArray())

        fun from(bytes: ByteArray) = ZBytes(bytes)
    }

    fun toBytes(): ByteArray = bytes

    fun tryToString(): Result<String> = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }

    override fun toString(): String = bytes.decodeToString()

    override fun into(): ZBytes = this

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}

internal fun ByteArray.into(): ZBytes {
    return ZBytes(this)
}
