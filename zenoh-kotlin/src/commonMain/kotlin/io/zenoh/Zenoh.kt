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

import io.zenoh.Logger.Companion.LOG_ENV
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIScout
import io.zenoh.scouting.Hello
import io.zenoh.scouting.Scout
import io.zenoh.scouting.WhatAmI
import io.zenoh.scouting.WhatAmI.*
import kotlinx.coroutines.channels.Channel

object Zenoh {

    /**
     * Open a [Session] with the provided [Config].
     *
     * @param config The configuration for the session.
     * @return A [Result] with the [Session] on success.
     */
    fun open(config: Config): Result<Session> {
        return Session.open(config)
    }

    /**
     * Scout for routers and/or peers.
     *
     * Scout spawns a task that periodically sends scout messages and waits for Hello replies.
     * Drop the returned Scout to stop the scouting task or explicitly call [Scout.stop] or [Scout.close].
     *
     * @param callback [Callback] to be run when receiving a [Hello] message.
     * @param whatAmI [WhatAmI] configuration: it indicates the role of the zenoh node sending the HELLO message.
     * @param config Optional [Config] for the scout.
     * @return A result with the [Scout] object.
     */
    fun scout(
        callback: Callback<Hello>,
        whatAmI: Set<WhatAmI> = setOf(Peer, Router),
        config: Config? = null
    ): Result<Scout<Unit>> {
        ZenohLoad
        return JNIScout.scout(whatAmI = whatAmI, callback = callback, receiver = Unit, config = config)
    }

    /**
     * Scout for routers and/or peers.
     *
     * Scout spawns a task that periodically sends scout messages and waits for Hello replies.
     * Drop the returned Scout to stop the scouting task or explicitly call [Scout.stop] or [Scout.close].
     *
     * @param handler [Handler] to handle incoming [Hello] messages.
     * @param whatAmI [WhatAmI] configuration: it indicates the role of the zenoh node sending the HELLO message.
     * @param config Optional [Config] for the scout.
     * @return A result with the [Scout] object.
     */
    fun <R> scout(
        handler: Handler<Hello, R>,
        whatAmI: Set<WhatAmI> = setOf(Peer, Router),
        config: Config? = null
    ): Result<Scout<R>> {
        ZenohLoad
        return JNIScout.scout(
            whatAmI = whatAmI,
            callback = { hello -> handler.handle(hello) },
            receiver = handler.receiver(),
            config = config
        )
    }

    /**
     * Scout for routers and/or peers.
     *
     * Scout spawns a task that periodically sends scout messages and waits for Hello replies.
     * Drop the returned Scout to stop the scouting task or explicitly call [Scout.stop] or [Scout.close].
     *
     * @param channel [Channel] upon which the incoming [Hello] messages will be piped.
     * @param whatAmI [WhatAmI] configuration: it indicates the role of the zenoh node sending the HELLO message.
     * @param config Optional [Config] for the scout.
     * @return A result with the [Scout] object.
     */
    fun scout(
        channel: Channel<Hello>,
        whatAmI: Set<WhatAmI> = setOf(Peer, Router),
        config: Config? = null
    ): Result<Scout<Channel<Hello>>> {
        ZenohLoad
        val handler = ChannelHandler(channel)
        return JNIScout.scout(
            whatAmI = whatAmI,
            callback = { hello -> handler.handle(hello) },
            receiver = handler.receiver(),
            config = config
        )
    }

    /**
     * Initializes the zenoh runtime logger, using rust environment settings.
     * E.g.: `RUST_LOG=info` will enable logging at info level. Similarly, you can set the variable to `error` or `debug`.
     *
     * Note that if the environment variable is not set, then logging will not be enabled.
     * See https://docs.rs/env_logger/latest/env_logger/index.html for accepted filter format.
     *
     * @see Logger
     */
    fun tryInitLogFromEnv() {
        val logEnv = System.getenv(LOG_ENV)
        if (logEnv != null) {
            ZenohLoad
            Logger.start(logEnv)
        }
    }

    /**
     * Initializes the zenoh runtime logger, using rust environment settings or the provided fallback level.
     * E.g.: `RUST_LOG=info` will enable logging at info level. Similarly, you can set the variable to `error` or `debug`.
     *
     * Note that if the environment variable is not set, then [fallbackFilter] will be used instead.
     * See https://docs.rs/env_logger/latest/env_logger/index.html for accepted filter format.
     *
     * @param fallbackFilter: The fallback filter if the `RUST_LOG` environment variable is not set.
     * @see Logger
     */
    fun initLogFromEnvOr(fallbackFilter: String): Result<Unit> = runCatching {
        ZenohLoad
        val logLevelProp = System.getenv(LOG_ENV)
        logLevelProp?.let { Logger.start(it) } ?: Logger.start(fallbackFilter)
    }
}

/**
 * Static singleton class to load the Zenoh native library once and only once, as well as the logger in function of the
 * log level configuration.
 */
internal expect object ZenohLoad
