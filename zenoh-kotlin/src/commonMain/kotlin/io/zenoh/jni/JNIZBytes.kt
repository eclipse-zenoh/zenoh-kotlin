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

import io.zenoh.ZenohLoad
import io.zenoh.bytes.ZBytes
import io.zenoh.bytes.into

internal object JNIZBytes {

    init {
        ZenohLoad
    }

    fun serializeIntoList(list: List<ZBytes>): ZBytes {
        return serializeIntoListViaJNI(list.map { it.bytes }).into()
    }

    fun deserializeIntoList(zbytes: ZBytes): List<ZBytes> {
        return deserializeIntoListViaJNI(zbytes.bytes).map { it.into() }.toList()
    }

    fun serializeIntoMap(map: Map<ZBytes, ZBytes>, keyType: String, valueType: String): ZBytes {
        return serializeIntoMapViaJNI(map.map { (k, v) -> k.bytes to v.bytes }.toMap(), keyType, valueType).into()
    }

    fun deserializeIntoMap(bytes: ZBytes): Map<ZBytes, ZBytes> {
        return deserializeIntoMapViaJNI(bytes.bytes).map { (k, v) -> k.into() to v.into() }.toMap()
    }

    private external fun serializeIntoMapViaJNI(map: Map<ByteArray, ByteArray>, keyType: String, valueType: String): ByteArray

    private external fun serializeIntoListViaJNI(list: List<ByteArray>): ByteArray

    private external fun deserializeIntoMapViaJNI(payload: ByteArray): Map<ByteArray, ByteArray>

    private external fun deserializeIntoListViaJNI(payload: ByteArray): List<ByteArray>
}
