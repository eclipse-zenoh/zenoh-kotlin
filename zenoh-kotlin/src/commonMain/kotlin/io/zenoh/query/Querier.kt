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

import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes
import io.zenoh.exceptions.ZError
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQuerier
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.qos.QoS
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

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
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            handler::handle,
            handler::onClose,
            handler.receiver(),
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

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
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            callback,
            {},
            Unit,
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

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
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            handler::handle,
            handler::onClose,
            handler.receiver(),
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

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

}
