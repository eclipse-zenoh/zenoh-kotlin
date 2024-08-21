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

package io.zenoh

import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIScout
import io.zenoh.scouting.Hello
import io.zenoh.scouting.Scout
import io.zenoh.scouting.WhatAmI
import kotlinx.coroutines.channels.Channel

/**
 * Static singleton class to load the Zenoh native library once and only once, as well as the logger in function of the
 * log level configuration.
 */
internal expect object ZenohLoad

object Zenoh {

    fun scout(
        callback: Callback<Hello>,
        whatAmI: Set<WhatAmI> = setOf(WhatAmI.Peer),
        config: Config = Config.default()
    ): Scout<Unit> {
        ZenohLoad
        return JNIScout.scout(whatAmI = whatAmI, callback = callback, receiver = Unit, config = config)
    }

    fun <R> scout(
        handler: Handler<Hello, R>,
        whatAmI: Set<WhatAmI> = setOf(WhatAmI.Peer),
        config: Config = Config.default()
    ): Scout<R> {
        ZenohLoad
        return JNIScout.scout(
            whatAmI = whatAmI,
            callback = { hello -> handler.handle(hello) },
            receiver = handler.receiver(),
            config = config
        )
    }

    fun scout(
        channel: Channel<Hello>,
        whatAmI: Set<WhatAmI> = setOf(WhatAmI.Peer),
        config: Config = Config.default()
    ): Scout<Channel<Hello>> {
        ZenohLoad
        val handler = ChannelHandler(channel)
        return JNIScout.scout(
            whatAmI = whatAmI,
            callback = { hello -> handler.handle(hello) },
            receiver = handler.receiver(),
            config = config
        )
    }
}