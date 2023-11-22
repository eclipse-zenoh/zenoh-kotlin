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

package io.zenoh.queryable

import io.zenoh.*
import io.zenoh.exceptions.ZenohException
import io.zenoh.handlers.Callback
import io.zenoh.handlers.BlockingQueueHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQueryable
import io.zenoh.keyexpr.KeyExpr
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

/**
 * A queryable that allows to perform multiple queries on the specified [KeyExpr].
 *
 * Its main purpose is to keep the queryable active as long as it exists.
 *
 * Example using the default [BlockingQueueHandler] handler:
 * ```java
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/zenoh-java-queryable")) {
 *         System.out.println("Declaring Queryable");
 *         try (Queryable<BlockingQueue<Optional<Query>>> queryable = session.declareQueryable(keyExpr).res()) {
 *             BlockingQueue<Optional<Query>> receiver = queryable.getReceiver();
 *             while (true) {
 *                 Optional<Query> wrapper = receiver.take();
 *                 if (wrapper.isEmpty()) {
 *                     break;
 *                 }
 *                 Query query = wrapper.get();
 *                 String valueInfo = query.getValue() != null ? " with value '" + query.getValue() + "'" : "";
 *                 System.out.println(">> [Queryable] Received Query '" + query.getSelector() + "'" + valueInfo);
 *                 try {
 *                     query.reply(keyExpr)
 *                         .success("Queryable from Java!")
 *                         .withKind(SampleKind.PUT)
 *                         .withTimeStamp(TimeStamp.getCurrentTime())
 *                         .res();
 *                 } catch (Exception e) {
 *                     System.out.println(">> [Queryable] Error sending reply: " + e);
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, [R] will be [Unit].
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @property jniQueryable Delegate object in charge of communicating with the underlying native code.
 * @constructor Internal constructor. Instances of Queryable must be created through the [Builder] obtained after
 * calling [Session.declareQueryable] or alternatively through [newBuilder].
 */
class Queryable<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R?, private var jniQueryable: JNIQueryable?
) : AutoCloseable, SessionDeclaration {

    override fun isValid(): Boolean {
        return jniQueryable != null
    }

    override fun undeclare() {
        jniQueryable?.close()
        jniQueryable = null
    }

    override fun close() {
        undeclare()
    }

    protected fun finalize() {
        jniQueryable?.close()
    }

    companion object {

        /**
         * Creates a new [Builder] associated to the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the queryable will be declared.
         * @param keyExpr The [KeyExpr] associated to the queryable.
         * @return An empty [Builder] with a default [BlockingQueueHandler] to handle the incoming samples.
         */
        fun newBuilder(session: Session, keyExpr: KeyExpr): Builder<BlockingQueue<Optional<Query>>> {
            return Builder(session, keyExpr, handler = BlockingQueueHandler(queue = LinkedBlockingDeque()))
        }
    }

    /**
     * Builder to construct a [Queryable].
     *
     * Either a [Handler] or a [Callback] must be specified. Note neither of them are stackable and are mutually exclusive,
     * meaning that it is not possible to specify multiple callbacks and/or handlers, the builder only considers the
     * last one specified.
     *
     * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, R will be [Unit].
     * @property session [Session] to which the [Queryable] will be bound to.
     * @property keyExpr The [KeyExpr] to which the queryable is associated.
     * @property callback Optional callback that will be triggered upon receiving a [Query].
     * @property handler Optional handler to receive the incoming queries.
     * @constructor Creates a Builder. This constructor is internal and should not be called directly. Instead, this
     * builder should be obtained through the [Session] after calling [Session.declareQueryable].
     */
    class Builder<R> internal constructor(
        private val session: Session,
        private val keyExpr: KeyExpr,
        private var callback: Callback<Query>? = null,
        private var handler: Handler<Query, R>? = null
    ): Resolvable<Queryable<R>> {
        private var complete: Boolean = false
        private var onClose: (() -> Unit)? = null

        private constructor(other: Builder<*>, handler: Handler<Query, R>?) : this(other.session, other.keyExpr) {
            this.handler = handler
            this.complete = other.complete
            this.onClose = other.onClose
        }

        private constructor(other: Builder<*>, callback: Callback<Query>?) : this(other.session, other.keyExpr) {
            this.callback = callback
            this.complete = other.complete
            this.onClose = other.onClose
        }

        /** Change queryable completeness. */
        fun complete(complete: Boolean) = apply { this.complete = complete }

        /** Specify an action to be invoked when the [Queryable] is undeclared. */
        fun onClose(action: () -> Unit): Builder<R> {
            this.onClose = action
            return this
        }

        /** Specify a [Callback]. Overrides any previously specified callback or handler. */
        fun with(callback: Callback<Query>): Builder<Unit> = Builder(this, callback)

        /** Specify a [Handler]. Overrides any previously specified callback or handler. */
        fun <R2> with(handler: Handler<Query, R2>): Builder<R2> = Builder(this, handler)

        /** Specify a [BlockingQueue]. Overrides any previously specified callback or handler. */
        fun with(blockingQueue: BlockingQueue<Optional<Query>>): Builder<BlockingQueue<Optional<Query>>> = Builder(this, BlockingQueueHandler(blockingQueue))

        /**
         * Resolve the builder, creating a [Queryable] with the provided parameters.
         *
         * @return The newly created [Queryable].
         */
        @Throws(ZenohException::class)
        override fun res(): Queryable<R> {
            require(callback != null || handler != null) { "Either a callback or a handler must be provided." }
            val resolvedCallback = callback ?: Callback { t: Query -> handler?.handle(t) }
            val resolvedOnClose = fun() {
                handler?.onClose()
                onClose?.invoke()
            }
            return session.run { resolveQueryable(keyExpr, resolvedCallback, resolvedOnClose, handler?.receiver(), complete) }
        }
    }
}

