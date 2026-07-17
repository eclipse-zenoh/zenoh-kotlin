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
import io.zenoh.bytes.ZBytes
import io.zenoh.config.EntityGlobalId
import io.zenoh.config.ZenohId
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIGetCallback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.jni.callbacks.JNISubscriberCallback
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.liveliness.LivelinessToken
import io.zenoh.pubsub.Subscriber
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import io.zenoh.query.Reply
import io.zenoh.query.ReplyError
import io.zenoh.sample.Sample
import io.zenoh.sample.SampleKind
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration

internal object JNILiveliness {

    fun <R> get(
        jniSession: JNISession,
        keyExpr: KeyExpr,
        callback: Callback<Reply>,
        receiver: R,
        timeout: Duration,
        onClose: () -> Unit
    ): Result<R> = runCatching {
        val getCallback = JNIGetCallback {
                replierZid: ByteArray?,
                replierEid: Int,
                success: Boolean,
                replyKeyExpr: String?,
                replyPayload: ByteArray,
                encodingId: Int,
                encodingSchema: String?,
                kind: Int,
                timestampNTP64: Long,
                timestampIsValid: Boolean,
                replyAttachment: ByteArray?,
                express: Boolean,
                priority: Int,
                congestionControl: Int,
            ->
            val reply: Reply
            if (success) {
                val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                val sample = Sample(
                    KeyExpr(replyKeyExpr!!, null),
                    ZBytes.from(replyPayload),
                    Encoding(encodingId, schema = encodingSchema),
                    SampleKind.fromInt(kind),
                    timestamp,
                    QoS(CongestionControl.fromInt(congestionControl), Priority.fromInt(priority), express),
                    replyAttachment?.let { ZBytes.from(it) }
                )
                reply = Reply(replierZid?.let { EntityGlobalId(ZenohId(it), replierEid.toUInt()) }, Result.success(sample))
            } else {
                reply = Reply(
                    replierZid?.let { EntityGlobalId(ZenohId(it), replierEid.toUInt()) }, Result.failure(
                        ReplyError(
                            ZBytes.from(replyPayload),
                            Encoding(encodingId, schema = encodingSchema)
                        )
                    )
                )
            }
            callback.run(reply)
        }
        jniSession.livelinessGet(
            keyExpr.jniKeyExpr,
            keyExpr.keyExpr,
            getCallback,
            timeout.toMillis(),
            JNIOnCloseCallback { onClose() }
        )
        receiver
    }

    fun declareToken(jniSession: JNISession, keyExpr: KeyExpr): LivelinessToken {
        return LivelinessToken(jniSession.declareLivelinessToken(keyExpr.jniKeyExpr, keyExpr.keyExpr))
    }

    fun <R> declareSubscriber(
        jniSession: JNISession,
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        receiver: R,
        history: Boolean,
        onClose: () -> Unit
    ): Result<Subscriber<R>> = runCatching {
        val subCallback =
            JNISubscriberCallback { keyExpr2, payload, encodingId, encodingSchema, kind, timestampNTP64, timestampIsValid, attachmentBytes, express: Boolean, priority: Int, congestionControl: Int ->
                val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                val sample = Sample(
                    KeyExpr(keyExpr2, null),
                    ZBytes.from(payload),
                    Encoding(encodingId, schema = encodingSchema),
                    SampleKind.fromInt(kind),
                    timestamp,
                    QoS(CongestionControl.fromInt(congestionControl), Priority.fromInt(priority), express),
                    attachmentBytes?.let { ZBytes.from(it) }
                )
                callback.run(sample)
            }
        val jniSubscriber = jniSession.declareLivelinessSubscriber(
            keyExpr.jniKeyExpr,
            keyExpr.keyExpr,
            subCallback,
            history,
            JNIOnCloseCallback { onClose() }
        )
        Subscriber(keyExpr, receiver, jniSubscriber)
    }
}
