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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import io.zenoh.keyexpr.intoKeyExpr
import java.util.concurrent.CountDownLatch

class ZLiveliness(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Liveliness example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        println("Opening session...")
        val session = Zenoh.open(config).getOrThrow()
        val keyExpr = key.intoKeyExpr().getOrThrow()
        session.liveliness().declareToken(keyExpr)

        CountDownLatch(1).await() // A countdown latch is used here to block execution while the liveliness token
                                         // is declared. Typically, this wouldn't be needed.
        session.close()
    }

    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val key by option(
        "-k", "--key", help = "The key expression to subscribe to [default: group1/zenoh-kotlin]", metavar = "key"
    ).default("group1/zenoh-kotlin")
    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val listen: List<String> by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).multiple()
    private val mode by option(
        "-m",
        "--mode",
        help = "The session mode. Default: peer. Possible values: [peer, client, router]",
        metavar = "mode"
    ).default("peer")
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZLiveliness(args.isEmpty()).main(args)
