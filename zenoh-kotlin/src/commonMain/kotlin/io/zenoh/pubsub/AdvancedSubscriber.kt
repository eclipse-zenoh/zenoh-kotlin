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
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.handlers.SampleMissCallback
import io.zenoh.handlers.SampleMissChannelHandler
import io.zenoh.handlers.SampleMissHandler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.sample.Sample
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Advanced Subscriber
 *
 * A [Subscriber] with advanced capabilities.
 *
 * NOTE (zenoh-flat-transition): advanced pub/sub is not yet exposed by
 * zenoh-flat / zenoh-flat-jni, so instances of this class are never
 * constructed — [io.zenoh.Session.declareAdvancedSubscriber] fails first, and
 * every operation on this class returns a failure.
 *
 * @see Subscriber
 */
@Unstable
@Suppress("UNUSED_PARAMETER")
class AdvancedSubscriber<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R
) : AutoCloseable, SessionDeclaration {

    inline fun <reified T> invalidSubscriberResult(): Result<T> =
        Result.failure(ZError("AdvancedSubscriber is not valid."))

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
    ): Result<Subscriber<Unit>> {
        return invalidSubscriberResult()
    }

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
    ): Result<Subscriber<R>> {
        return invalidSubscriberResult()
    }

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
        channelHandler.onClose()
        return invalidSubscriberResult()
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
    ): Result<Unit> {
        return invalidSubscriberResult()
    }

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
    ): Result<Unit> {
        return invalidSubscriberResult()
    }

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
        channelHandler.onClose()
        return invalidSubscriberResult()
    }

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param callback: callback to be executed when missed samples detected
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun declareSampleMissListener(callback: SampleMissCallback,
                                onClose: (() -> Unit)? = null,): Result<SampleMissListener> {
        return invalidSubscriberResult()
    }

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param handler [Handler] implementation to handle the sample miss events. [Handler.onClose] will be called upon closing the [SampleMissListener].
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun <R> declareSampleMissListener(handler: SampleMissHandler<R>,
                                    onClose: (() -> Unit)? = null,): Result<SampleMissListener> {
        return invalidSubscriberResult()
    }

    /** Declares a [SampleMissListener] to detect missed samples for ths [AdvancedSubscriber].
     *
     * Missed samples can only be detected from [AdvancedPublisher] that enable sample miss detection.
     *
     * @param channel [Channel] instance through which the sample miss events will be piped.
     * Once the [SampleMissListener] is closed, the [Channel] is closed as well.
     * @param onClose: callback to be executed when associated [SampleMissListener] will be closed
     * */
    fun <R> declareSampleMissListener(channel: Channel<SampleMiss>,
                                      onClose: (() -> Unit)? = null,): Result<SampleMissListener> {
        val channelHandler = SampleMissChannelHandler(channel)
        channelHandler.onClose()
        return invalidSubscriberResult()
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
    fun declareBackgroundSampleMissListener(callback: SampleMissCallback,
                                  onClose: (() -> Unit)? = null,): Result<Unit> {
        return invalidSubscriberResult()
    }

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
    fun <R> declareBackgroundSampleMissListener(handler: SampleMissHandler<R>,
                                      onClose: (() -> Unit)? = null,): Result<Unit> {
        return invalidSubscriberResult()
    }

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
    fun <R> declareBackgroundSampleMissListener(channel: Channel<SampleMiss>,
                                      onClose: (() -> Unit)? = null,): Result<Unit> {
        val channelHandler = SampleMissChannelHandler(channel)
        channelHandler.onClose()
        return invalidSubscriberResult()
    }

    /**
     * Returns `true` if the subscriber is still running.
     */
    fun isValid(): Boolean {
        return false
    }

    /**
     * Undeclares the subscriber. After calling this function, the subscriber won't be receiving messages anymore.
     */
    override fun undeclare() {
    }

    /**
     * Closes the subscriber. This function is equivalent to [undeclare] and is called automatically when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }
}
