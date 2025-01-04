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
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.QoS
import io.zenoh.sample.Sample
import java.util.concurrent.CountDownLatch

class ZPong(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh ZPong example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")
        val latch = CountDownLatch(1)

        println("Opening session...")
        val session = Zenoh.open(config).getOrThrow()
        val keyExprPing = "test/ping".intoKeyExpr().getOrThrow()
        val keyExprPong = "test/pong".intoKeyExpr().getOrThrow()

        val publisher = session.declarePublisher(keyExprPong, qos = QoS(CongestionControl.BLOCK, express = !noExpress)).getOrThrow()
        session.declareSubscriber(keyExprPing, callback = { sample: Sample -> publisher.put(sample.payload).getOrThrow() }).getOrThrow()

        latch.await()
    }


    private val noExpress: Boolean by option(
        "--no-express", help = "Express for sending data."
    ).flag(default = false)

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

fun main(args: Array<String>) {
    val zPong = ZPong(args.isEmpty())
    zPong.main(args)
}
