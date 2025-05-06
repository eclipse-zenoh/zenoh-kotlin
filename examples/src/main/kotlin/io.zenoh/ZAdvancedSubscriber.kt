//
// Copyright (c) 2025 ZettaScale Technology
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
import io.zenoh.ext.HistoryConfig
import io.zenoh.ext.RecoveryConfig
import io.zenoh.ext.RecoveryMode
import io.zenoh.handlers.Handler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.sample.Sample
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

class ZAdvancedSubscriber(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Advanced Subscriber example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        println("Opening session...")
        val session = Zenoh.open(config).getOrThrow()
        val keyExpr = key.intoKeyExpr().getOrThrow()

        println("Declaring Advanced Subscriber on '$keyExpr'...")

        runChannelExample(session, keyExpr)
    }

    private fun runChannelExample(session: Session, keyExpr: KeyExpr) {
        val historyConfig = HistoryConfig(true)
        val recoveryConfig = RecoveryConfig(RecoveryMode.Heartbeat)

        val subscriber = session.declareAdvancedSubscriber(
            keyExpr, historyConfig, recoveryConfig, true, Channel()).getOrThrow()

        runBlocking {
            for (sample in subscriber.receiver) {
                println(">> [Advanced Subscriber] Received ${sample.kind} ('${sample.keyExpr}': '${sample.payload}'" + "${
                    sample.attachment?.let {
                        ", with attachment: $it"
                    } ?: ""
                })")
            }
        }

        subscriber.close()
    }

    private fun runCallbackExample(session: Session, keyExpr: KeyExpr) {
        val historyConfig = HistoryConfig(true)
        val recoveryConfig = RecoveryConfig(RecoveryMode.Heartbeat)

        val subscriber = session.declareAdvancedSubscriber(
            keyExpr, historyConfig, recoveryConfig, true, callback = { sample ->
                println(">> [Advanced Subscriber] Received ${sample.kind} ('${sample.keyExpr}': '${sample.payload}'" + "${
                    sample.attachment?.let {
                        ", with attachment: $it"
                    } ?: ""
                })")
        }).getOrThrow()


        CountDownLatch(1).await() // A countdown latch is used here to block execution while samples are received.
                                         // Typically, this wouldn't be needed.
        subscriber.close()
    }

    private fun runHandlerExample(session: Session, keyExpr: KeyExpr) {
        class ExampleHandler: Handler<Sample, Unit> {
            override fun handle(t: Sample) {
                println(">> [Advanced Subscriber] Received ${t.kind} ('${t.keyExpr}': '${t.payload}'" + "${
                    t.attachment?.let {
                        ", with attachment: $it"
                    } ?: ""
                })")
            }

            override fun receiver() {}
            override fun onClose() {}
        }

        val historyConfig = HistoryConfig(true)
        val recoveryConfig = RecoveryConfig(RecoveryMode.Heartbeat)

        val subscriber = session.declareAdvancedSubscriber(
            keyExpr, historyConfig, recoveryConfig, true, handler = ExampleHandler()).getOrThrow()

        CountDownLatch(1).await() // A countdown latch is used here to block execution while samples are received.
                                         // Typically, this wouldn't be needed.
        subscriber.close()
    }

    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val key by option(
        "-k", "--key", help = "The key expression to subscribe to [default: demo/example/**]", metavar = "key"
    ).default("demo/example/**")
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

fun main(args: Array<String>) = ZAdvancedSubscriber(args.isEmpty()).main(args)
