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

package io.zenoh.query

import io.zenoh.annotations.Unstable
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.config.EntityGlobalId
import io.zenoh.config.ZenohId
import io.zenoh.exceptions.ZError
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQuerier
import io.zenoh.jni.callbacks.JNIGetCallback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import io.zenoh.sample.Sample
import io.zenoh.sample.SampleKind
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel
import org.apache.commons.net.ntp.TimeStamp

/**
 * A querier that allows to send queries to a [Queryable].
 *
 * Queriers are automatically undeclared when dropped.
 *
 * Example:
 * ```kotlin
 * val session = Zenoh.open(config).getOrThrow();
 * val keyExpr = "a/b/c".intoKeyExpr().getOrThrow();
 *
 * val querier = session.declareQuerier(keyExpr).getOrThrow();
 * querier.get(callback = {
 *         it.result.onSuccess { sample ->
 *             println(">> Received ('${sample.keyExpr}': '${sample.payload}')")
 *         }.onFailure { error ->
 *             println(">> Received (ERROR: '${error.message}')")
 *         }
 *     }
 * )
 * ```
 *
 */
class Querier internal constructor(val keyExpr: KeyExpr, val qos: QoS, private var jniQuerier: JNIQuerier?) :
    SessionDeclaration, AutoCloseable {

    /**
     * Perform a get operation to the [keyExpr] from the Querier and pipe the incoming
     * replies into the [channel] provided.
     *
     * @param channel The [Channel] that will receive the replies.
     * @param parameters Optional [Parameters] for the query.
     * @param payload Optional payload for the query.
     * @param encoding Optional encoding for the payload of the query.
     * @param attachment Optional attachment for the query.
     * @return A result with the provided channel.
     */
    fun get(
        channel: Channel<Reply>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<Channel<Reply>> {
        val handler = ChannelHandler(channel)
        return performGet(parameters, handler::handle, handler::onClose, handler.receiver(), attachment, payload, encoding)
    }

    fun get(
        channel: Channel<Reply>,
        parameters: Parameters? = null,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null
    ): Result<Channel<Reply>> = get(channel, parameters, ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(it) })

    /**
     * Perform a get operation to the [keyExpr] from the Querier and handle the incoming replies
     * with the [callback] provided.
     *
     * @param callback [Callback] to be run upon receiving a [Reply] to the query.
     * @param parameters Optional [Parameters] for the query.
     * @param payload Optional payload for the query.
     * @param encoding Optional encoding for the payload of the query.
     * @param attachment Optional attachment for the query.
     * @return A result with the status of the operation.
     */
    fun get(
        callback: Callback<Reply>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        return performGet(parameters, callback, {}, Unit, attachment, payload, encoding)
    }

    fun get(
        callback: Callback<Reply>,
        parameters: Parameters? = null,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null
    ): Result<Unit> = get(callback, parameters, ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(it) })

    /**
     * Perform a get operation to the [keyExpr] from the Querier and handle the incoming replies
     * with the [handler] provided.
     *
     * @param handler [Handler] to handle the receiving replies to the query.
     * @param parameters Optional [Parameters] for the query.
     * @param payload Optional payload for the query.
     * @param encoding Optional encoding for the payload of the query.
     * @param attachment Optional attachment for the query.
     * @return A result with the status of the operation.
     */
    fun <R> get(
        handler: Handler<Reply, R>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<R> {
        return performGet(parameters, handler::handle, handler::onClose, handler.receiver(), attachment, payload, encoding)
    }

    fun <R> get(
        handler: Handler<Reply, R>,
        parameters: Parameters? = null,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null
    ): Result<R> = get(handler, parameters, ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(it) })

    /**
     * Get the [QoS.congestionControl] of the querier.
     */
    fun congestionControl() = qos.congestionControl

    /**
     * Get the [QoS.priority] of the querier.
     */
    fun priority() = qos.priority

    /**
     * Undeclares the querier. After calling this function, the querier won't be valid anymore and get operations
     * performed on it will fail.
     */
    override fun undeclare() {
        jniQuerier?.close()
        jniQuerier = null
    }

    /**
     * Closes the querier. Equivalent to [undeclare], this function is automatically called when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }

    protected fun finalize() {
        undeclare()
    }

    private fun <R> performGet(
        parameters: Parameters?,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R,
        attachment: IntoZBytes?,
        payload: IntoZBytes?,
        encoding: Encoding?
    ): Result<R> {
        return jniQuerier?.run {
            runCatching {
                val jniCallback = JNIGetCallback { replierZid, replierEid, success, replyKeyExpr, replyPayload, encodingId, encodingSchema, kind, timestampNTP64, timestampIsValid, replyAttachment, express, priority, congestionControl ->
                    val reply = if (success) {
                        val timestamp = if (timestampIsValid) TimeStamp(timestampNTP64) else null
                        Reply(
                            replierZid?.let { EntityGlobalId(ZenohId(it), replierEid.toUInt()) },
                            Result.success(Sample(
                                KeyExpr(replyKeyExpr!!), ZBytes.from(replyPayload),
                                Encoding(encodingId, schema = encodingSchema),
                                SampleKind.fromInt(kind), timestamp,
                                QoS(CongestionControl.fromInt(congestionControl), Priority.fromInt(priority), express),
                                replyAttachment?.let { ZBytes.from(it) }
                            ))
                        )
                    } else {
                        Reply(
                            replierZid?.let { EntityGlobalId(ZenohId(it), replierEid.toUInt()) },
                            Result.failure(ReplyError(ZBytes.from(replyPayload), Encoding(encodingId, schema = encodingSchema)))
                        )
                    }
                    callback.run(reply)
                }
                val resolvedEncoding = encoding ?: Encoding.default()
                get(
                    keyExpr.jniKeyExpr, keyExpr.keyExpr, parameters?.toString(),
                    jniCallback, JNIOnCloseCallback { onClose() },
                    attachment?.into()?.bytes, payload?.into()?.bytes,
                    resolvedEncoding.id, resolvedEncoding.schema
                )
                receiver
            }
        } ?: throw ZError("Querier is not valid.")
    }
}
