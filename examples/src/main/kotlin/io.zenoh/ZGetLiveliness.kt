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
import io.zenoh.keyexpr.intoKeyExpr
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.time.Duration

class ZGetLiveliness(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Sub Liveliness example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile)

        Zenoh.initLogFromEnvOr("error")

        println("Opening session...")
        Zenoh.open(config).onSuccess { session ->
            key.intoKeyExpr().onSuccess { keyExpr ->
                session.liveliness().get(keyExpr, channel = Channel(), timeout = Duration.ofMillis(timeout))
                    .onSuccess { channel ->
                        runBlocking {
                            for (reply in channel) {
                                reply.result.onSuccess {
                                    println(">> Alive token ('${it.keyExpr}')")
                                }.onFailure {
                                    println(">> Received (ERROR: '${it.message}')")
                                }
                            }
                        }
                    }
            }
        }.onFailure { exception -> println(exception.message) }
    }

    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val key by option(
        "-k",
        "--key",
        help = "The key expression matching liveliness tokens to query. [default: group1/**]",
        metavar = "key"
    ).default("group1/**")
    private val timeout by option(
        "-o", "--timeout", help = "The query timeout in milliseconds [default: 10000]", metavar = "timeout"
    ).long().default(10000)
}

fun main(args: Array<String>) = ZGetLiveliness(args.isEmpty()).main(args)
