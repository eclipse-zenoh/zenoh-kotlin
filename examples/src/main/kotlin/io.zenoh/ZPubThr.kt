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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.ulong
import io.zenoh.prelude.KnownEncoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.publication.CongestionControl
import io.zenoh.publication.Priority
import io.zenoh.value.Value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path

class ZPubThr(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Throughput example"
) {

    private val payloadSize by argument(
        "payload_size",
        help = "Sets the size of the payload to publish [Default: 8]"
    ).int().default(8)

    private val priorityInput by option(
        "-p",
        "--priority",
        help = "Priority for sending data",
        metavar = "priority"
    ).int()
    private val number by option(
        "-n",
        "--number",
        help = "Number of messages in each throughput measurements [default: 100000]",
        metavar = "number"
    ).ulong().default(100000u)
    private val statsPrint by option("-t", "--print", help = "Print the statistics").boolean().default(true)

    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
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

    override fun run() {
        val data = ByteArray(payloadSize)
        for (i in 0..<payloadSize) {
            data[i] = (i % 10).toByte()
        }
        val value = Value(data, Encoding(KnownEncoding.EMPTY))

        val config = loadConfig()

        Session.open(config).onSuccess {
            it.use { session ->
                session.declarePublisher("test/thr".intoKeyExpr().getOrThrow())
                    .congestionControl(CongestionControl.BLOCK).apply {
                        priorityInput?.let { priority(Priority.entries[it]) }
                    }.res().onSuccess { pub ->
                        pub.use {
                            println("Publisher declared on test/thr.")
                            var count: Long = 0
                            var start = System.currentTimeMillis()
                            val number = number.toLong()
                            while (true) {
                                pub.put(value).res().getOrThrow()

                                if (statsPrint) {
                                    if (count < number) {
                                        count++
                                    } else {
                                        val throughput = count * 1000 / (System.currentTimeMillis() - start)
                                        println("$throughput msgs/s")
                                        count = 0
                                        start = System.currentTimeMillis()
                                    }
                                }

                            }
                        }
                    }
            }
        }
    }

    private fun loadConfig(): Config {
        val config = if (emptyArgs) {
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
}

fun main(args: Array<String>) = ZPubThr(args.isEmpty()).main(args)
