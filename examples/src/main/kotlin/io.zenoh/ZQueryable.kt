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
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.queryable.Query
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ntp.TimeStamp

class ZQueryable(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Queryable example"
) {

    private val key by option(
        "-k",
        "--key",
        help = "The key expression to write to [default: demo/example/zenoh-kotlin-queryable]",
        metavar = "key"
    ).default("demo/example/zenoh-kotlin-queryable")
    private val value by option(
        "-v", "--value", help = "The value to reply to queries [default: \"Queryable from Kotlin!\"]", metavar = "value"
    ).default("Queryable from Kotlin!")
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

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting,mode)

        Session.open(config).onSuccess { session ->
            session.use {
                key.intoKeyExpr().onSuccess { keyExpr ->
                    keyExpr.use {
                        println("Declaring Queryable")
                        session.declareQueryable(keyExpr).res().onSuccess { queryable ->
                            queryable.use {
                                queryable.receiver?.let { receiverChannel -> //  The default receiver is a Channel we can process on a coroutine.
                                    runBlocking {
                                        handleRequests(receiverChannel, keyExpr)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRequests(
        receiverChannel: Channel<Query>, keyExpr: KeyExpr
    ) {
        val iterator = receiverChannel.iterator()
        while (iterator.hasNext()) {
            iterator.next().use { query ->
                val valueInfo = query.value?.let { value -> " with value '$value'" } ?: ""
                println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
                query.reply(keyExpr).success(value).withKind(SampleKind.PUT).withTimeStamp(TimeStamp.getCurrentTime())
                    .res().onFailure { println(">> [Queryable ] Error sending reply: $it") }
            }
        }
    }
}

fun main(args: Array<String>) = ZQueryable(args.isEmpty()).main(args)
