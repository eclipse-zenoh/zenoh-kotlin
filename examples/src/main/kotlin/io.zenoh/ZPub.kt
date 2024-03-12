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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import io.zenoh.config.*
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.sample.Attachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path

class ZPub(private val defaultConfig: Boolean) : CliktCommand(
    help = "Zenoh Pub example"
) {

    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val key by option(
        "-k", "--key", help = "The key expression to write to [default: demo/example/zenoh-kotlin-pub]", metavar = "key"
    ).default("demo/example/zenoh-kotlin-pub")
    private val listen: List<String> by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).multiple()
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
        val config = loadConfig()

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

    private fun loadConfig(): Config {
        val config = if (defaultConfig) {
            Config.default()
        } else {
            configFile?.let { Config.from(Path(it)) } ?: let {
                val connect = if (connect.isEmpty()) null else Connect(connect)
                val listen = if (listen.isEmpty()) null else Listen(listen)
                val scouting = Scouting(Multicast(!noMulticastScouting))
                val configData = ConfigData(connect, listen, mode, scouting)
                val jsonConfig = Json.encodeToJsonElement(configData)
                Config.from(jsonConfig)
            }
        }
        return config
    }

    private fun decodeAttachment(attachment: String): Attachment {
        val pairs = attachment.split("&").map { it.split("=").let { (k, v) -> k.toByteArray() to v.toByteArray() } }
        return Attachment.Builder().addAll(pairs).res()
    }
}

fun main(args: Array<String>) = ZPub(args.isEmpty()).main(args)
