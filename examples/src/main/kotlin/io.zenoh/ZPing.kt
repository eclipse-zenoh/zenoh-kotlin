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
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import io.zenoh.bytes.ZBytes
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.QoS
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

class ZPing(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Ping example"
) {
    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        println("Opening session...")
        val session = Zenoh.open(config).getOrThrow()
        val keyExprPing = "test/ping".intoKeyExpr().getOrThrow()
        val keyExprPong = "test/pong".intoKeyExpr().getOrThrow()

        val sub = session.declareSubscriber(keyExprPong, Channel()).getOrThrow()
        val publisher = session.declarePublisher(keyExprPing, qos = QoS(CongestionControl.BLOCK, express = !noExpress)).getOrThrow()

        val data = ByteArray(payloadSize)
        for (i in 0..<payloadSize) {
            data[i] = (i % 10).toByte()
        }
        val payload = ZBytes.from(data)
        val samples = arrayListOf<Long>()

        // -- warmup --
        println("Warming up for $warmup...")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < warmup) {
            publisher.put(payload).getOrThrow()
            runBlocking { sub.receiver.receive() }
        }

        for (x in 0..n ) {
            val writeTime = System.nanoTime()
            publisher.put(payload).getOrThrow()
            runBlocking { sub.receiver.receive() }
            val ts = (System.nanoTime() - writeTime) / 1_000 //convert to microseconds
            samples.add(ts)
        }

        for (x in samples.withIndex()) {
            println("$payloadSize bytes: seq=${x.index} rtt=${x.value}µs lat=${x.value / 2}µs")
        }
    }


    private val payloadSize by argument(
        "payload_size",
        help = "Sets the size of the payload to publish [Default: 8]"
    ).int().default(8)
    private val noExpress: Boolean by option(
        "--no-express", help = "Express for sending data."
    ).flag(default = false)
    private val warmup: Double by option(
        "-w",
        "--warmup",
        metavar = "warmup",
        help = "The number of seconds to warm up (double) [default: 1]"
    ).double().default(1.0)
    private val n: Int by option(
        "-n",
        "--samples",
        metavar = "samples",
        help = "The number of round-trips to measure [default: 100]"
    ).int().default(100)

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

fun main(args: Array<String>) = ZPing(args.isEmpty()).main(args)
