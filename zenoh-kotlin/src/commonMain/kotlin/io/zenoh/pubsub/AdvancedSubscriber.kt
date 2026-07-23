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
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.handlers.SampleMissCallback
import io.zenoh.handlers.SampleMissChannelHandler
import io.zenoh.handlers.SampleMissHandler
import io.zenoh.jni.VoidCallback
import io.zenoh.jni.pubsub.AdvancedSubscriber as JniAdvancedSubscriber
import io.zenoh.jni.pubsub.SampleMissListener as JniSampleMissListener
import io.zenoh.jni.pubsub.Subscriber as JniSubscriber
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.sample.Sample
import io.zenoh.sampleCallbackOf
import io.zenoh.sampleMissCallbackOf
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Advanced Subscriber
 *
 * A [Subscriber] with advanced capabilities: it can query historical data,
 * recover missed samples, detect the [AdvancedPublisher]s matching its key
 * expression, and report missed samples via a [SampleMissListener].
 *
 * A background detect-publishers subscriber or sample-miss listener (declared
 * via the `declareBackground*` methods) has no returnable handle — it lives
 * until this [AdvancedSubscriber] is undeclared.
 *
 * @see Subscriber
 * @see io.zenoh.Session.declareAdvancedSubscriber
 */
@Unstable
class AdvancedSubscriber<R> internal constructor(
    val keyExpr: KeyExpr,
    val receiver: R,
    private var jniAdvancedSubscriber: JniAdvancedSubscriber?,
) : AutoCloseable, SessionDeclaration {

    companion object {
        private fun <T> invalidSubscriberResult(): Result<T> =
            Result.failure(ZError("AdvancedSubscriber is not valid."))
    }

    /** Declares a subscriber to detect matching publishers.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param callback: callback to be executed when matching publisher added or removed
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated detect publishers subscriber will be closed
     * */
    fun declareDetectPublishersSubscriber(
        callback: Callback<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Subscriber<Unit>> =
        resolveDetectPublishersSubscriber(callback, history, onClose ?: {}, background = false, receiver = Unit)

    /** Declares a subscriber to detect matching publishers.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the [Subscriber].
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated detect publishers subscriber will be closed
     * */
    fun <R> declareDetectPublishersSubscriber(
        handler: Handler<Sample, R>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null,
        receiver: R
    ): Result<Subscriber<R>> =
        resolveDetectPublishersSubscriber(
            Callback { handler.handle(it) },
            history,
            { handler.onClose(); onClose?.invoke() },
            background = false,
            receiver = receiver,
        )

    /** Declares a subscriber to detect matching publishers.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param channel [Channel] instance through which the received samples will be piped. Once the [Subscriber] is
     *  closed, the channel is closed as well.
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated detect publishers subscriber will be closed
     * */
    fun <R> declareDetectPublishersSubscriber(
        channel: Channel<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null,
    ): Result<Subscriber<Channel<Sample>>> {
        val channelHandler = ChannelHandler(channel)
        return resolveDetectPublishersSubscriber(
            Callback { channelHandler.handle(it) },
            history,
            { channelHandler.onClose(); onClose?.invoke() },
            background = false,
            receiver = channelHandler.receiver(),
        )
    }

    /** Declares a background subscriber to detect matching publishers.
     *
     * Register the listener callback to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param callback: callback to be executed when matching publisher added or removed
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun declareBackgroundDetectPublishersSubscriber(
        callback: Callback<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Unit> =
        resolveDetectPublishersSubscriber(callback, history, onClose ?: {}, background = true, receiver = Unit).map {}

    /** Declares a background subscriber to detect matching publishers.
     *
     * Register the handler to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called upon closing the [AdvancedSubscriber].
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun <R> declareBackgroundDetectPublishersSubscriber(
        handler: Handler<Sample, R>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Unit> =
        resolveDetectPublishersSubscriber(
            Callback { handler.handle(it) },
            history,
            { handler.onClose(); onClose?.invoke() },
            background = true,
            receiver = handler.receiver(),
        ).map {}

    /** Declares a background subscriber to detect matching publishers.
     *
     * Register the channel to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Only [AdvancedPublisher] that enable publisher detection can be detected.
     *
     * @param channel [Channel] instance through which the received samples will be piped. Once the [AdvancedSubscriber] is
     *  closed, the channel is closed as well.
     * @param history: check already existing publishers
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun declareBackgroundDetectPublishersSubscriber(
        channel: Channel<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> {
        val channelHandler = ChannelHandler(channel)
        return resolveDetectPublishersSubscriber(
            Callback { channelHandler.handle(it) },
            history,
            { channelHandler.onClose(); onClose?.invoke() },
            background = true,
            receiver = channelHandler.receiver(),
        ).map {}
    }

    private fun <R2> resolveDetectPublishersSubscriber(
        callback: Callback<Sample>,
        history: Boolean,
        onClose: () -> Unit,
        background: Boolean,
        receiver: R2,
    ): Result<Subscriber<R2>> {
        val s = jniAdvancedSubscriber ?: return invalidSubscriberResult()
        val jniCallback = sampleCallbackOf { callback.run(it) }
        val jniOnClose = VoidCallback { onClose() }
        return if (background) {
            // Background subscribers register no returnable handle — they live
            // until the advanced subscriber ends, so the wrapper holds null.
            zCallUnit { onBindingError, onError ->
                s.declareBackgroundDetectPublishersSubscriber(jniCallback, jniOnClose, history, onBindingError, onError)
            }.map { Subscriber(keyExpr, receiver, null) }
        } else {
            zCall({ JniSubscriber(0L) }) { onBindingError, onError ->
                s.declareDetectPublishersSubscriber(jniCallback, jniOnClose, history, onBindingError, onError)
            }.map { Subscriber(keyExpr, receiver, it) }
        }
    }

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param callback: callback to be executed when missed samples detected
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun declareSampleMissListener(
        callback: SampleMissCallback,
        onClose: (() -> Unit)? = null,
    ): Result<SampleMissListener> =
        resolveSampleMissListener(callback, onClose ?: {}, background = false)

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param handler [Handler] implementation to handle the sample miss events. [Handler.onClose] will be called upon closing the [SampleMissListener].
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun <R> declareSampleMissListener(
        handler: SampleMissHandler<R>,
        onClose: (() -> Unit)? = null,
    ): Result<SampleMissListener> =
        resolveSampleMissListener(
            SampleMissCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = false,
        )

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param channel [Channel] instance through which the sample miss events will be piped.
     * Once the [SampleMissListener] is closed, the [Channel] is closed as well.
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun <R> declareSampleMissListener(
        channel: Channel<SampleMiss>,
        onClose: (() -> Unit)? = null,
    ): Result<SampleMissListener> {
        val channelHandler = SampleMissChannelHandler(channel)
        return resolveSampleMissListener(
            SampleMissCallback { channelHandler.handle(it) },
            { channelHandler.onClose(); onClose?.invoke() },
            background = false,
        )
    }

    /** Declares a background sample miss listener to detect missed samples for ths [AdvancedSubscriber].
     *
     * Register the listener callback to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param callback: callback to be executed when missed samples detected
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun declareBackgroundSampleMissListener(
        callback: SampleMissCallback,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> =
        resolveSampleMissListener(callback, onClose ?: {}, background = true).map {}

    /** Declares a background sample miss listener to detect missed samples for ths [AdvancedSubscriber].
     *
     * Register the handler to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param handler [Handler] implementation to handle the sample miss events.
     * [Handler.onClose] will be called upon closing the [AdvancedSubscriber].
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun <R> declareBackgroundSampleMissListener(
        handler: SampleMissHandler<R>,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> =
        resolveSampleMissListener(
            SampleMissCallback { handler.handle(it) },
            { handler.onClose(); onClose?.invoke() },
            background = true,
        ).map {}

    /** Declares a background sample miss listener to detect missed samples for ths [AdvancedSubscriber].
     *
     * Register the channel to be run in background until the [AdvancedSubscriber] is undeclared.
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param channel [Channel] instance through which the sample miss events will be piped.
     * Once the [AdvancedSubscriber] is closed, the [Channel] is closed as well.
     * @param onClose: callback to be executed when associated [AdvancedSubscriber] will be closed
     * */
    fun <R> declareBackgroundSampleMissListener(
        channel: Channel<SampleMiss>,
        onClose: (() -> Unit)? = null,
    ): Result<Unit> {
        val channelHandler = SampleMissChannelHandler(channel)
        return resolveSampleMissListener(
            SampleMissCallback { channelHandler.handle(it) },
            { channelHandler.onClose(); onClose?.invoke() },
            background = true,
        ).map {}
    }

    private fun resolveSampleMissListener(
        callback: SampleMissCallback,
        onClose: () -> Unit,
        background: Boolean,
    ): Result<SampleMissListener> {
        val s = jniAdvancedSubscriber ?: return invalidSubscriberResult()
        val jniCallback = sampleMissCallbackOf { callback.run(it) }
        val jniOnClose = VoidCallback { onClose() }
        return if (background) {
            zCallUnit { onBindingError, onError ->
                s.declareBackgroundSampleMissListener(jniCallback, jniOnClose, onBindingError, onError)
            }.map { SampleMissListener(null) }
        } else {
            zCall({ JniSampleMissListener(0L) }) { onBindingError, onError ->
                s.declareSampleMissListener(jniCallback, jniOnClose, onBindingError, onError)
            }.map { SampleMissListener(it) }
        }
    }

    /**
     * Returns `true` if the subscriber is still running.
     */
    fun isValid(): Boolean {
        return jniAdvancedSubscriber != null
    }

    /**
     * Undeclares the subscriber. After calling this function, the subscriber won't be receiving messages anymore.
     */
    override fun undeclare() {
        jniAdvancedSubscriber?.close()
        jniAdvancedSubscriber = null
    }

    /**
     * Closes the subscriber. This function is equivalent to [undeclare] and is called automatically when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }
}
