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

package io.zenoh.value

import io.zenoh.prelude.Encoding
import io.zenoh.protocol.Serializable
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into

/**
 * A Zenoh value.
 *
 * A Value is a pair of a binary payload, and a mime-type-like encoding string.
 *
 * @property payload The payload of this Value.
 * @property encoding An encoding description indicating how the associated payload is encoded.
 */
class Value(val payload: ZBytes, val encoding: Encoding) {

    /**
     * Constructs a value with the provided message, using [Encoding.ID.TEXT_PLAIN] for encoding.
     */
    constructor(message: String): this(message.toByteArray().into(), Encoding(Encoding.ID.TEXT_PLAIN))

    /**
     * Constructs a value with the provided message and encoding.
     */
    constructor(message: String, encoding: Encoding): this(message.toByteArray().into(), encoding)

    /**
     * Constructs a value with the provided payload and encoding.
     */
    constructor(payload: ByteArray, encoding: Encoding): this(payload.into(), encoding)

    /**
     * Constructs a value with the provided payload and encoding.
     */
    constructor(payload: Serializable, encoding: Encoding): this(payload.into(), encoding)

    /**
     * Constructs a value with the provided message
     *
     * @param message The message for the value.
     * @param encoding The [Encoding.ID]
     * @param schema Optional [Encoding.schema]
     */
    constructor(message: String, encoding: Encoding.ID, schema: String? = null): this(message.toByteArray().into(), Encoding(encoding, schema))


    /**
     * Constructs a value with the provided [payload]
     *
     * @param payload The payload of the value.
     * @param encoding The [Encoding.ID]
     * @param schema Optional [Encoding.schema]
     */
    constructor(payload: ByteArray, encoding: Encoding.ID, schema: String? = null): this(payload.into(), Encoding(encoding, schema))

    /**
     * Constructs a value with the provided [payload]
     *
     * @param payload The payload of the value.
     * @param encoding The [Encoding.ID]
     * @param schema Optional [Encoding.schema]
     */
    constructor(payload: Serializable, encoding: Encoding.ID, schema: String? = null): this(payload.into(), Encoding(encoding, schema))


    companion object {

        /** Return an empty value. */
        fun empty(): Value {
            return Value(ByteArray(0), Encoding(Encoding.ID.ZENOH_BYTES))
        }
    }

    override fun toString(): String = payload.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Value

        if (payload != other.payload) return false

        return encoding == other.encoding
    }

    override fun hashCode(): Int {
        var result = payload.bytes.hashCode()
        result = 31 * result + encoding.hashCode()
        return result
    }
}
