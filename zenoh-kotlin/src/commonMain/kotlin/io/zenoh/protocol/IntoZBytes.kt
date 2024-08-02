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

interface IntoZBytes {
    fun into(): ZBytes
}

interface Serializable: IntoZBytes {
    override fun into(): ZBytes
}

interface Deserializable {
    interface From {
        fun from(zbytes: ZBytes): Serializable
    }
}

fun Number.into(): ZBytes {
    return ZBytes.from(this)
}

fun String.into(): ZBytes {
    return ZBytes.from(this)
}

fun ByteArray.into(): ZBytes {
    return ZBytes(this)
}

fun ZBytes.toByte(): Byte {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).get()
}

fun ZBytes.toShort(): Short {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).short
}

fun ZBytes.toInt(): Int {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).int
}

fun ZBytes.toLong(): Long {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).long
}

fun ZBytes.toFloat(): Float {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).float
}

fun ZBytes.toDouble(): Double {
    return ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN).double
}

@Throws
fun Any?.into(): ZBytes {
    return when (this) {
        is String -> this.into()
        is Number -> this.into()
        is ByteArray -> this.into()
        is Serializable -> this.into()
        else -> throw IllegalArgumentException("Unsupported type")
    }
}
