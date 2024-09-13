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
import io.zenoh.scouting.WhatAmI
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

class ZScout : CliktCommand(
    help = "Zenoh Scouting example"
) {
    override fun run() {

        Zenoh.initLogFromEnvOr("error")

        println("Scouting...")

        val scout = Zenoh.scout(channel = Channel(), whatAmI = setOf(WhatAmI.Peer, WhatAmI.Router)).getOrThrow()
        runBlocking {
            for (hello in scout.receiver) {
                println(hello)
            }
        }

        scout.stop()
    }
}

fun main(args: Array<String>) = ZScout().main(args)
