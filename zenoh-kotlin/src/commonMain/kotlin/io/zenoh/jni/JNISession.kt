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

import io.zenoh.*
import io.zenoh.exceptions.SessionException
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.prelude.KnownEncoding
import io.zenoh.jni.callbacks.JNIGetCallback
import io.zenoh.jni.callbacks.JNIQueryableCallback
import io.zenoh.jni.callbacks.JNISubscriberCallback
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.SampleKind
import io.zenoh.prelude.QoS
import io.zenoh.publication.Publisher
import io.zenoh.publication.Put
import io.zenoh.query.*
import io.zenoh.queryable.Query
import io.zenoh.queryable.Queryable
import io.zenoh.sample.Attachment
import io.zenoh.sample.Sample
import io.zenoh.selector.Selector
import io.zenoh.subscriber.Reliability
import io.zenoh.subscriber.Subscriber
import io.zenoh.value.Value
import org.apache.commons.net.ntp.TimeStamp
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/** Adapter class to handle the communication with the Zenoh JNI code for a [Session]. */
internal class JNISession {

    /* Pointer to the underlying Rust zenoh session. */
    private var sessionPtr: AtomicLong = AtomicLong(0)

    fun open(config: Config): Result<Unit> = runCatching {
        config.jsonConfig?.let { jsonConfig ->
            sessionPtr.set(openSessionWithJsonConfigViaJNI(jsonConfig.toString()))
        } ?: run {
            sessionPtr.set(openSessionViaJNI(config.path?.toString().orEmpty()))
        }
    }

    fun close(): Result<Unit> = runCatching {
        closeSessionViaJNI(sessionPtr.get())
    }

    fun declarePublisher(builder: Publisher.Builder): Result<Publisher> = runCatching {
        val publisherRawPtr = declarePublisherViaJNI(
            builder.keyExpr.jniKeyExpr?.ptr ?: 0,
            builder.keyExpr.keyExpr,
            sessionPtr.get(),
            builder.congestionControl.value,
            builder.priority.value,
        )
        Publisher(
            builder.keyExpr,
            JNIPublisher(publisherRawPtr),
            builder.congestionControl,
            builder.priority,
        )
    }

    fun <R> declareSubscriber(
        keyExpr: KeyExpr, callback: Callback<Sample>, onClose: () -> Unit, receiver: R?, reliability: Reliability
    ): Result<Subscriber<R>> = runCatching {
        val subCallback =
            JNISubscriberCallback { keyExpr, payload, encoding, kind, timestampNTP64, timestampIsValid, qos, attachmentBytes ->
                val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                val attachment = attachmentBytes.takeIf { it.isNotEmpty() }?.let { decodeAttachment(it) }
                val sample = Sample(
                    KeyExpr(keyExpr, null),
                    Value(payload, Encoding(KnownEncoding.fromInt(encoding))),
                    SampleKind.fromInt(kind),
                    timestamp,
                    QoS(qos),
                    attachment
                )
                callback.run(sample)
            }
        val subscriberRawPtr = declareSubscriberViaJNI(
            keyExpr.jniKeyExpr?.ptr ?: 0, keyExpr.keyExpr, sessionPtr.get(), subCallback, onClose, reliability.ordinal
        )
        Subscriber(keyExpr, receiver, JNISubscriber(subscriberRawPtr))
    }

    fun <R> declareQueryable(
        keyExpr: KeyExpr, callback: Callback<Query>, onClose: () -> Unit, receiver: R?, complete: Boolean
    ): Result<Queryable<R>> = runCatching {
        val queryCallback =
            JNIQueryableCallback { keyExpr: String, selectorParams: String, withValue: Boolean, payload: ByteArray?, encoding: Int, attachmentBytes: ByteArray, queryPtr: Long ->
                val jniQuery = JNIQuery(queryPtr)
                val keyExpr2 = KeyExpr(keyExpr, null)
                val selector = Selector(keyExpr2, selectorParams)
                val value: Value? = if (withValue) Value(payload!!, Encoding(KnownEncoding.fromInt(encoding))) else null
                val decodedAttachment = attachmentBytes.takeIf { it.isNotEmpty() }?.let { decodeAttachment(it) }
                val query = Query(keyExpr2, selector, value, decodedAttachment, jniQuery)
                callback.run(query)
            }
        val queryableRawPtr =
            declareQueryableViaJNI(keyExpr.jniKeyExpr?.ptr ?: 0, keyExpr.keyExpr, sessionPtr.get(), queryCallback, onClose, complete)
        Queryable(keyExpr, receiver, JNIQueryable(queryableRawPtr))
    }

