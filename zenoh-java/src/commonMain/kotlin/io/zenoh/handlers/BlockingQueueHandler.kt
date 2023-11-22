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
import java.util.Optional
import java.util.concurrent.BlockingQueue

/**
 * Blocking queue handler
 *
 * Implementation of a [Handler] with a [BlockingQueue] receiver. This handler is intended to be used
 * as the default handler by the [io.zenoh.queryable.Queryable], [io.zenoh.subscriber.Subscriber] and [io.zenoh.query.Get],
 * allowing us to send the incoming elements through a [BlockingQueue].
 *
 * The way to tell no more elements of type [T] will be received is when an empty element is put (see [onClose]).
 *
 * @param T a [ZenohType]
 * @property queue
 * @constructor Create empty Queue handler
 */
class BlockingQueueHandler<T: ZenohType>(private val queue: BlockingQueue<Optional<T>>) : Handler<T, BlockingQueue<Optional<T>>> {

    override fun handle(t: T) {
        queue.put(Optional.of(t))
    }

    override fun receiver(): BlockingQueue<Optional<T>> {
        return queue
    }

    override fun onClose() {
        queue.put(Optional.empty())
    }
}
