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
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

/**
 * Channel handler
 *
 * Implementation of a [Handler] with a [Channel] receiver. This handler is intended to be used
 * as the default handler by the [io.zenoh.queryable.Queryable], [io.zenoh.subscriber.Subscriber] and [io.zenoh.query.Get],
 * allowing us to send the incoming elements through a [Channel] within the context of a Kotlin coroutine.
 *
 * @param T
 * @property channel
 * @constructor Create empty Channel handler
 */
class ChannelHandler<T: ZenohType>(private val channel: Channel<T>) : Handler<T, Channel<T>> {

    override fun handle(t: T) {
        runBlocking { channel.send(t) }
    }

    override fun receiver(): Channel<T> {
        return channel
    }

    override fun onClose() {
        channel.close()
    }
}
