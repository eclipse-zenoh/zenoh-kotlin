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

package io.zenoh.handlers

import io.zenoh.annotations.Unstable
import io.zenoh.pubsub.Miss

/**
 * Handler interface for classes implementing behavior to handle the incoming [Miss] events.
 *
 * **Example**:
 * ```kotlin
 * class SampleMissQueueHandler : SampleMissHandler<ArrayDeque<Miss>> {
 *     private val queue: ArrayDeque<T> = ArrayDeque()
 *
 *     override fun handle(miss: Miss) {
 *         println("Received $miss, enqueuing...")
 *         queue.add(miss)
 *     }
 *
 *     override fun receiver(): ArrayDeque<Miss> {
 *         return queue
 *     }
 *
 *     override fun onClose() {
 *         println("Received in total ${queue.size} elements."}
 *     }
 * }
 * ```
 *
 *
 * @param R An arbitrary receiver.
 */
@Unstable
interface SampleMissHandler<R> {

    /**
     * Handle the received [Miss] event.
     *
     * @param miss A [Miss] event.
     */
    fun handle(miss: Miss)

    /**
     * Return the receiver of the handler.
     */
    fun receiver(): R

    /**
     * This callback is invoked by Zenoh at the conclusion of the handler's lifecycle.
     */
    fun onClose()
}
