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

import io.zenoh.handlers.Callback
import io.zenoh.Session
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.value.Value
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * Get to query data from the matching queryables in the system.
 *
 * Example with a [Callback]:
 * ```
 * println("Opening Session")
 * Session.open().onSuccess { session -> session.use {
 *     "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
 *         session.get(keyExpr)
 *             .consolidation(ConsolidationMode.NONE)
 *             .target(QueryTarget.BEST_MATCHING)
 *             .withValue("Get value example")
 *             .with { reply -> println("Received reply $reply") }
 *             .timeout(Duration.ofMillis(1000))
 *             .res()
 *             .onSuccess {...}
 *         }
 *     }
 * }
 * ```
 *
 * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, R will be [Unit].
 * @property keyExpr The [KeyExpr] upon which the get operation will be performed.
 * @property receiver The receiver, of the handler, if no handler is specified then the receiver is set as null and
 * [R] is set as [Unit].
 * @constructor Internal constructor. A Get operation must be created through the [Builder] obtained after calling
 * [Session.get] or alternatively through [newBuilder].
 */
class Get<R> internal constructor(val keyExpr: KeyExpr, val receiver: R?) {

    companion object {
        /**
         * Creates a bew [Builder] associated to the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the query will be triggered.
         * @param keyExpr The [KeyExpr] upon which the query will be performed.
         * @return A [Builder] with a default [ChannelHandler] to handle any incoming [Reply].
         */
        fun newBuilder(session: Session, keyExpr: KeyExpr): Builder<Channel<Reply>> {
            return Builder(session, keyExpr, handler = ChannelHandler(Channel()))
        }
    }

    /**
     * Builder to construct a [Get].
     *
     * Either a [Handler] or a [Callback] must be specified. Note neither of them are stackable and are mutually exclusive,
     * meaning that it is not possible to specify multiple callbacks and/or handlers, the builder only considers the
     * last one specified.
     *
     * @param R The receiver type of the [Handler] implementation, defaults to [Unit] when no handler is specified.
     * @property session The [Session] from which the query will be performed.
     * @property keyExpr The [KeyExpr] upon which the get query will be performed.
     * @constructor Creates a Builder. This constructor is internal and should not be called directly. Instead, this
     * builder should be obtained through the [Session] after calling [Session.get].
     */
    class Builder<R> internal constructor(
        private val session: Session,
        private val keyExpr: KeyExpr,
        private var callback: Callback<Reply>? = null,
        private var handler: Handler<Reply, R>? = null,
    ) {

        private var timeout = Duration.ofMillis(10000)
        private var target: QueryTarget = QueryTarget.BEST_MATCHING
        private var consolidation: ConsolidationMode = ConsolidationMode.NONE // None
        private var value: Value? = null

        private constructor(other: Builder<*>, handler: Handler<Reply, R>?) : this(other.session, other.keyExpr) {
            this.handler = handler
            copyParams(other)
        }

        private constructor(other: Builder<*>, callback: Callback<Reply>?) : this(other.session, other.keyExpr) {
            this.callback = callback
            copyParams(other)
        }

        private fun copyParams(other: Builder<*>) {
            this.timeout = other.timeout
            this.target = other.target
            this.consolidation = other.consolidation
            this.value = other.value
        }

        /** Specify the [QueryTarget]. */
        fun target(target: QueryTarget): Builder<R> {
            this.target = target
            return this
        }

        /** Specify the [ConsolidationMode]. */
        fun consolidation(consolidation: ConsolidationMode): Builder<R> {
            this.consolidation = consolidation
            return this
        }

        /** Specify the timeout. */
        fun timeout(timeout: Duration): Builder<R> {
            this.timeout = timeout
            return this
        }

        /**
         * Specify a string value. A [Value] is generated with the provided message, therefore
         * this method is equivalent to calling `withValue(Value(message))`.
         */
        fun withValue(message: String): Builder<R> {
            this.value = Value(message)
            return this
        }

        /** Specify a [Value]. */
        fun withValue(value: Value): Builder<R> {
            this.value = value
            return this
        }

        /** Specify a [Callback]. Overrides any previously specified callback or handler. */
        fun with(callback: Callback<Reply>): Builder<Unit> = Builder(this, callback)

        /** Specify a [Handler]. Overrides any previously specified callback or handler. */
        fun <R2> with(handler: Handler<Reply, R2>): Builder<R2> = Builder(this, handler)

        /** Specify a [Channel]. Overrides any previously specified callback or handler. */
        fun with(channel: Channel<Reply>): Builder<Channel<Reply>> = Builder(this, ChannelHandler(channel))

        /**
         * Resolve the builder triggering the query.
         *
         * @return A [Result] with the [receiver] from the specified [Handler] (if specified).
         */
        fun res(): Result<R?> = runCatching {
            require(callback != null || handler != null) { "Either a callback or a handler must be provided." }
            val resolvedCallback = callback ?: Callback { t: Reply -> handler?.handle(t) }
            return session.run {
                resolveGet(
                    keyExpr,
                    resolvedCallback,
                    handler?.receiver(),
                    timeout,
                    target,
                    consolidation,
                    value
                )
            }
        }
    }
}
