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
import com.github.ajalt.clikt.parameters.types.long
import io.zenoh.annotations.Unstable
import io.zenoh.bytes.ZBytes
import io.zenoh.query.QueryTarget
import io.zenoh.query.intoSelector
import java.time.Duration

class ZQuerier(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Querier example"
) {

    @OptIn(Unstable::class)
    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        val session = Zenoh.open(config).getOrThrow()
        val selector = selector.intoSelector().getOrThrow()

        val target = target ?.let{ QueryTarget.valueOf(it.uppercase()) } ?: QueryTarget.BEST_MATCHING
        val timeout = Duration.ofMillis(timeout)
        val querier = session.declareQuerier(selector.keyExpr, target, timeout = timeout).getOrThrow()

        for (idx in 0..Int.MAX_VALUE) {
            Thread.sleep(1000)
            val payload = "[${idx.toString().padStart(4, ' ')}] ${payload ?: ""}"
            println("Querying '$selector' with payload: '$payload'...")
            querier.get(callback = {
                it.result.onSuccess { sample ->
                    println(">> Received ('${sample.keyExpr}': '${sample.payload}')")
                }.onFailure { error ->
                    println(">> Received (ERROR: '${error.message}')")
                }
            }, payload = ZBytes.from(payload), parameters = selector.parameters)
        }

        session.close()
    }

    private val selector by option(
        "-s",
        "--selector",
        help = "The selection of resources to query [default: demo/example/**]",
        metavar = "selector"
    ).default("demo/example/**")
    private val payload by option(
        "-p", "--payload", help = "An optional payload to put in the queries.", metavar = "payload"
    )
    private val target by option(
        "-t",
        "--target",
        help = "The target queryables of the querier. Default: BEST_MATCHING. " + "[possible values: BEST_MATCHING, ALL, ALL_COMPLETE]",
        metavar = "target"
    )
    private val timeout by option(
        "-o", "--timeout", help = "The query timeout in milliseconds [default: 10000]", metavar = "timeout"
    ).long().default(10000)
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val mode by option(
        "-m",
        "--mode",
        help = "The session mode. Default: peer. Possible values: [peer, client, router]",
        metavar = "mode"
    ).default("peer")
    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val listen: List<String> by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).multiple()
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZQuerier(args.isEmpty()).main(args)
