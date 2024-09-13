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
import com.github.ajalt.clikt.parameters.types.ulong
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.subscriber.Subscriber
import kotlin.system.exitProcess

class ZSubThr(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Subscriber Throughput test"
) {
    companion object {
        private const val NANOS_TO_SEC = 1_000_000_000L
    }

    private var batchCount = 0u
    private var count = 0u
    private var startTimestampNs: Long = 0
    private var globalStartTimestampNs: Long = 0

    private fun listener(number: ULong) {
        if (batchCount > samples) {
            subscriber.close()
            report()
        }
        if (count == 0u) {
            startTimestampNs = System.nanoTime()
            if (globalStartTimestampNs == 0L) {
                globalStartTimestampNs = startTimestampNs
            }
            count++
            return
        }
        if (count < number) {
            count++
            return
        }
        val stop = System.nanoTime()
        val elapsedTimeSecs = (stop - startTimestampNs).toDouble() / NANOS_TO_SEC
        val messagesPerSec = number.toLong() / elapsedTimeSecs
        println("$messagesPerSec msgs/sec")
        batchCount++
        count = 0u
    }

    private fun report() {
        val end = System.nanoTime()
        val total = batchCount * number + count
        val elapsedTimeSecs = (end - globalStartTimestampNs).toDouble() / NANOS_TO_SEC
        val globalMessagesPerSec = total.toLong() / elapsedTimeSecs
        print("Received $total messages in $elapsedTimeSecs seconds: averaged $globalMessagesPerSec msgs/sec")
        exitProcess(0)
    }

    private lateinit var subscriber: Subscriber<Unit>

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        "test/thr".intoKeyExpr().onSuccess { keyExpr ->
            keyExpr.use {
                println("Opening Session")
                Zenoh.open(config).onSuccess { session ->
                    session.use {
                        println("Press CTRL-C to quit...")
                        subscriber =
                            session.declareSubscriber(
                                keyExpr,
                                callback = { listener(number) },
                            ).getOrThrow()
                        while (subscriber.isValid()) {/* Keep alive the subscriber until the test is done. */
                            Thread.sleep(1000)
                        }
                    }
                }
            }
        }
    }

    private val samples by option(
        "-s", "--samples", help = "Number of throughput measurements [default: 10]", metavar = "number"
    ).ulong().default(10u)
    private val number by option(
        "-n",
        "--number",
        help = "Number of messages in each throughput measurements [default: 100000]",
        metavar = "number"
    ).ulong().default(10000u)
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

fun main(args: Array<String>) = ZSubThr(args.isEmpty()).main(args)
