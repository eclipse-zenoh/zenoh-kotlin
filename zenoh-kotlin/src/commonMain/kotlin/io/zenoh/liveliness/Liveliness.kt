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

class Liveliness internal constructor(private val session: Session) {

    /**
     * Create a LivelinessToken for the given key expression.
     */
    fun declareToken(keyExpr: KeyExpr): Result<LivelinessToken> = runCatching {
        val jniSession = session.jniSession ?: throw Session.sessionClosedException
        JNILiveliness.declareToken(jniSession, keyExpr)
    }

    /**
     * Query liveliness tokens with matching key expressions.
     */
    fun get(
        keyExpr: KeyExpr, callback: Callback<Reply>, timeout: Duration = Duration.ofMillis(10000),
        onClose: (() -> Unit)? = null
    ): Result<Unit> =
        runCatching {
            val jniSession = session.jniSession ?: throw Session.sessionClosedException
            return JNILiveliness.get(jniSession, keyExpr, callback, Unit, timeout,
                fun() { onClose?.invoke() })
        }

    /**
     * Query liveliness tokens with matching key expressions.
     */
    fun <R> get(
        keyExpr: KeyExpr, handler: Handler<Reply, R>, timeout: Duration = Duration.ofMillis(10000),
        onClose: (() -> Unit)? = null
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
                onClose = fun() { onClose?.invoke() })

        }

    /**
     * Query liveliness tokens with matching key expressions.
     */
    fun get(
        keyExpr: KeyExpr,
        channel: Channel<Reply>,
        timeout: Duration = Duration.ofMillis(10000),
        onClose: (() -> Unit)? = null
    ): Result<Channel<Reply>> {
        return session.jniSession?.let {
            val channelHandler = ChannelHandler(channel)
            JNILiveliness.get(it,
                keyExpr,
                channelHandler::handle,
                receiver = channelHandler.receiver(),
                timeout,
                onClose = fun() {
                    channelHandler.onClose()
                    onClose?.invoke()
                })
        } ?: Result.failure(Session.sessionClosedException)
    }

    /**
     * Create a Subscriber for liveliness changes matching the given key expression.
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
     * Create a Subscriber for liveliness changes matching the given key expression.
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
     * Create a Subscriber for liveliness changes matching the given key expression.
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
