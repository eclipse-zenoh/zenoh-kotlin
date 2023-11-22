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
 * ```java
 * public class QueueHandler<T extends ZenohType> implements Handler<T, ArrayDeque<T>> {
 *
 *     private final ArrayDeque<T> queue = new ArrayDeque<>();
 *
 *     @Override
 *     public void handle(T t) {
 *         System.out.println("Received " + t + ", enqueuing...");
 *         queue.add(t);
 *     }
 *
 *     @Override
 *     public ArrayDeque<T> receiver() {
 *         return queue;
 *     }
 *
 *     @Override
 *     public void onClose() {
 *         System.out.println("Received in total " + queue.size() + " elements.");
 *     }
 * }
 * ```
 *
 * That `QueueHandler` could then be used as follows, for instance for a subscriber:
 * ```java
 * QueueHandler<Sample> handler = new QueueHandler<Sample>();
 * session.declareSubscriber(keyExpr).with(handler).res();
 * ...
 * ```
 *
 * @param T A receiving [ZenohType], either a [io.zenoh.sample.Sample], a [io.zenoh.query.Reply] or a [io.zenoh.queryable.Query].
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
     * For instances of [io.zenoh.queryable.Queryable] and [io.zenoh.subscriber.Subscriber],
     * Zenoh triggers this callback when they are closed or undeclared. In the case of a Get query
     * (see [io.zenoh.query.Get]), it is invoked when no more elements of type [T] are expected
     * to be received.
     */
    fun onClose()
}
