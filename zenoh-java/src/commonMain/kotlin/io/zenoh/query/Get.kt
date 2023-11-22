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
import io.zenoh.exceptions.ZenohException
import io.zenoh.handlers.BlockingQueueHandler
import io.zenoh.handlers.Handler
import io.zenoh.selector.Selector
import io.zenoh.value.Value
import java.time.Duration
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

/**
 * Get to query data from the matching queryables in the system.
 *
 * Example with a [Callback]:
 * ```java
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/java/example")) {
 *          session.get(keyExpr)
 *              .consolidation(ConsolidationMode.NONE)
 *              .withValue("Get value example")
 *              .with(reply -> System.out.println("Received reply " + reply))
 *              .res()
 *     }
 * }
 * ```
 *
 * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, R will be [Unit].
 */
class Get<R> private constructor() {

    companion object {
        /**
         * Creates a bew [Builder] associated to the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the query will be triggered.
         * @param selector The [Selector] with which the query will be performed.
         * @return A [Builder] with a default [BlockingQueueHandler] to handle any incoming [Reply].
         */
        fun newBuilder(session: Session, selector: Selector): Builder<BlockingQueue<Optional<Reply>>> {
            return Builder(session, selector, handler = BlockingQueueHandler(LinkedBlockingDeque()))
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
     * @property selector The [Selector] with which the get query will be performed.
     * @constructor Creates a Builder. This constructor is internal and should not be called directly. Instead, this
     * builder should be obtained through the [Session] after calling [Session.get].
     */
    class Builder<R> internal constructor(
        private val session: Session,
        private val selector: Selector,
        private var callback: Callback<Reply>? = null,
        private var handler: Handler<Reply, R>? = null,
    ) {

        private var timeout = Duration.ofMillis(10000)
        private var target: QueryTarget = QueryTarget.BEST_MATCHING
        private var consolidation: ConsolidationMode = ConsolidationMode.NONE
        private var value: Value? = null
        private var onClose: (() -> Unit)? = null

        private constructor(other: Builder<*>, handler: Handler<Reply, R>?) : this(other.session, other.selector) {
            this.handler = handler
            copyParams(other)
        }

        private constructor(other: Builder<*>, callback: Callback<Reply>?) : this(other.session, other.selector) {
            this.callback = callback
            copyParams(other)
        }

        private fun copyParams(other: Builder<*>) {
            this.timeout = other.timeout
            this.target = other.target
            this.consolidation = other.consolidation
            this.value = other.value
            this.onClose = other.onClose
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

        /**
         * Specify an action to be invoked when the Get operation is over.
         *
         * Zenoh will trigger ths specified action once no more replies are to be expected.
         */
        fun onClose(action: () -> Unit): Builder<R> {
            this.onClose = action
            return this
        }

        /** Specify a [Callback]. Overrides any previously specified callback or handler. */
        fun with(callback: Callback<Reply>): Builder<Unit> = Builder(this, callback)

        /** Specify a [Handler]. Overrides any previously specified callback or handler. */
        fun <R2> with(handler: Handler<Reply, R2>): Builder<R2> = Builder(this, handler)

        /** Specify a [BlockingQueue]. Overrides any previously specified callback or handler. */
        fun with(blockingQueue: BlockingQueue<Optional<Reply>>): Builder<BlockingQueue<Optional<Reply>>> = Builder(this, BlockingQueueHandler(blockingQueue))

        /**
         * Resolve the builder triggering the query.
         *
         * @return The receiver [R] from the specified [Handler] (if specified).
         */
        @Throws(ZenohException::class)
        fun res(): R? {
            require(callback != null || handler != null) { "Either a callback or a handler must be provided." }
            val resolvedCallback = callback ?: Callback { t: Reply -> handler?.handle(t) }
            val resolvedOnClose = fun() {
                onClose?.invoke()
                handler?.onClose()
            }
            return session.run {
                resolveGet(
                    selector,
                    resolvedCallback,
                    resolvedOnClose,
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
