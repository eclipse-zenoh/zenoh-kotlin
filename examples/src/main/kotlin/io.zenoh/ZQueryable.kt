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
import io.zenoh.bytes.ZBytes
import io.zenoh.handlers.Handler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.query.Query
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ntp.TimeStamp
import java.util.concurrent.CountDownLatch

class ZQueryable(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Queryable example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        val session = Zenoh.open(config).getOrThrow()
        val keyExpr = key.intoKeyExpr().getOrThrow()

        // Run the queryable example through one of the examples below:
        runChannelExample(session, keyExpr)
        // runCallbackExample(session, keyExpr)
        // runHandlerExample(session, keyExpr)

        session.close()
    }

    private fun runChannelExample(session: Session, keyExpr: KeyExpr) {
        println("Declaring Queryable on $key...")
        val queryable = session.declareQueryable(keyExpr, Channel()).getOrThrow()
        runBlocking {
            for (query in queryable.receiver) {
                val valueInfo = query.payload?.let { value -> " with value '$value'" } ?: ""
                println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
                query.reply(
                    keyExpr,
                    payload = ZBytes.from(value),
                    timestamp = TimeStamp.getCurrentTime()
                ).onFailure { println(">> [Queryable ] Error sending reply: $it") }
            }
        }
        queryable.close()
    }

    private fun runCallbackExample(session: Session, keyExpr: KeyExpr) {
        println("Declaring Queryable on $key...")
        val queryable = session.declareQueryable(keyExpr, callback = { query ->
            val valueInfo = query.payload?.let { value -> " with value '$value'" } ?: ""
            println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
            query.reply(
                keyExpr,
                payload = ZBytes.from(value),
                timestamp = TimeStamp.getCurrentTime()
            ).onFailure { println(">> [Queryable ] Error sending reply: $it") }
        }).getOrThrow()

        CountDownLatch(1).await() // A countdown latch is used here to block execution while queries are received.
                                         // Typically, this wouldn't be needed.

        queryable.close()
    }

    private fun runHandlerExample(session: Session, keyExpr: KeyExpr) {

        // Create your own handler implementation
        class ExampleHandler : Handler<Query, Unit> {
            override fun handle(t: Query) {
                val valueInfo = t.payload?.let { value -> " with value '$value'" } ?: ""
                println(">> [Queryable] Received Query '${t.selector}' $valueInfo")
                t.reply(
                    keyExpr,
                    payload = ZBytes.from(value),
                    timestamp = TimeStamp.getCurrentTime()
                ).onFailure { println(">> [Queryable ] Error sending reply: $it") }
            }

            override fun receiver() {}
            override fun onClose() {}
        }

        // Declare the queryable, providing an instance of the handler
        println("Declaring Queryable on $key...")
        val queryable = session.declareQueryable(keyExpr, handler = ExampleHandler()).getOrThrow()

        CountDownLatch(1).await() // A countdown latch is used here to block execution while queries are received.
                                         // Typically, this wouldn't be needed.

        queryable.close()
    }

    private val key by option(
        "-k",
        "--key",
        help = "The key expression to write to [default: demo/example/zenoh-kotlin-queryable]",
        metavar = "key"
    ).default("demo/example/zenoh-kotlin-queryable")
    private val value by option(
        "-v", "--value", help = "The value to reply to queries [default: \"Queryable from Kotlin!\"]", metavar = "value"
    ).default("Queryable from Kotlin!")
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val mode by option(
        "-m",
        "--mode",
        help = "The session mode. Default: peer. Possible values: [peer, client, router]",
        metavar = "mode"
    ).default("peer")
    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val listen: List<String> by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).multiple()
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZQueryable(args.isEmpty()).main(args)
