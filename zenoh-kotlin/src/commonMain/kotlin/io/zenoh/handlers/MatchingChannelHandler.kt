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

import kotlinx.coroutines.channels.*
import kotlinx.coroutines.runBlocking

/**
 * Channel handler for matching status
 *
 * Implementation of a [Handler] with a [Channel] receiver.
 *
 * @property channel
 * @constructor Create empty Channel handler
 */
internal class MatchingChannelHandler(private val channel: Channel<Boolean>) : MatchingHandler<Channel<Boolean>> {

    override fun handle(matching: Boolean) {
        runBlocking { channel.send(matching) }
    }

    override fun receiver(): Channel<Boolean> {
        return channel
    }

    override fun onClose() {
        channel.close()
    }
}
