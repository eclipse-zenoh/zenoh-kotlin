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

import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.into
import io.zenoh.config.ZenohId
import io.zenoh.exceptions.ZError
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIGetCallback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import io.zenoh.query.Parameters
import io.zenoh.query.Reply
import io.zenoh.query.ReplyError
import io.zenoh.sample.Sample
import io.zenoh.sample.SampleKind
import org.apache.commons.net.ntp.TimeStamp

internal class JNIQuerier(val ptr: Long) {

    fun <R> performGet(
        keyExpr: KeyExpr,
        parameters: Parameters?,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R,
        attachment: IntoZBytes?,
        payload: IntoZBytes?,
        encoding: Encoding?
    ): Result<R> = runCatching {
        val getCallback = JNIGetCallback {
                replierId: ByteArray?,
                success: Boolean,
                keyExpr: String?,
                payload: ByteArray,
                encodingId: Int,
                encodingSchema: String?,
                kind: Int,
                timestampNTP64: Long,
                timestampIsValid: Boolean,
                attachmentBytes: ByteArray?,
                express: Boolean,
                priority: Int,
                congestionControl: Int,
            ->
            val reply: Reply
            if (success) {
                val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                val sample = Sample(
                    KeyExpr(keyExpr!!, null),
                    payload.into(),
                    Encoding(encodingId, schema = encodingSchema),
                    SampleKind.fromInt(kind),
                    timestamp,
                    QoS(CongestionControl.fromInt(congestionControl), Priority.fromInt(priority), express),
                    attachmentBytes?.into()
                )
                reply = Reply(replierId?.let { ZenohId(it) }, Result.success(sample))
            } else {
                reply = Reply(
                    replierId?.let { ZenohId(it) }, Result.failure(
                        ReplyError(
                            payload.into(),
                            Encoding(encodingId, schema = encodingSchema)
                        )
                    )
                )
            }
            callback.run(reply)
        }
        getViaJNI(this.ptr,
            keyExpr.jniKeyExpr?.ptr ?: 0,
            keyExpr.keyExpr,
            parameters?.toString(),
            getCallback,
            onClose,
            attachment?.into()?.bytes,
            payload?.into()?.bytes,
            encoding?.id ?: Encoding.default().id,
            encoding?.schema
        )
        receiver
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(ZError::class)
    private external fun getViaJNI(
        querierPtr: Long,
        keyExprPtr: Long,
        keyExprString: String,
        parameters: String?,
        callback: JNIGetCallback,
        onClose: JNIOnCloseCallback,
        attachmentBytes: ByteArray?,
        payload: ByteArray?,
        encodingId: Int,
        encodingSchema: String?,
    )

    private external fun freePtrViaJNI(ptr: Long)
}
