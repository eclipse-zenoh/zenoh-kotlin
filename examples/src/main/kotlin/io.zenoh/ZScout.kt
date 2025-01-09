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
import io.zenoh.config.WhatAmI
import io.zenoh.handlers.Handler
import io.zenoh.scouting.Hello
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

class ZScout : CliktCommand(
    help = "Zenoh Scouting example"
) {
    override fun run() {

        Zenoh.initLogFromEnvOr("error")

        // Run the scout example with one of the implementations below:
        runChannelExample()
        // runCallbackExample()
        // runHandlerExample()
    }

    private fun runChannelExample() {
        println("Scouting...")

        val scout = Zenoh.scout(channel = Channel(), whatAmI = setOf(WhatAmI.Peer, WhatAmI.Router)).getOrThrow()
        runBlocking {
            for (hello in scout.receiver) {
                println(hello)
            }
        }

        scout.stop()
    }

    private fun runCallbackExample() {
        println("Scouting...")

        val scout = Zenoh.scout(whatAmI = setOf(WhatAmI.Peer, WhatAmI.Router), callback = ::println).getOrThrow()

        CountDownLatch(1).await() // A countdown latch is used here to block execution while queries are received.
                                         // Typically, this wouldn't be needed.
        scout.stop()
    }

    private fun runHandlerExample() {

        // Create your own Handler implementation:
        class ExampleHandler: Handler<Hello, Unit> {
            override fun handle(t: Hello) = println(t)

            override fun receiver() {}

            override fun onClose() {}
        }

        println("Scouting...")

        // Declare the scout with the handler
        val scout = Zenoh.scout(whatAmI = setOf(WhatAmI.Peer, WhatAmI.Router), handler = ExampleHandler()).getOrThrow()

        CountDownLatch(1).await() // A countdown latch is used here to block execution while queries are received.
                                  // Typically, this wouldn't be needed.
        scout.stop()
    }
}

fun main(args: Array<String>) = ZScout().main(args)
