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

package io.zenoh.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ZBytes(val bytes: ByteArray) : IntoZBytes {

    override fun into(): ZBytes {
        return this
    }

    inline fun <reified T> deserialize(): Result<T> {
        return try {
            val result = when (T::class) {
                String::class -> bytes.decodeToString() as T
                Int::class -> {
                    require(bytes.size == 4) { "Byte array must have exactly 4 bytes to convert to an Int" }
                    ByteBuffer.wrap(bytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int as T
                }

                else -> throw IllegalArgumentException("Unsupported type")
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZBytes

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

}