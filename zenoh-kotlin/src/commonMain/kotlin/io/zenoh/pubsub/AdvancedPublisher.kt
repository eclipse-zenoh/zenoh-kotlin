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
import io.zenoh.exceptions.zCall
import io.zenoh.exceptions.zCallUnit
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.jniHandle
import io.zenoh.bytes.jniId
import io.zenoh.bytes.jniSchema
import io.zenoh.bytes.jniSel
import io.zenoh.qos.QoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.handlers.MatchingCallback
import io.zenoh.handlers.MatchingChannelHandler
import io.zenoh.handlers.MatchingHandler
import io.zenoh.jni.VoidCallback
import io.zenoh.jni.boolCallback
import io.zenoh.jni.pubsub.AdvancedPublisher as JniAdvancedPublisher
import io.zenoh.jni.pubsub.MatchingListener as JniMatchingListener
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Advanced Publisher
 *
 * A [Publisher] with advanced features: sample-miss detection, publisher
 * detection so that advanced subscribers can discover it, a retransmission
 * cache, and matching-status notifications.
 *
 * @see Publisher
 * @see io.zenoh.Session.declareAdvancedPublisher
 */
@Unstable
class AdvancedPublisher internal constructor(
    val keyExpr: KeyExpr,
    val qos: QoS,
    val encoding: Encoding,
    private var jniAdvancedPublisher: JniAdvancedPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private fun <T> invalidPublisherResult(): Result<T> =
            Result.failure(ZError("AdvancedPublisher is not valid."))
    }

    /** Get the congestion control applied when routing the data. */
    fun congestionControl() = qos.congestionControl

    /** Get the priority of the written data. */
    fun priority() = qos.priority

    /** Declare a [MatchingListener] for this publisher with a callback.
     *
     * @param callback callback to be executed when the matching status changes.
     * @param onClose callback to be executed when the matching listener is closed.
     */
    fun declareMatchingListener(
        callback: MatchingCallback,
        onClose: (() -> Unit)? = null,
    ): Result<MatchingListener> =
        resolveMatchingListener(callback, onClose ?: {}, background = false)

    /** Declare a [MatchingListener] for this publisher, specifying a handler.
     *
     * @param handler [MatchingHandler] implementation to handle the matching statuses.
     * @param onClose callback to be executed when the matching listener is closed.
     */
    fun <R> declareMatchingListener(
        handler: MatchingHandler<R>,
        onClose: (() -> Unit)? = null,
    ): Result<MatchingListener> =
        resolveMatchingListener(
            MatchingCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = false,
        )

    /** Declare a [MatchingListener] for this publisher, piping statuses to a [Channel].
     *
     * @param channel [Channel] the matching statuses will be piped through.
     * @param onClose callback to be executed when the matching listener is closed.
     */
    fun declareMatchingListener(
        channel: Channel<Boolean>,
        onClose: (() -> Unit)? = null,
    ): Result<MatchingListener> {
        val handler = MatchingChannelHandler(channel)
        return resolveMatchingListener(
            MatchingCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = false,
        )
    }

    /** Declare a background matching status listener for this publisher with a callback.
     *
     * The listener runs in the background until the [AdvancedPublisher] is undeclared.
     */
    fun declareBackgroundMatchingListener(
        callback: MatchingCallback,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> =
        resolveMatchingListener(callback, onClose ?: {}, background = true).map {}

    /** Declare a background matching status listener for this publisher, specifying a handler. */
    fun <R> declareBackgroundMatchingListener(
        handler: MatchingHandler<R>,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> =
        resolveMatchingListener(
            MatchingCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = true,
        ).map {}

    /** Declare a background matching status listener for this publisher, piping statuses to a [Channel]. */
    fun declareBackgroundMatchingListener(
        channel: Channel<Boolean>,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> {
        val handler = MatchingChannelHandler(channel)
        return resolveMatchingListener(
            MatchingCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = true,
        ).map {}
    }

    private fun resolveMatchingListener(
        callback: MatchingCallback,
        onClose: () -> Unit,
        background: Boolean,
    ): Result<MatchingListener> {
        val p = jniAdvancedPublisher ?: return invalidPublisherResult()
        val jniCallback = boolCallback { matching -> callback.run(matching) }
        val jniOnClose = VoidCallback { onClose() }
        return if (background) {
            // Background listeners register no returnable handle — they live
            // until the publisher ends, so the wrapper holds a null handle.
            zCallUnit { onBindingError, onError ->
                p.declareBackgroundMatchingListener(jniCallback, jniOnClose, onBindingError, onError)
            }.map { MatchingListener(null) }
        } else {
            zCall({ JniMatchingListener(0L) }) { onBindingError, onError ->
                p.declareMatchingListener(jniCallback, jniOnClose, onBindingError, onError)
            }.map { MatchingListener(it) }
        }
    }

    /**
     * Return the matching status of the [AdvancedPublisher].
     *
     * Returns `true` if subscribers matching the publisher's key expression exist.
     */
    fun getMatchingStatus(): Result<Boolean> {
        val p = jniAdvancedPublisher ?: return invalidPublisherResult()
        return zCall({ false }) { onBindingError, onError ->
            p.matchingStatus(onBindingError, onError)
        }
    }

    /** Performs a PUT operation on the publisher's [keyExpr] with the specified [payload]. */
    fun put(payload: IntoZBytes, encoding: Encoding? = null, attachment: IntoZBytes? = null): Result<Unit> {
        val p = jniAdvancedPublisher ?: return invalidPublisherResult()
        return zCallUnit { onBindingError, onError ->
            p.put(
                payload.into().bytes,
                encoding.jniSel, encoding.jniId, encoding.jniSchema, encoding.jniHandle,
                attachment?.into()?.bytes,
                onBindingError, onError
            )
        }
    }

    fun put(payload: String, encoding: Encoding? = null, attachment: String? = null) =
        put(ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(attachment) })

    /**
     * Performs a DELETE operation on the publisher's [keyExpr].
     */
    fun delete(attachment: IntoZBytes? = null): Result<Unit> {
        val p = jniAdvancedPublisher ?: return invalidPublisherResult()
        return zCallUnit { onBindingError, onError ->
            p.delete(attachment?.into()?.bytes, onBindingError, onError)
        }
    }

    fun delete(attachment: String) = delete(ZBytes.from(attachment))

    /**
     * Returns `true` if the publisher is still running.
     */
    fun isValid(): Boolean {
        return jniAdvancedPublisher != null
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
        jniAdvancedPublisher?.close()
        jniAdvancedPublisher = null
    }

    protected fun finalize() {
        undeclare()
    }
}
