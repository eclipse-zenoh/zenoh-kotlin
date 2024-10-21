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
 * ZBytes contains the serialized bytes of user data.
 *
 * It provides convenient methods to the user for serialization/deserialization.
 *
 * **NOTE**
 *
 * Zenoh semantic and protocol take care of sending and receiving bytes
 * without restricting the actual data types. Default (de)serializers are provided for
 * convenience to the users to deal with primitives data types via a simple
 * out-of-the-box encoding. They are NOT by any means the only (de)serializers
 * users can use nor a limitation to the types supported by Zenoh. Users are free and
 * encouraged to use any data format of their choice like JSON, protobuf,
 * flatbuffers, etc.
 *
 * See also: [ZBytes examples](https://github.com/eclipse-zenoh/zenoh-kotlin/blob/main/examples/src/main/kotlin/io.zenoh/ZBytes.kt)
 */
class ZBytes internal constructor(internal val bytes: ByteArray) : IntoZBytes {

    companion object {

        /**
         * Creates a [ZBytes] instance from a [String].
         */
        fun from(string: String) = ZBytes(string.encodeToByteArray())

        /**
         * Creates a [ZBytes] instance from a [ByteArray].
         */
        fun from(bytes: ByteArray) = ZBytes(bytes)
    }

    /** Returns the internal byte representation of the [ZBytes]. */
    fun toBytes(): ByteArray = bytes

    /** Attempts to decode the [ZBytes] into a string with UTF-8 encoding. */
    fun tryToString(): Result<String> =
        runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }

    override fun toString(): String = bytes.decodeToString()

    override fun into(): ZBytes = this

    override fun equals(other: Any?) = other is ZBytes && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}

internal fun ByteArray.into(): ZBytes {
    return ZBytes(this)
}
