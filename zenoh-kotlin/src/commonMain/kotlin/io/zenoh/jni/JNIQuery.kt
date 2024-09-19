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

import io.zenoh.exceptions.ZError
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.QoS
import io.zenoh.protocol.IntoZBytes
import io.zenoh.sample.Sample
import org.apache.commons.net.ntp.TimeStamp

/**
 * Adapter class for interacting with a Query using JNI.
 *
 * This class serves as an adapter for interacting with a Query through JNI (Java Native Interface).
 *
 * @property ptr The raw pointer to the underlying native query.
 */
internal class JNIQuery(private val ptr: Long) {

    fun replySuccess(sample: Sample): Result<Unit> = runCatching {
        val timestampEnabled = sample.timestamp != null
        replySuccessViaJNI(
            ptr,
            sample.keyExpr.jniKeyExpr?.ptr ?: 0,
            sample.keyExpr.keyExpr,
            sample.payload.bytes,
            sample.encoding.id,
            sample.encoding.schema,
            timestampEnabled,
            if (timestampEnabled) sample.timestamp!!.ntpValue() else 0,
            sample.attachment?.bytes,
            sample.qos.express,
            sample.qos.priority.value,
            sample.qos.congestionControl.value
        )
    }

    fun replyError(error: IntoZBytes, encoding: Encoding): Result<Unit> = runCatching {
        replyErrorViaJNI(ptr, error.into().bytes, encoding.id, encoding.schema)
    }

    fun replyDelete(keyExpr: KeyExpr, timestamp: TimeStamp?, attachment: IntoZBytes?, qos: QoS): Result<Unit> =
        runCatching {
            val timestampEnabled = timestamp != null
            replyDeleteViaJNI(
                ptr,
                keyExpr.jniKeyExpr?.ptr ?: 0,
                keyExpr.keyExpr,
                timestampEnabled,
                if (timestampEnabled) timestamp!!.ntpValue() else 0,
                attachment?.into()?.bytes,
                qos.express,
                qos.priority.value,
                qos.congestionControl.value
            )
        }

    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(ZError::class)
    private external fun replySuccessViaJNI(
        queryPtr: Long,
        keyExprPtr: Long,
        keyExprString: String,
        valuePayload: ByteArray,
        valueEncodingId: Int,
        valueEncodingSchema: String?,
        timestampEnabled: Boolean,
        timestampNtp64: Long,
        attachment: ByteArray?,
        qosExpress: Boolean,
        qosPriority: Int,
        qosCongestionControl: Int,
    )

    @Throws(ZError::class)
    private external fun replyErrorViaJNI(
        queryPtr: Long,
        errorValuePayload: ByteArray,
        errorValueEncoding: Int,
        encodingSchema: String?,
    )

    @Throws(ZError::class)
    private external fun replyDeleteViaJNI(
        queryPtr: Long,
        keyExprPtr: Long,
        keyExprString: String,
        timestampEnabled: Boolean,
        timestampNtp64: Long,
        attachment: ByteArray?,
        qosExpress: Boolean,
        qosPriority: Int,
        qosCongestionControl: Int,
    )

    /** Frees the underlying native Query. */
    private external fun freePtrViaJNI(ptr: Long)
}
