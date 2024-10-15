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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionInfoTest {

    @Test
    fun `peersZid test`() {
        val jsonConfig = """
        {
            mode: "peer",
            connect: {
                endpoints: ["tcp/localhost:7450"],
            },
        }
        """.trimIndent()

        val listenConfig = Config.fromJson("""
        {
            mode: "peer",
            listen: {
                endpoints: ["tcp/localhost:7450"],
            },
        }
        """.trimIndent()).getOrThrow()

        val sessionC = Zenoh.open(listenConfig).getOrThrow()
        val sessionA = Zenoh.open(Config.fromJson(jsonConfig).getOrThrow()).getOrThrow()
        val sessionB = Zenoh.open(Config.fromJson(jsonConfig).getOrThrow()).getOrThrow()

        val idA = sessionA.info().zid().getOrThrow()
        val idB = sessionB.info().zid().getOrThrow()
        val peers = sessionC.info().peersZid().getOrThrow()
        assertTrue(peers.contains(idA))
        assertTrue(peers.contains(idB))

        sessionA.close()
        sessionB.close()
        sessionC.close()
    }

    @Test
    fun `routersZid test`() {

        val listenerRouter = Zenoh.open(Config.fromJson("""
        {
            mode: "router",
            listen: {
                endpoints: ["tcp/localhost:7450"],
            },
        }
        """.trimIndent()).getOrThrow()).getOrThrow()

        val connectedRouterA = Zenoh.open(Config.fromJson("""
        {
            mode: "router",
            connect: {
                endpoints: ["tcp/localhost:7450"],
            },
            listen: {
                endpoints: ["tcp/localhost:7451"],
            },
        }
        """.trimIndent()).getOrThrow()).getOrThrow()

        val connectedRouterB = Zenoh.open(Config.fromJson("""
        {
            mode: "router",
            connect: {
                endpoints: ["tcp/localhost:7450"],
            },
            listen: {
                endpoints: ["tcp/localhost:7452"],
            },
        }
        """.trimIndent()).getOrThrow()).getOrThrow()

        val idA = connectedRouterA.info().zid().getOrThrow()
        val idB = connectedRouterB.info().zid().getOrThrow()

        val routers = listenerRouter.info().routersZid().getOrThrow()

        assertTrue(routers.contains(idA))
        assertTrue(routers.contains(idB))

        connectedRouterA.close()
        connectedRouterB.close()
        listenerRouter.close()
    }

    @Test
    fun `zid test`() {
        val jsonConfig = """
        {
            id: "123456",
        }
        """.trimIndent()

        val session = Zenoh.open(Config.fromJson(jsonConfig).getOrThrow()).getOrThrow()
        assertEquals("123456", session.info().zid().getOrThrow().toString())
        session.close()
    }
}
