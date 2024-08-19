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
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.ulong
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.Priority
import io.zenoh.prelude.QoS
import io.zenoh.value.Value

class ZPubThr(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Throughput example"
) {

    override fun run() {
        val data = ByteArray(payloadSize)
        for (i in 0..<payloadSize) {
            data[i] = (i % 10).toByte()
        }
        val value = Value(data, Encoding(Encoding.ID.ZENOH_BYTES))

        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        val qos = QoS(
            congestionControl = CongestionControl.BLOCK,
            priority = priorityInput?.let { Priority.entries[it] } ?: Priority.default(),
        )

        Session.open(config).onSuccess {
            it.use { session ->
                session.declarePublisher("test/thr".intoKeyExpr().getOrThrow(), qos = qos).onSuccess { pub ->
                    println("Publisher declared on test/thr.")
                    var count: Long = 0
                    var start = System.currentTimeMillis()
                    val number = number.toLong()
                    println("Press CTRL-C to quit...")
                    while (true) {
                        pub.put(value).getOrThrow()
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
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
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

fun main(args: Array<String>) = ZPubThr(args.isEmpty()).main(args)
