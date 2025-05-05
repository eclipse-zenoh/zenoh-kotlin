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

import io.zenoh.pubsub.SampleMiss
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

/**
 * Channel handler for [SampleMiss] events.
 *
 * Implementation of a [Handler] with a [Channel] receiver.
 *
 * @property channel
 * @constructor Create empty Channel handler
 */
internal class SampleMissChannelHandler(private val channel: Channel<SampleMiss>) : SampleMissHandler<Channel<SampleMiss>> {

    /**
     * Handle the received sample miss event.
     *
     * @param miss sample miss event.
     */
    override fun handle(miss: SampleMiss) {
        runBlocking { channel.send(miss) }
    }

    /**
     * Return the receiver of the handler.
     */
    override fun receiver(): Channel<SampleMiss> {
        return channel
    }

    /**
     * This callback is invoked by Zenoh at the conclusion of the handler's lifecycle.
     */
    override fun onClose() {
        channel.close()
    }
}
