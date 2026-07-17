//
// Copyright (c) 2025 ZettaScale Technology
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

package io.zenoh.pubsub

import io.zenoh.annotations.Unstable
import io.zenoh.exceptions.ZError
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.handlers.MatchingCallback
import io.zenoh.handlers.MatchingChannelHandler
import io.zenoh.handlers.MatchingHandler
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Advanced Publisher
 *
 * A [Publisher] with advanced features.
 *
 * NOTE (zenoh-flat-transition): advanced pub/sub is not yet exposed by
 * zenoh-flat / zenoh-flat-jni, so instances of this class are never
 * constructed — [io.zenoh.Session.declareAdvancedPublisher] fails first, and
 * every operation on this class returns a failure.
 *
 * @see Publisher
 */
@Unstable
@Suppress("UNUSED_PARAMETER")
class AdvancedPublisher internal constructor(
    val keyExpr: KeyExpr,
    val qos: QoS,
    val encoding: Encoding,
) : SessionDeclaration, AutoCloseable {

    inline fun <reified T> invalidPublisherResult(): Result<T> =
        Result.failure(ZError("AdvancedPublisher is not valid."))

    /** Get the congestion control applied when routing the data. */
    fun congestionControl() = qos.congestionControl

    /** Get the priority of the written data. */
    fun priority() = qos.priority

    /** Declare [MatchingListener] for this publisher with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun declareMatchingListener(callback: MatchingCallback,
                                onClose: (() -> Unit)? = null,): Result<MatchingListener> {
        return invalidPublisherResult()
    }

    /** Declare [MatchingListener] for this publisher, specifying a handler to handle matching statuses.
     *
     * @param handler [MatchingHandler] implementation to handle the matching statuses.
     * [MatchingHandler.onClose] will be called upon closing the associated [AdvancedPublisher] or dropping [MatchingListener].
     * @param onClose: callback to be executed when associated [AdvancedPublisher] is closed or [MatchingListener] is dropped.
     */
    fun <R> declareMatchingListener(handler: MatchingHandler<R>,
                                    onClose: (() -> Unit)? = null,): Result<MatchingListener> {
        return invalidPublisherResult()
    }

    /** Declare [MatchingListener] for this publisher, specifying a [Channel] to pipe the received matching statuses.
     *
     * @param channel [Channel] instance through which the matching statuses will be piped.
     * Once the [AdvancedPublisher] is closed or [MatchingListener] is dropped, the channel is closed as well.
     * @param onClose: callback to be executed when associated [AdvancedPublisher] is closed or [MatchingListener] is dropped
     * */
    fun <R> declareMatchingListener(channel: Channel<Boolean>,
                                    onClose: (() -> Unit)? = null,): Result<MatchingListener> {
        val channelHandler = MatchingChannelHandler(channel)
        channelHandler.onClose()
        return invalidPublisherResult()
    }

    /** Declare background matching status listener for this [AdvancedPublisher] with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated [AdvancedPublisher] will be closed
     * */
    fun declareBackgroundMatchingListener(callback: MatchingCallback,
                                onClose: (() -> Unit)? = null,): Result<Unit> {
        return invalidPublisherResult()
    }

    /** Declare background matching status listener for this [AdvancedPublisher], specifying a handler to handle matching statuses.
     *
     * @param handler [MatchingHandler] implementation to handle the received samples. [MatchingHandler.onClose] will be called upon closing associated [AdvancedPublisher].
     * @param onClose: callback to be executed when associated [AdvancedPublisher] will be closed
     * */
    fun <R> declareBackgroundMatchingListener(handler: MatchingHandler<R>,
                                    onClose: (() -> Unit)? = null,): Result<Unit> {
        return invalidPublisherResult()
    }

    /** Declare background matching status listener for this [AdvancedPublisher], specifying a [Channel] to pipe the received matching statuses.
     *
     * @param channel [Channel] instance through which the matching statuses will be piped.
     * @param onClose: callback to be executed when associated [AdvancedPublisher] will be closed
     * */
    fun <R> declareBackgroundMatchingListener(channel: Channel<Boolean>,
                                    onClose: (() -> Unit)? = null,): Result<Unit> {
        val channelHandler = MatchingChannelHandler(channel)
        channelHandler.onClose()
        return invalidPublisherResult()
    }

    /**
     * Return the matching status of the [AdvancedPublisher].
     *
     * Will return true if there exist Subscribers matching the Publisher's key expression and false otherwise.
     */
    fun getMatchingStatus(): Result<Boolean> {
        return invalidPublisherResult()
    }

    /** Performs a PUT operation on the specified [keyExpr] with the specified [payload]. */
    fun put(payload: IntoZBytes, encoding: Encoding? = null, attachment: IntoZBytes? = null): Result<Unit> {
        return invalidPublisherResult()
    }

    fun put(payload: String, encoding: Encoding? = null, attachment: String? = null) =
        put(ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(attachment) })

    /**
     * Performs a DELETE operation on the specified [keyExpr].
     */
    fun delete(attachment: IntoZBytes? = null): Result<Unit> {
        return invalidPublisherResult()
    }

    fun delete(attachment: String) = delete(ZBytes.from(attachment))

    /**
     * Returns `true` if the publisher is still running.
     */
    fun isValid(): Boolean {
        return false
    }

    /**
     * Closes the publisher. This function is equivalent to [undeclare] and is called automatically when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }

    /**
     * Undeclares the publisher.
     *
     * Further operations performed with the publisher will not be valid anymore.
     */
    override fun undeclare() {
    }
}
