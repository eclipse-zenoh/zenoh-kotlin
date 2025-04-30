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
import io.zenoh.handlers.Handler
import io.zenoh.handlers.SampleMissCallback
import io.zenoh.handlers.SampleMissChannelHandler
import io.zenoh.handlers.SampleMissHandler
import io.zenoh.jni.JNIAdvancedSubscriber
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Subscriber
 *
 * A subscriber that allows listening to updates on a key expression and reacting to changes.
 *
 * Simple example using a callback to handle the received samples:
 * ```kotlin
 * val session = Session.open(Config.default()).getOrThrow()
 * val keyexpr = "a/b/c".intoKeyExpr().getOrThrow()
 * session.declareSubscriber(keyexpr, callback = { sample ->
 *     println(">> [Subscriber] Received $sample")
 * })
 * ```
 *
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Subscriber] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the subscriber earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @param R Receiver type of the [Handler] implementation.
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @see Session.declareSubscriber
 */
class AdvancedSubscriber<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R, private var jniSubscriber: JNIAdvancedSubscriber?
) : AutoCloseable, SessionDeclaration {

    /** Declare matching status listener for this publisher with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun declareSampleMissListener(callback: SampleMissCallback,
                                onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        jniSubscriber?.declareSampleMissListener(callback, resolvedOnClose)
    }

    /** Declare matching status listener for this publisher, specifying a handler to handle matching statuses.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the session.
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareSampleMissListener(handler: SampleMissHandler<R>,
                                    onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = SampleMissCallback { miss: SampleMiss -> handler.handle(miss) }
        jniSubscriber?.declareSampleMissListener(callback, resolvedOnClose)
    }

    /** Declare matching status listener for this publisher, specifying a [Channel] to pipe the received matching statuses.
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareSampleMissListener(channel: Channel<SampleMiss>,
                                      onClose: (() -> Unit)? = null,) = {
        val channelHandler = SampleMissChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = SampleMissCallback { miss: SampleMiss -> channelHandler.handle(miss) }
        jniSubscriber?.declareSampleMissListener(callback, resolvedOnClose)
    }






    /** Declare matching status listener for this publisher with callback
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun declareBackgroundSampleMissListener(callback: SampleMissCallback,
                                  onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        jniSubscriber?.declareBackgroundSampleMissListener(callback, resolvedOnClose)
    }

    /** Declare matching status listener for this publisher, specifying a handler to handle matching statuses.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the session.
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareBackgroundSampleMissListener(handler: SampleMissHandler<R>,
                                      onClose: (() -> Unit)? = null,) = {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = SampleMissCallback { miss: SampleMiss -> handler.handle(miss) }
        jniSubscriber?.declareBackgroundSampleMissListener(callback, resolvedOnClose)
    }

    /** Declare matching status listener for this publisher, specifying a [Channel] to pipe the received matching statuses.
     *
     * @param callback: callback to be executed when matching status changes
     * @param onClose: callback to be executed when associated matching listener will be closed
     * */
    fun <R> declareBackgroundSampleMissListener(channel: Channel<SampleMiss>,
                                      onClose: (() -> Unit)? = null,) = {
        val channelHandler = SampleMissChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = SampleMissCallback { miss: SampleMiss -> channelHandler.handle(miss) }
        jniSubscriber?.declareBackgroundSampleMissListener(callback, resolvedOnClose)
    }



    
    /**
     * Returns `true` if the subscriber is still running.
     */
    fun isValid(): Boolean {
        return jniSubscriber != null
    }

    /**
     * Undeclares the subscriber. After calling this function, the subscriber won't be receiving messages anymore.
     */
    override fun undeclare() {
        jniSubscriber?.close()
        jniSubscriber = null
    }

    /**
     * Closes the subscriber. This function is equivalent to [undeclare] and is called automatically when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }
}
