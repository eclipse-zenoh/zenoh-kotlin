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
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.Encoding.ID
import io.zenoh.exceptions.SessionException
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.jni.callbacks.JNIGetCallback
import io.zenoh.jni.callbacks.JNIQueryableCallback
import io.zenoh.jni.callbacks.JNISubscriberCallback
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.*
import io.zenoh.publication.Delete
import io.zenoh.publication.Publisher
import io.zenoh.publication.Put
import io.zenoh.query.*
import io.zenoh.queryable.Query
import io.zenoh.queryable.Queryable
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

    fun declarePublisher(keyExpr: KeyExpr, qos: QoS): Result<Publisher> = runCatching {
        val publisherRawPtr = declarePublisherViaJNI(
            keyExpr.jniKeyExpr?.ptr ?: 0,
            keyExpr.keyExpr,
            sessionPtr.get(),
            qos.congestionControl.value,
            qos.priority.value,
            qos.express
        )
        Publisher(
            keyExpr,
            qos,
            JNIPublisher(publisherRawPtr),
        )
    }

    fun <R> declareSubscriber(
        keyExpr: KeyExpr, callback: Callback<Sample>, onClose: () -> Unit, receiver: R?, reliability: Reliability
    ): Result<Subscriber<R>> = runCatching {
        val subCallback =
            JNISubscriberCallback { keyExpr, payload, encodingId, encodingSchema, kind, timestampNTP64, timestampIsValid, attachmentBytes, express: Boolean, priority: Int, congestionControl: Int ->
                val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                val sample = Sample(
                    KeyExpr(keyExpr, null),
                    Value(payload, Encoding(ID.fromId(encodingId)!!, encodingSchema)),
                    SampleKind.fromInt(kind),
                    timestamp,
                    QoS(express, congestionControl, priority),
                    attachmentBytes
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
            JNIQueryableCallback { keyExpr: String, selectorParams: String, withValue: Boolean, payload: ByteArray?, encodingId: Int, encodingSchema: String?, attachmentBytes: ByteArray?, queryPtr: Long ->
                val jniQuery = JNIQuery(queryPtr)
                val keyExpr2 = KeyExpr(keyExpr, null)
                val selector = Selector(keyExpr2, selectorParams)
                val value: Value? =
                    if (withValue) Value(payload!!, Encoding(ID.fromId(encodingId)!!, encodingSchema)) else null
                val query = Query(keyExpr2, selector, value, attachmentBytes, jniQuery)
                callback.run(query)
            }
        val queryableRawPtr = declareQueryableViaJNI(
            keyExpr.jniKeyExpr?.ptr ?: 0, keyExpr.keyExpr, sessionPtr.get(), queryCallback, onClose, complete
        )
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
        attachment: ByteArray?
    ): Result<R?> = runCatching {
        val getCallback = JNIGetCallback {
                replierId: String,
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
                when (SampleKind.fromInt(kind)) {
                    SampleKind.PUT -> {
                        val sample = Sample(
                            KeyExpr(keyExpr!!, null),
                            Value(payload, Encoding(ID.fromId(encodingId)!!, encodingSchema)),
                            SampleKind.fromInt(kind),
                            timestamp,
                            QoS(express, congestionControl, priority),
                            attachmentBytes
                        )
                        reply = Reply.Success(replierId, sample)
                    }

                    SampleKind.DELETE -> {
                        reply = Reply.Delete(
                            replierId,
                            KeyExpr(keyExpr!!, null),
                            timestamp,
                            attachmentBytes,
                            QoS(express, congestionControl, priority)
                        )
                    }
                }
            } else {
                reply = Reply.Error(replierId, Value(payload, Encoding(ID.fromId(encodingId)!!, encodingSchema)))
            }
            callback.run(reply)
        }

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
            attachment,
            value != null,
            value?.payload,
            value?.encoding?.id?.ordinal ?: 0,
            value?.encoding?.schema
        )
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
            put.value.encoding.id.ordinal,
            put.value.encoding.schema,
            put.qos.congestionControl.value,
            put.qos.priority.value,
            put.qos.express,
            put.attachment
        )
    }

    @Throws(Exception::class)
    fun performDelete(
        keyExpr: KeyExpr,
        delete: Delete,
    ) {
        deleteViaJNI(
            keyExpr.jniKeyExpr?.ptr ?: 0,
            keyExpr.keyExpr,
            sessionPtr.get(),
            delete.qos.congestionControl.value,
            delete.qos.priority.value,
            delete.qos.express,
            delete.attachment
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
        priority: Int,
        express: Boolean
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
        withValue: Boolean,
        payload: ByteArray?,
        encodingId: Int,
        encodingSchema: String?,
    )

    @Throws(Exception::class)
    private external fun putViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        valuePayload: ByteArray,
        valueEncoding: Int,
        valueEncodingSchema: String?,
        congestionControl: Int,
        priority: Int,
        express: Boolean,
        attachmentBytes: ByteArray?
    )

    @Throws(Exception::class)
    private external fun deleteViaJNI(
        keyExprPtr: Long,
        keyExprString: String,
        sessionPtr: Long,
        congestionControl: Int,
        priority: Int,
        express: Boolean,
        attachmentBytes: ByteArray?
    )
}
