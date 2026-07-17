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

import io.zenoh.exceptions.throwZError0
import io.zenoh.jni.bytes.ZBytes as JniZBytes

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
 * # Native memory lifecycle
 *
 * A ZBytes *created* from user data ([from]) is a plain value — it never
 * holds native memory. A *received* ZBytes (a sample's payload or
 * attachment, a query payload, a reply) wraps a native buffer that is
 * freed automatically on the first [bytes] access (the bytes are copied
 * out lazily, once). A received ZBytes whose content is **never read**
 * keeps its native buffer allocated: unlike every other handle-owning
 * class in this SDK, ZBytes is deliberately NOT covered by the
 * garbage-collection backstop — payloads are the per-message hot path,
 * and registering a GC cleaner per message measured −23% throughput at
 * small payload sizes. In callback-based subscribers/queryables, access
 * (or discard) payloads and attachments you care about; unread ones on
 * dropped samples are the one place native memory can be retained.
 *
 * See also: [ZBytes examples](https://github.com/eclipse-zenoh/zenoh-kotlin/blob/main/examples/src/main/kotlin/io.zenoh/ZBytes.kt)
 */
class ZBytes private constructor(
    private var eager: ByteArray?,
    private var handle: JniZBytes?,
) : IntoZBytes {

    internal constructor(bytes: ByteArray) : this(bytes, null)

    /**
     * The payload bytes. A handle-backed (received) ZBytes materializes them
     * LAZILY on first access — one borrow-copy out of the native buffer —
     * then closes the native handle (forward-extraction rule: the handle is
     * delivered eagerly, the heavy bytes on demand).
     */
    internal val bytes: ByteArray
        get() = eager ?: synchronized(this) {
            eager ?: run {
                val h = handle!!
                val b = h.asBytes(throwZError0)
                eager = b
                handle = null
                h.close()
                b
            }
        }

    companion object {

        /**
         * Creates a [ZBytes] instance from a [String].
         */
        fun from(string: String) = ZBytes(string.encodeToByteArray())

        /**
         * Creates a [ZBytes] instance from a [ByteArray].
         */
        fun from(bytes: ByteArray) = ZBytes(bytes)

        /** Wrap a received owned handle; bytes are read lazily (see [bytes]). */
        internal fun fromHandle(handle: JniZBytes): ZBytes =
            ZBytes(null, handle)
    }

    /**
     * Builds a fresh native `ZBytes` handle from these bytes. The raw
     * payload/attachment parameters take it **by value** (Rust frees it), so
     * the caller does not close it.
     */
    internal fun toZZBytes(): JniZBytes =
        JniZBytes.newFromVec(bytes, throwZError0)

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
