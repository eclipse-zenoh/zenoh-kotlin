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

package io.zenoh.subscriber

import io.zenoh.*
import io.zenoh.exceptions.ZenohException
import io.zenoh.handlers.Callback
import io.zenoh.handlers.BlockingQueueHandler
import io.zenoh.handlers.Handler
import io.zenoh.subscriber.Subscriber.Builder
import io.zenoh.jni.JNISubscriber
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.sample.Sample
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

/**
 * A subscriber that allows listening to updates on a key expression and reacting to changes.
 *
 * Its main purpose is to keep the subscription active as long as it exists.
 *
 * Example using the default [BlockingQueueHandler] handler:
 *
 * ```java
 * System.out.println("Opening session...");
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example")) {
 *         System.out.println("Declaring Subscriber on '" + keyExpr + "'...");
 *         try (Subscriber<BlockingQueue<Optional<Sample>>> subscriber = session.declareSubscriber(keyExpr).res()) {
 *             BlockingQueue<Optional<Sample>> receiver = subscriber.getReceiver();
 *             assert receiver != null;
 *             while (true) {
 *                 Optional<Sample> wrapper = receiver.take();
 *                 if (wrapper.isEmpty()) {
 *                     break;
 *                 }
 *                 Sample sample = wrapper.get();
 *                 System.out.println(">> [Subscriber] Received " + sample.getKind() + " ('" + sample.getKeyExpr() + "': '" + sample.getValue() + "')");
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, R will be [Unit].
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @property jniSubscriber Delegate object in charge of communicating with the underlying native code.
 * @constructor Internal constructor. Instances of Subscriber must be created through the [Builder] obtained after
 * calling [Session.declareSubscriber] or alternatively through [newBuilder].
 */
class Subscriber<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R?, private var jniSubscriber: JNISubscriber?
) : AutoCloseable, SessionDeclaration {

    override fun isValid(): Boolean {
        return jniSubscriber != null
    }

    override fun undeclare() {
        jniSubscriber?.close()
        jniSubscriber = null
    }

    override fun close() {
        undeclare()
    }

    protected fun finalize() {
        jniSubscriber?.close()
    }

    companion object {

        /**
         * Creates a new [Builder] associated to the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the subscriber will be declared.
         * @param keyExpr The [KeyExpr] associated to the subscriber.
         * @return An empty [Builder] with a default [BlockingQueueHandler] to handle the incoming samples.
         */
        fun newBuilder(session: Session, keyExpr: KeyExpr): Builder<BlockingQueue<Optional<Sample>>> {
            return Builder(session, keyExpr, handler = BlockingQueueHandler(queue = LinkedBlockingDeque()))
        }
    }

    /**
     * Builder to construct a [Subscriber].
     *
     * Either a [Handler] or a [Callback] must be specified. Note neither of them are stackable and are mutually exclusive,
     * meaning that it is not possible to specify multiple callbacks and/or handlers, the builder only considers the
     * last one specified.
     *
     * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, R will be [Unit].
     * @property session [Session] to which the [Subscriber] will be bound to.
     * @property keyExpr The [KeyExpr] to which the subscriber is associated.
     * @constructor Creates a Builder. This constructor is internal and should not be called directly. Instead, this
     * builder should be obtained through the [Session] after calling [Session.declareSubscriber].
     */
    class Builder<R> internal constructor(
        private val session: Session,
        private val keyExpr: KeyExpr,
        private var callback: Callback<Sample>? = null,
        private var handler: Handler<Sample, R>? = null
    ): Resolvable<Subscriber<R>> {

        private var reliability: Reliability = Reliability.BEST_EFFORT
        private var onClose: (() -> Unit)? = null

        private constructor(other: Builder<*>, handler: Handler<Sample, R>?): this(other.session, other.keyExpr) {
            this.handler = handler
            copyParams(other)
        }

        private constructor(other: Builder<*>, callback: Callback<Sample>?) : this(other.session, other.keyExpr) {
            this.callback = callback
            copyParams(other)
        }

        private fun copyParams(other: Builder<*>) {
            this.reliability = other.reliability
            this.onClose = other.onClose
        }

        /** Sets the [Reliability]. */
        fun reliability(reliability: Reliability): Builder<R> = apply {
            this.reliability = reliability
        }

        /** Sets the reliability to [Reliability.RELIABLE]. */
        fun reliable(): Builder<R> = apply {
            this.reliability = Reliability.RELIABLE
        }

        /** Sets the reliability to [Reliability.BEST_EFFORT]. */
        fun bestEffort(): Builder<R> = apply {
            this.reliability = Reliability.BEST_EFFORT
        }

        /** Specify an action to be invoked when the [Subscriber] is undeclared. */
        fun onClose(action: () -> Unit): Builder<R> {
            this.onClose = action
            return this
        }

        /** Specify a [Callback]. Overrides any previously specified callback or handler. */
        fun with(callback: Callback<Sample>): Builder<Unit> = Builder(this, callback)

        /** Specify a [Handler]. Overrides any previously specified callback or handler. */
        fun <R2> with(handler: Handler<Sample, R2>): Builder<R2> = Builder(this, handler)

        /** Specify a [BlockingQueue]. Overrides any previously specified callback or handler. */
        fun with(blockingQueue: BlockingQueue<Optional<Sample>>): Builder<BlockingQueue<Optional<Sample>>> = Builder(this, BlockingQueueHandler(blockingQueue))

        /**
         * Resolve the builder, creating a [Subscriber] with the provided parameters.
         *
         * @return The newly created [Subscriber].
         */
        @Throws(ZenohException::class)
        override fun res(): Subscriber<R> {
            require(callback != null || handler != null) { "Either a callback or a handler must be provided." }
            val resolvedCallback = callback ?: Callback { t: Sample -> handler?.handle(t) }
            val resolvedOnClose = fun() {
                handler?.onClose()
                onClose?.invoke()
            }
            return session.run { resolveSubscriber(keyExpr, resolvedCallback, resolvedOnClose, handler?.receiver(), reliability) }
        }
    }
}
