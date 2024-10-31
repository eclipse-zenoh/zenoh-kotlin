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

package io.zenoh.handlers

import io.zenoh.ZenohType

/**
 * Handler interface for classes implementing behavior to handle the
 * incoming [T] elements.
 *
 * **Example**:
 * ```kotlin
 * class QueueHandler<T: ZenohType> : Handler<T, ArrayDeque<T>> {
 *     private val queue: ArrayDeque<T> = ArrayDeque()
 *
 *     override fun handle(t: T) {
 *         println("Received $t, enqueuing...")
 *         queue.add(t)
 *     }
 *
 *     override fun receiver(): ArrayDeque<T> {
 *         return queue
 *     }
 *
 *     override fun onClose() {
 *         println("Received in total ${queue.size} elements."}
 *     }
 * }
 * ```
 *
 * That `QueueHandler` could then be used as follows, for instance for a subscriber:
 * ```kotlin
 * val subscriber = session.declareSubscriber(keyExpr, handler = QueueHandler<Sample>()).getOrThrow()
 * ```
 *
 * @param T A receiving [ZenohType], either a [io.zenoh.sample.Sample], a [io.zenoh.query.Reply] or a [io.zenoh.query.Query].
 * @param R An arbitrary receiver.
 */
interface Handler<T: ZenohType, R> {

    /**
     * Handle the received [t] element.
     *
     * @param t An element of type [T].
     */
    fun handle(t: T)

    /**
     * Return the receiver of the handler.
     */
    fun receiver(): R

    /**
     * This callback is invoked by Zenoh at the conclusion of the handler's lifecycle.
     *
     * For instances of [io.zenoh.query.Queryable] and [io.zenoh.pubsub.Subscriber],
     * Zenoh triggers this callback when they are closed or undeclared. In the case of a Get query
     * it is invoked when no more elements of type [T] are expected to be received.
     */
    fun onClose()
}
