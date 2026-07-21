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
import io.zenoh.config.WhatAmI
import io.zenoh.config.WhatAmI.*
import io.zenoh.exceptions.zCall
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.scouting.Scout as JniScout
import io.zenoh.scouting.Hello
import io.zenoh.scouting.Scout
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
    ): Result<Scout<Unit>> = performScout(callback, {}, Unit, whatAmI, config)

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
    ): Result<Scout<R>> =
        performScout(handler::handle, handler::onClose, handler.receiver(), whatAmI, config)

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
        val handler = ChannelHandler(channel)
        return performScout(handler::handle, handler::onClose, handler.receiver(), whatAmI, config)
    }

    private fun <R> performScout(
        callback: Callback<Hello>,
        onClose: () -> Unit,
        receiver: R,
        whatAmI: Set<WhatAmI>,
        config: Config?
    ): Result<Scout<R>> {
        return zCall({ JniScout(0L) }) { onBindingError, onError ->
            // Argument preparation stays inside the captured block: an empty
            // [whatAmI] makes `reduce` throw, which must surface as
            // Result.failure (the pre-flat API contract).
            val binaryWhatAmI = whatAmI.map { it.value }.reduce { acc, it -> acc or it }
            io.zenoh.jni.scouting.scout(
                binaryWhatAmI,
                config?.jniConfig,
                helloCallbackOf { callback.run(it) },
                { onClose() },
                onBindingError, onError
            )
        }.map { Scout(receiver, it) }
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
    fun initLogFromEnvOr(fallbackFilter: String): Result<Unit> {
        val logLevelProp = System.getenv(LOG_ENV)
        return Logger.start(logLevelProp ?: fallbackFilter)
    }
}
