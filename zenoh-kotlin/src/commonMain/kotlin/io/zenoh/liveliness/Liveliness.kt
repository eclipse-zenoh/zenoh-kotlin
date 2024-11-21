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

package io.zenoh.liveliness

import io.zenoh.Session
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNILiveliness
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.pubsub.Subscriber
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * A structure with functions to declare a [LivelinessToken],
 * query existing [LivelinessToken]s and subscribe to liveliness changes.
 *
 * A [LivelinessToken] is a token which liveliness is tied
 * to the Zenoh [Session] and can be monitored by remote applications.
 *
 * The [Liveliness] instance can be obtained with the [Session.liveliness] function
 * of the [Session] instance.
 */
class Liveliness internal constructor(private val session: Session) {

    /**
     * Create a LivelinessToken for the given key expression.
     */
    fun declareToken(keyExpr: KeyExpr): Result<LivelinessToken> = runCatching {
        val jniSession = session.jniSession ?: throw Session.sessionClosedException
        JNILiveliness.declareToken(jniSession, keyExpr)
    }

    /**
     * Query the liveliness tokens with matching key expressions.
     *
     * @param keyExpr The [KeyExpr] for the query.
     * @param callback [Callback] to handle the incoming replies.
     * @param timeout Optional timeout of the query, defaults to 10 secs.
     * @return A [Result] with the [Handler]'s receiver.
     */
    fun get(
        keyExpr: KeyExpr, callback: Callback<Reply>, timeout: Duration = Duration.ofMillis(10000)
    ): Result<Unit> =
        runCatching {
            val jniSession = session.jniSession ?: throw Session.sessionClosedException
            return JNILiveliness.get(jniSession, keyExpr, callback, Unit, timeout, {})
        }

    /**
     * Query the liveliness tokens with matching key expressions.
     *
     * @param R The [Handler.receiver] type.
     * @param keyExpr The [KeyExpr] for the query.
     * @param handler [Handler] to deal with the incoming replies.
     * @param timeout Optional timeout of the query, defaults to 10 secs.
     * @return A [Result] with the [Handler]'s receiver.
     */
    fun <R> get(
        keyExpr: KeyExpr, handler: Handler<Reply, R>, timeout: Duration = Duration.ofMillis(10000)
    ): Result<R> =
        runCatching {
            val jniSession = session.jniSession ?: throw Session.sessionClosedException
            val callback = handler::handle
            return JNILiveliness.get(
                jniSession,
                keyExpr,
                callback,
                handler.receiver(),
                timeout,
                onClose = handler::onClose
            )

        }

    /**
     * Query the liveliness tokens with matching key expressions.
     *
     * @param keyExpr The [KeyExpr] for the query.
     * @param channel [Channel] to deal with the incoming replies. The channel will get automatically closed
     *  after the query is performed.
     * @param timeout Optional timeout of the query, defaults to 10 secs.
     * @return A [Result] with the provided [channel].
     */
    fun get(
        keyExpr: KeyExpr,
        channel: Channel<Reply>,
        timeout: Duration = Duration.ofMillis(10000),
    ): Result<Channel<Reply>> {
        return session.jniSession?.let {
            val channelHandler = ChannelHandler(channel)
            JNILiveliness.get(it,
                keyExpr,
                channelHandler::handle,
                receiver = channelHandler.receiver(),
                timeout,
                onClose = channelHandler::onClose
            )
        } ?: Result.failure(Session.sessionClosedException)
    }

    /**
     * Create a [Subscriber] for liveliness changes matching the given key expression.
     *
     * @param keyExpr The [KeyExpr] the subscriber will be listening to.
     * @param callback The [Callback] to be run when a liveliness change is received.
     * @param history Optional parameter to get historical liveliness tokens.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A [Result] with the subscriber.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Subscriber<Unit>> = runCatching {
        val jniSession = session.jniSession ?: throw Session.sessionClosedException
        return JNILiveliness.declareSubscriber(
            jniSession,
            keyExpr,
            callback,
            Unit,
            history,
            fun() { onClose?.invoke() })
    }

    /**
     * Create a [Subscriber] for liveliness changes matching the given key expression.
     *
     * @param R The [Handler.receiver] type.
     * @param keyExpr The [KeyExpr] the subscriber will be listening to.
     * @param handler [Handler] to handle liveliness changes events.
     * @param history Optional parameter to get historical liveliness tokens.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A [Result] with the subscriber.
     */
    fun <R> declareSubscriber(
        keyExpr: KeyExpr,
        handler: Handler<Sample, R>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Subscriber<R>> = runCatching {
        val jniSession = session.jniSession ?: throw Session.sessionClosedException
        return JNILiveliness.declareSubscriber(
            jniSession,
            keyExpr,
            handler::handle,
            handler.receiver(),
            history,
            fun() {
                handler.onClose()
                onClose?.invoke()
            })
    }

    /**
     * Create a [Subscriber] for liveliness changes matching the given key expression.
     *
     * @param keyExpr The [KeyExpr] the subscriber will be listening to.
     * @param channel [Channel] to handle liveliness changes events.
     * @param history Optional parameter to get historical liveliness tokens.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A [Result] with the subscriber.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        channel: Channel<Sample>,
        history: Boolean = false,
        onClose: (() -> Unit)? = null
    ): Result<Subscriber<Channel<Sample>>> = runCatching {
        val jniSession = session.jniSession ?: throw Session.sessionClosedException
        val channelHandler = ChannelHandler(channel)
        return JNILiveliness.declareSubscriber(
            jniSession,
            keyExpr,
            channelHandler::handle,
            channelHandler.receiver(),
            history,
            fun() {
                channelHandler.onClose()
                onClose?.invoke()
            })
    }
}
