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
import io.zenoh.query.ConsolidationMode
import io.zenoh.query.QueryTarget
import io.zenoh.query.Reply
import io.zenoh.selector.intoSelector
import kotlinx.coroutines.runBlocking
import java.time.Duration

class ZGet(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Get example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting,mode)

        Session.open(config).onSuccess { session ->
            session.use {
                selector.intoSelector().onSuccess { selector ->
                    selector.use {
                        session.get(selector)
                            .consolidation(ConsolidationMode.NONE)
                            .timeout(Duration.ofMillis(timeout))
                            .apply {
                                target?.let {
                                    target(QueryTarget.valueOf(it.uppercase()))
                                }
                                attachment?.let {
                                    withAttachment(it.toByteArray())
                                }
                                value?.let {
                                    withValue(it)
                                }
                            }
                            .wait()
                            .onSuccess { receiver ->
                                runBlocking {
                                    for (reply in receiver!!) {
                                        when (reply) {
                                            is Reply.Success -> println("Received ('${reply.sample.keyExpr}': '${reply.sample.value}')")
                                            is Reply.Error -> println("Received (ERROR: '${reply.error}')")
                                            is Reply.Delete -> println("Received (DELETE '${reply.keyExpr}')")
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    private val selector by option(
        "-s",
        "--selector",
        help = "The selection of resources to query [default: demo/example/**]",
        metavar = "selector"
    ).default("demo/example/**")
    private val value by option(
        "-v", "--value", help = "An optional value to put in the query.", metavar = "value"
    )
    private val target by option(
        "-t",
        "--target",
        help = "The target queryables of the query. Default: BEST_MATCHING. " + "[possible values: BEST_MATCHING, ALL, ALL_COMPLETE]",
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
    private val attachment by option(
        "-a",
        "--attach",
        help = "The attachment to add to the get. The key-value pairs are &-separated, and = serves as the separator between key and value.",
        metavar = "attach"
    )
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZGet(args.isEmpty()).main(args)
