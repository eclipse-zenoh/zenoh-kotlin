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
import com.github.ajalt.clikt.parameters.types.ulong
import io.zenoh.config.*
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.subscriber.Subscriber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path
import kotlin.system.exitProcess

class ZSubThr(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Subscriber Throughput test"
) {

    private val samples by option(
        "-s", "--samples", help = "Number of throughput measurements [default: 10]", metavar = "number"
    ).ulong().default(10u)
    private val number by option(
        "-n",
        "--number",
        help = "Number of messages in each throughput measurements [default: 100000]",
        metavar = "number"
    ).ulong().default(10000u)
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

    lateinit var subscriber: Subscriber<Unit>
    override fun run() {
        val config = loadConfig()

        "test/thr".intoKeyExpr().onSuccess {
            it.use { keyExpr ->
                println("Opening Session")
                Session.open(config).onSuccess {
                    it.use { session ->
                        subscriber =
                            session.declareSubscriber(keyExpr).reliable().with { listener(number) }.res().getOrThrow()
                        while (subscriber.isValid()) {
                            /* Keep alive the subscriber until the test is done. */
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

fun main(args: Array<String>) = ZSubThr(args.isEmpty()).main(args)
