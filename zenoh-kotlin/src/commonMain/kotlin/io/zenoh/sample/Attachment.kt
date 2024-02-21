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

package io.zenoh.sample

/**
 * Attachment
 *
 * An attachment consists of a list of non-unique ordered key value pairs, where keys are UTF-8 Strings and the values are bytes.
 * Inserting at the same key multiple times leads to both values being transmitted for that key.
 *
 * Attachments can be added to a message sent through Zenoh while performing puts, queries and replies.
 *
 * Using attachments will result in performance loss.
 *
 * @property values
 * @constructor Create empty Attachment
 */
class Attachment internal constructor(val values: List<Pair<ByteArray, ByteArray>>) {

    class Builder {

        private val values: MutableList<Pair<ByteArray, ByteArray>> = mutableListOf()

        fun add(key: ByteArray, value: ByteArray) = apply {
            values.add(key to value)
        }

        fun add(key: String, value: ByteArray) = apply {
            values.add(key.toByteArray() to value)
        }

        fun add(key: String, value: String) = apply {
            values.add(key.toByteArray() to value.toByteArray())
        }

        fun addAll(elements: Collection<Pair<ByteArray, ByteArray>>) {
            values.addAll(elements)
        }

        fun addAll(elements: Iterable<Pair<ByteArray, ByteArray>>) {
            values.addAll(elements)
        }

        fun res(): Attachment {
            return Attachment(values)
        }
    }
}
