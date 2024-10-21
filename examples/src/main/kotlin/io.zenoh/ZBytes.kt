package io.zenoh

import io.zenoh.ext.zDeserialize
import io.zenoh.ext.zSerialize
import io.zenoh.bytes.ZBytes

fun main() {

    /**
     * # ZBytes
     *
     * A ZBytes instance can be created from a [String] and from a [ByteArray] with the `ZBytes.from(string: String)`
     * and `ZBytes.from(bytes: ByteArray)` functions.
     *
     * A ZBytes can be converted back into a [String] with the functions [ZBytes.toString] and [ZBytes.tryToString].
     * Similarly, with [ZBytes.toBytes] you can get the inner byte representation.
     */

    // String examples
    val inputA = "Example"
    val zbytesA = ZBytes.from(inputA)

    val outputA = zbytesA.toString()
    check(inputA == outputA)

    zbytesA.tryToString().onSuccess {
        check(inputA == it)
    }.onFailure { error ->
        throw error
    }

    // Bytes example
    val inputC = "Example2".toByteArray()
    val zbytesC = ZBytes.from(inputC)
    val outputC = zbytesC.toBytes()
    check(inputC.contentEquals(outputC))

    /**
     * # Serialization and deserialization.
     *
     * Additionally, the Zenoh API provides a series of serialization and deserialization utilities for processing
     * the received payloads.
     *
     * Serialization and deserialization supports the following types:
     * - [Boolean]
     * - [Byte]
     * - [Short]
     * - [Int]
     * - [Long]
     * - [Float]
     * - [Double]
     * - [UByte]
     * - [UShort]
     * - [UInt]
     * - [ULong]
     * - [List]
     * - [String]
     * - [ByteArray]
     * - [Map]
     * - [Pair]
     * - [Triple]
     *
     * For `List`, `Pair` and `Triple`, the inner types can be a combination of the above types, including themselves.
     *
     * These serialization and deserialization utilities can be used across the Zenoh ecosystem with Zenoh
     * versions based on other supported languages such as Rust, Python, C and C++.
     * This works when the types are equivalent (a `Byte` corresponds to an `i8` in Rust, a `Short` to an `i16`, etc).
     *
     */

    /** Boolean example */
    val input1: Boolean = true
    val zbytes1 = zSerialize(input1).getOrThrow()
    val output1 = zDeserialize<Boolean>(zbytes1).getOrThrow()
    check(input1 == output1)

    /** Byte example */
    val input2: Byte = 126.toByte()
    val zbytes2 = zSerialize(input2).getOrThrow()
    val output2 = zDeserialize<Byte>(zbytes2).getOrThrow()
    check(input2 == output2)

    /** Short example */
    val input3: Short = 256.toShort()
    val zbytes3 = zSerialize(input3).getOrThrow()
    val output3 = zDeserialize<Short>(zbytes3).getOrThrow()
    check(input3 == output3)

    /** Int example */
    val input4: Int = 123456
    val zbytes4 = zSerialize(input4).getOrThrow()
    val output4 = zDeserialize<Int>(zbytes4).getOrThrow()
    check(input4 == output4)

    /** Long example */
    val input5: Long = 123456789L
    val zbytes5 = zSerialize(input5).getOrThrow()
    val output5 = zDeserialize<Long>(zbytes5).getOrThrow()
    check(input5 == output5)

    /** Float example */
    val input6: Float = 123.45f
    val zbytes6 = zSerialize(input6).getOrThrow()
    val output6 = zDeserialize<Float>(zbytes6).getOrThrow()
    check(input6 == output6)

    /** Double example */
    val input7: Double = 12345.6789
    val zbytes7 = zSerialize(input7).getOrThrow()
    val output7 = zDeserialize<Double>(zbytes7).getOrThrow()
    check(input7 == output7)

    /** UByte example */
    val input8: UByte = 255.toUByte()
    val zbytes8 = zSerialize(input8).getOrThrow()
    val output8 = zDeserialize<UByte>(zbytes8).getOrThrow()
    check(input8 == output8)

    /** UShort example */
    val input9: UShort = 65535.toUShort()
    val zbytes9 = zSerialize(input9).getOrThrow()
    val output9 = zDeserialize<UShort>(zbytes9).getOrThrow()
    check(input9 == output9)

    /** UInt example */
    val input10: UInt = 123456789u
    val zbytes10 = zSerialize(input10).getOrThrow()
    val output10 = zDeserialize<UInt>(zbytes10).getOrThrow()
    check(input10 == output10)

    /** ULong example */
    val input11: ULong = 1234567890123456789uL
    val zbytes11 = zSerialize(input11).getOrThrow()
    val output11 = zDeserialize<ULong>(zbytes11).getOrThrow()
    check(input11 == output11)

    /** List example */
    val input12: List<Int> = listOf(1, 2, 3, 4, 5)
    val zbytes12 = zSerialize(input12).getOrThrow()
    val output12 = zDeserialize<List<Int>>(zbytes12).getOrThrow()
    check(input12 == output12)

    /** String example */
    val input13: String = "Hello, World!"
    val zbytes13 = zSerialize(input13).getOrThrow()
    val output13 = zDeserialize<String>(zbytes13).getOrThrow()
    check(input13 == output13)

    /** ByteArray example */
    val input14: ByteArray = byteArrayOf(1, 2, 3, 4, 5)
    val zbytes14 = zSerialize(input14).getOrThrow()
    val output14 = zDeserialize<ByteArray>(zbytes14).getOrThrow()
    check(input14.contentEquals(output14))

    /** Map example */
    val input15: Map<String, Int> = mapOf("one" to 1, "two" to 2, "three" to 3)
    val zbytes15 = zSerialize(input15).getOrThrow()
    val output15 = zDeserialize<Map<String, Int>>(zbytes15).getOrThrow()
    check(input15 == output15)

    /** Pair example */
    val input16: Pair<String, Int> = Pair("one", 1)
    val zbytes16 = zSerialize(input16).getOrThrow()
    val output16 = zDeserialize<Pair<String, Int>>(zbytes16).getOrThrow()
    check(input16 == output16)

    /** Triple example */
    val input17: Triple<String, Int, Boolean> = Triple("one", 1, true)
    val zbytes17 = zSerialize(input17).getOrThrow()
    val output17 = zDeserialize<Triple<String, Int, Boolean>>(zbytes17).getOrThrow()
    check(input17 == output17)

    /** Nested List example */
    val input18: List<List<Int>> = listOf(listOf(1, 2, 3))
    val zbytes18 = zSerialize(input18).getOrThrow()
    val output18 = zDeserialize<List<List<Int>>>(zbytes18).getOrThrow()
    check(input18 == output18)

    /** Combined types example */
    val input19: List<Map<String, Int>> = listOf(mapOf("a" to 1, "b" to 2))
    val zbytes19 = zSerialize(input19).getOrThrow()
    val output19 = zDeserialize<List<Map<String, Int>>>(zbytes19).getOrThrow()
    check(input19 == output19)

}
