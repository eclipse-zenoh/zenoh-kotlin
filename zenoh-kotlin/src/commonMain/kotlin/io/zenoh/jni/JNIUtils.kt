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

package io.zenoh.jni

import io.zenoh.sample.Attachment

/**
 * Encode attachment as a byte array.
 */
internal fun encodeAttachment(attachment: Attachment): ByteArray {
    return attachment.values.map {
        val key = it.first
        val keyLength = key.size.toByteArray()
        val value = it.second
        val valueLength = value.size.toByteArray()
        keyLength + key + valueLength + value
    }.reduce { acc, bytes -> acc + bytes }
}

/**
 * Decode an attachment as a byte array, recreating the original [Attachment].
 */
internal fun decodeAttachment(attachmentBytes: ByteArray): Attachment {
    var idx = 0
    var sliceSize: Int
    val pairs: MutableList<Pair<ByteArray, ByteArray>> = mutableListOf()
    while (idx < attachmentBytes.size) {
        sliceSize = attachmentBytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)).toInt()
        idx += Int.SIZE_BYTES

        val key = attachmentBytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
        idx += sliceSize

        sliceSize = attachmentBytes.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)).toInt()
        idx += Int.SIZE_BYTES

        val value = attachmentBytes.sliceArray(IntRange(idx, idx + sliceSize - 1))
        idx += sliceSize

        pairs.add(key to value)
    }
    return Attachment(pairs)
}

/**
 * Converts an integer into a byte array with little endian format.
 */
fun Int.toByteArray(): ByteArray {
    val result = ByteArray(UInt.SIZE_BYTES)
    (0 until UInt.SIZE_BYTES).forEach {
        result[it] = this.shr(Byte.SIZE_BITS * it).toByte()
    }
    return result
}

/**
 * To int. The byte array is expected to be in Little Endian format.
 *
 * @return The integer value.
 */
fun ByteArray.toInt(): Int =
    (((this[3].toUInt() and 0xFFu) shl 24) or ((this[2].toUInt() and 0xFFu) shl 16) or ((this[1].toUInt() and 0xFFu) shl 8) or (this[0].toUInt() and 0xFFu)).toInt()
