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
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

/**
 * Channel handler for [Miss] events.
 *
 * Implementation of a [Handler] with a [Channel] receiver.
 *
 * @property channel
 * @constructor Create empty Channel handler
 */
@Unstable
internal class SampleMissChannelHandler(private val channel: Channel<Miss>) : SampleMissHandler<Channel<Miss>> {

    /**
     * Handle the received sample miss event.
     *
     * @param miss sample miss event.
     */
    override fun handle(miss: Miss) {
        runBlocking { channel.send(miss) }
    }

    /**
     * Return the receiver of the handler.
     */
    override fun receiver(): Channel<Miss> {
        return channel
    }

    /**
     * This callback is invoked by Zenoh at the conclusion of the handler's lifecycle.
     */
    override fun onClose() {
        channel.close()
    }
}
