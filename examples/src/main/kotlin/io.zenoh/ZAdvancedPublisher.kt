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
import io.zenoh.bytes.ZBytes
import io.zenoh.ext.CacheConfig
import io.zenoh.ext.MissDetectionConfig
import io.zenoh.keyexpr.intoKeyExpr

class ZAdvancedPublisher(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Advanced Publisher example"
) {
    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        println("Opening session...")
        val session = Zenoh.open(config).getOrThrow()
        val keyExpr = key.intoKeyExpr().getOrThrow()

        val maxSamples = history.toLong()
        val cacheConfig = CacheConfig(maxSamples)

        val sampleMissDetection = MissDetectionConfig(500)

        println("Declaring AdvancedPublisher on '$keyExpr'...")
        val publisher = session.declareAdvancedPublisher(
            keyExpr,
            cacheConfig = cacheConfig,
            sampleMissDetection = sampleMissDetection,
            publisherDetection = true
        ).getOrThrow()

        println("Press CTRL-C to quit...")
        val attachment = attachment?.toByteArray()

        var idx = 0
        while (true) {
            Thread.sleep(1000)
            val payload = "[${
                idx.toString().padStart(4, ' ')
            }] $value"
            println(
                "Putting Data ('$keyExpr': '$payload')..."
            )
            attachment?.let {
                publisher.put(ZBytes.from(payload), attachment = ZBytes.from(it))
            } ?: let { publisher.put(ZBytes.from(payload)) }
            idx++
        }
    }


    private val key by option(
        "-k", "--key", help = "The key expression to write to [default: demo/example/zenoh-kotlin-pub]", metavar = "key"
    ).default("demo/example/zenoh-kotlin-pub")
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
    private val value by option(
        "-v", "--value", help = "The value to write. [Default: \"Pub from Kotlin!\"]", metavar = "value"
    ).default("Pub from Kotlin!")
    private val history by option(
        "-i", help = "The number of publications to keep in cache. [Default: 1]", metavar = "history"
    ).default("1")
    private val attachment by option(
        "-a",
        "--attach",
        help = "The attachments to add to each put. The key-value pairs are &-separated, and = serves as the separator between key and value.",
        metavar = "attach"
    )
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZAdvancedPublisher(args.isEmpty()).main(args)
