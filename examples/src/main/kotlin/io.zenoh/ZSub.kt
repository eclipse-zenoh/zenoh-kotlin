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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.varargValues
import io.zenoh.keyexpr.intoKeyExpr
import kotlinx.coroutines.runBlocking

class ZSub(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Sub example"
) {

    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val key by option(
        "-k", "--key", help = "The key expression to subscribe to [default: demo/example/**]", metavar = "key"
    ).default("demo/example/**")
    private val connect: List<String>? by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).varargValues()
    private val listen: List<String>? by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).varargValues()
    private val mode by option(
        "-m",
        "--mode",
        help = "The session mode. Default: peer. Possible values: [peer, client, router]",
        metavar = "mode"
    ).default("peer")
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting,mode)

        println("Opening session...")
        Session.open(config).onSuccess { session ->
            session.use {
                key.intoKeyExpr().onSuccess { keyExpr ->
                    keyExpr.use {
                        println("Declaring Subscriber on '$keyExpr'...")
                        session.declareSubscriber(keyExpr).bestEffort().res().onSuccess { subscriber ->
                            subscriber.use {
                                runBlocking {
                                    val receiver = subscriber.receiver!!
                                    val iterator = receiver.iterator()
                                    while (iterator.hasNext()) {
                                        val sample = iterator.next()
                                        println(">> [Subscriber] Received ${sample.kind} ('${sample.keyExpr}': '${sample.value}'" + "${
                                            sample.attachment?.let {
                                                ", with attachment: " + "${
                                                    it.values.map { it.first.decodeToString() to it.second.decodeToString() }
                                                }"
                                            } ?: ""
                                        })")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { exception -> println(exception.message) }
    }
}

fun main(args: Array<String>) = ZSub(args.isEmpty()).main(args)

