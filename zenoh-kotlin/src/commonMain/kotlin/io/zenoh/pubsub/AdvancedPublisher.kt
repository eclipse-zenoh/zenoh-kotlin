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

package io.zenoh.pubsub

import io.zenoh.*
import io.zenoh.Session.Companion.sessionClosedException
import io.zenoh.exceptions.ZError
import io.zenoh.jni.JNIAdvancedPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.handlers.MatchingCallback
import io.zenoh.handlers.MatchingChannelHandler
import io.zenoh.handlers.MatchingHandler
import io.zenoh.qos.Reliability
import io.zenoh.sample.Sample
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Publisher
 *
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * Example of a publisher declaration:
 * ```kotlin
 * val keyExpr = "demo/kotlin/greeting".intoKeyExpr().getOrThrow()
 * Zenoh.open(Config.default()).onSuccess {
 *     it.use { session ->
 *         session
 *             .declarePublisher(keyExpr)
 *             .onSuccess { pub ->
 *                 var i = 0
 *                 while (true) {
 *                     pub.put(ZBytes.from("Hello for the ${i}th time!"))
 *                     Thread.sleep(1000)
 *                     i++
 *                 }
 *             }
 *     }
 * }
 * ```
 *
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Publisher] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the publisher earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @property keyExpr The key expression the publisher will be associated to.
 * @property qos [QoS] configuration of the publisher.
 * @property encoding Default [Encoding] of the data to be published. A different encoding can be later provided when performing
 *  a `put` operation.
 * @see Session.declarePublisher
 */
class AdvancedPublisher internal constructor(
    val keyExpr: KeyExpr,
    val qos: QoS,
    val encoding: Encoding,
    private var jniPublisher: JNIAdvancedPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val InvalidPublisherResult = Result.failure<Unit>(ZError("Publisher is not valid."))
    }

    /** Get the congestion control applied when routing the data. */
    fun congestionControl() = qos.congestionControl

    /** Get the priority of the written data. */
    fun priority() = qos.priority

    /** Declare matching status listener for this publisher with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun declareMatchingListener(callback: MatchingCallback,
                                onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        jniPublisher?.declareMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /** Declare matching status listener for this publisher, specifying a handler to handle matching statuses.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the session.
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareMatchingListener(handler: MatchingHandler<R>,
                                    onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = MatchingCallback { matching: Boolean -> handler.handle(matching) }
        jniPublisher?.declareMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /** Declare matching status listener for this publisher, specifying a [Channel] to pipe the received matching statuses.
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareMatchingListener(channel: Channel<Boolean>,
                                    onClose: (() -> Unit)? = null,) = {
        val channelHandler = MatchingChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = MatchingCallback { matching: Boolean -> channelHandler.handle(matching) }
        jniPublisher?.declareMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /** Declare background matching status listener for this publisher with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun declareBackgroundMatchingListener(callback: MatchingCallback,
                                onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        jniPublisher?.declareBackgroundMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /** Declare matching background status listener for this publisher, specifying a handler to handle matching statuses.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the session.
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareBackgroundMatchingListener(handler: MatchingHandler<R>,
                                    onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = MatchingCallback { matching: Boolean -> handler.handle(matching) }
        jniPublisher?.declareBackgroundMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /** Declare background matching status listener for this publisher, specifying a [Channel] to pipe the received matching statuses.
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareBackgroundMatchingListener(channel: Channel<Boolean>,
                                    onClose: (() -> Unit)? = null,) = {
        val channelHandler = MatchingChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = MatchingCallback { matching: Boolean -> channelHandler.handle(matching) }
        jniPublisher?.declareBackgroundMatchingListener(callback, resolvedOnClose)?: InvalidPublisherResult
    }

    /**
     * Return the matching status of the publisher.
     *
     * Will return true if there exist Subscribers matching the Publisher's key expression and false otherwise.
     */
    fun getMatchingStatus() = jniPublisher?.getMatchingStatus() ?: InvalidPublisherResult

    /** Performs a PUT operation on the specified [keyExpr] with the specified [payload]. */
    fun put(payload: IntoZBytes, encoding: Encoding? = null, attachment: IntoZBytes? = null) =
        jniPublisher?.put(payload, encoding ?: this.encoding, attachment) ?: InvalidPublisherResult

    fun put(payload: String, encoding: Encoding? = null, attachment: String? = null) =
        put(ZBytes.from(payload), encoding, attachment?.let { ZBytes.from(attachment) })

    /**
     * Performs a DELETE operation on the specified [keyExpr].
     */
    fun delete(attachment: IntoZBytes? = null) = jniPublisher?.delete(attachment) ?: InvalidPublisherResult

    fun delete(attachment: String) = delete(ZBytes.from(attachment))

    /**
     * Returns `true` if the publisher is still running.
     */
    fun isValid(): Boolean {
        return jniPublisher != null
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
        jniPublisher?.close()
        jniPublisher = null
    }

    protected fun finalize() {
        undeclare()
    }
}
