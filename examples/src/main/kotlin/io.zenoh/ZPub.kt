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

class ZPub(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Pub example"
) {

    private val key by option(
        "-k", "--key", help = "The key expression to write to [default: demo/example/zenoh-kotlin-pub]", metavar = "key"
    ).default("demo/example/zenoh-kotlin-pub")
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
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
    private val value by option(
        "-v", "--value", help = "The value to write. [Default: \"Pub from Kotlin!\"]", metavar = "value"
    ).default("Pub from Kotlin!")
    private val attachment by option(
        "-a",
        "--attach",
        help = "The attachments to add to each put. The key-value pairs are &-separated, and = serves as the separator between key and value.",
        metavar = "attach"
    )
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
                        println("Declaring publisher on '$keyExpr'...")
                        session.declarePublisher(keyExpr).res().onSuccess { pub ->
                            pub.use {
                                val attachment = attachment?.let { decodeAttachment(it) }
                                var idx = 0
                                while (true) {
                                    Thread.sleep(1000)
                                    println(
                                        "Putting Data ('$keyExpr': '[${
                                            idx.toString().padStart(4, ' ')
                                        }] $value')..."
                                    )
                                    attachment?.let {
                                        pub.put(value).withAttachment(attachment).res()
                                    } ?: let { pub.put(value).res() }
                                    idx++
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { exception -> println(exception.message) }
    }
}

fun main(args: Array<String>) = ZPub(args.isEmpty()).main(args)
