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

import io.zenoh.exceptions.ZenohException
import io.zenoh.sample.Sample
import io.zenoh.value.Value

/**
 * Adapter class for interacting with a Query using JNI.
 *
 * This class serves as an adapter for interacting with a Query through JNI (Java Native Interface).
 *
 * @property ptr The raw pointer to the underlying native query.
 */
internal class JNIQuery(private val ptr: Long) {

    @Throws(ZenohException::class)
    fun replySuccess(sample: Sample) {
        val timestampEnabled = sample.timestamp != null
        replySuccessViaJNI(
            ptr,
            sample.keyExpr.jniKeyExpr!!.ptr,
            sample.value.payload,
            sample.value.encoding.knownEncoding.ordinal,
            sample.kind.ordinal,
            timestampEnabled,
            if (timestampEnabled) sample.timestamp!!.ntpValue() else 0,
        )
    }

    @Throws(ZenohException::class)
    fun replyError(errorValue: Value) {
        replyErrorViaJNI(ptr, errorValue.payload, errorValue.encoding.knownEncoding.ordinal)
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(ZenohException::class)
    private external fun replySuccessViaJNI(
        queryPtr: Long,
        keyExpr: Long,
        valuePayload: ByteArray,
        valueEncoding: Int,
        sampleKind: Int,
        timestampEnabled: Boolean,
        timestampNtp64: Long
    )

    @Throws(ZenohException::class)
    private external fun replyErrorViaJNI(
        queryPtr: Long,
        errorValuePayload: ByteArray,
        errorValueEncoding: Int,
    )

    /** Frees the underlying native Query. */
    private external fun freePtrViaJNI(ptr: Long)
}