    fun <R> performGet(
        selector: Selector,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R?,
        timeout: Duration,
        target: QueryTarget,
        consolidation: ConsolidationMode,
        value: Value?,
        attachment: Attachment?
    ): Result<R?> = runCatching {
        val getCallback =
            JNIGetCallback { replierId: String, success: Boolean, keyExpr: String, payload: ByteArray, encoding: Int, kind: Int, timestampNTP64: Long, timestampIsValid: Boolean, qos: Byte, attachmentBytes: ByteArray ->
                if (success) {
                    val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                    val decodedAttachment = attachmentBytes.takeIf { it.isNotEmpty() }?.let { decodeAttachment(it) }
                    val sample = Sample(
                        KeyExpr(keyExpr, null),
                        Value(payload, Encoding(KnownEncoding.fromInt(encoding))),
                        SampleKind.fromInt(kind),
                        timestamp,
                        QoS(qos),
                        decodedAttachment
                    )
                    val reply = Reply.Success(replierId, sample)
                    callback.run(reply)
                } else {
                    val reply = Reply.Error(replierId, Value(payload, Encoding(KnownEncoding.fromInt(encoding))))
                    callback.run(reply)
                }
            }

        if (value == null) {
            getViaJNI(
                selector.keyExpr.jniKeyExpr?.ptr ?: 0,
                selector.keyExpr.keyExpr,
                selector.parameters,
                sessionPtr.get(),
                getCallback,
                onClose,
                timeout.toMillis(),
                target.ordinal,
                consolidation.ordinal,
                attachment?.let { encodeAttachment(it) }
            )
        } else {
            getWithValueViaJNI(
                selector.keyExpr.jniKeyExpr?.ptr ?: 0,
                selector.keyExpr.keyExpr,
                selector.parameters,
                sessionPtr.get(),
                getCallback,
                onClose,
                timeout.toMillis(),
                target.ordinal,
                consolidation.ordinal,
                value.payload,
                value.encoding.knownEncoding.ordinal,
                attachment?.let { encodeAttachment(it) }
            )
        }
        receiver
    }

    fun declareKeyExpr(keyExpr: String): Result<KeyExpr> = runCatching {
        val ptr = declareKeyExprViaJNI(sessionPtr.get(), keyExpr)
        return Result.success(KeyExpr(keyExpr, JNIKeyExpr(ptr)))
    }

    fun undeclareKeyExpr(keyExpr: KeyExpr): Result<Unit> = runCatching {
        keyExpr.jniKeyExpr?.run {
            undeclareKeyExprViaJNI(sessionPtr.get(), this.ptr)
            keyExpr.close()
        } ?: throw SessionException("Attempting to undeclare a non declared key expression.")
    }

    @Throws(Exception::class)
    fun performPut(
        keyExpr: KeyExpr,
        put: Put,
    ) {
        putViaJNI(
            keyExpr.jniKeyExpr?.ptr ?: 0,
            keyExpr.keyExpr,
            sessionPtr.get(),
            put.value.payload,
            put.value.encoding.knownEncoding.ordinal,
            put.congestionControl.value,
            put.priority.value,
            put.kind.ordinal,
            put.attachment?.let { encodeAttachment(it) }
        )
    }
    @Throws(Exception::class)
    private external fun openSessionViaJNI(configFilePath: String): Long

    @Throws(Exception::class)
    private external fun openSessionWithJsonConfigViaJNI(jsonConfig: String): Long

    @Throws(Exception::class)
    private external fun closeSessionViaJNI(ptr: Long)

    @Throws(Exception::class)
    private external fun declarePublisherViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        congestionControl: Int,
        priority: Int
    ): Long

    @Throws(Exception::class)
    private external fun declareSubscriberViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        callback: JNISubscriberCallback,
        onClose: JNIOnCloseCallback,
        reliability: Int
    ): Long

    @Throws(Exception::class)
    private external fun declareQueryableViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        callback: JNIQueryableCallback,
        onClose: JNIOnCloseCallback,
        complete: Boolean
    ): Long

    @Throws(Exception::class)
    private external fun declareKeyExprViaJNI(sessionPtr: Long, keyExpr: String): Long

    @Throws(Exception::class)
    private external fun undeclareKeyExprViaJNI(sessionPtr: Long, keyExprPtr: Long)

    @Throws(Exception::class)
    private external fun getViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        selectorParams: String,
        sessionPtr: Long,
        callback: JNIGetCallback,
        onClose: JNIOnCloseCallback,
        timeoutMs: Long,
        target: Int,
        consolidation: Int,
        attachmentBytes: ByteArray?,
    )

    @Throws(Exception::class)
    private external fun getWithValueViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        selectorParams: String,
        sessionPtr: Long,
        callback: JNIGetCallback,
        onClose: JNIOnCloseCallback,
        timeoutMs: Long,
        target: Int,
        consolidation: Int,
        payload: ByteArray,
        encoding: Int,
        attachmentBytes: ByteArray?
    )

    @Throws(Exception::class)
    private external fun putViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        valuePayload: ByteArray,
        valueEncoding: Int,
        congestionControl: Int,
        priority: Int,
        kind: Int,
        attachmentBytes: ByteArray?
    )
}
