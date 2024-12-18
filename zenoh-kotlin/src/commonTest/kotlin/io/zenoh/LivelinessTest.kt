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

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.query.Reply
import io.zenoh.sample.Sample
import kotlin.test.Test
import kotlin.test.assertNotNull

class LivelinessTest {

    @Test
    fun `get liveliness test`() {
        val sessionA = Zenoh.open(Config.default()).getOrThrow()
        val sessionB = Zenoh.open(Config.default()).getOrThrow()

        val token = sessionA.liveliness().declareToken("test/liveliness".intoKeyExpr().getOrThrow()).getOrThrow()

        Thread.sleep(1000)
        var receivedReply: Reply? = null
        sessionB.liveliness().get(keyExpr = "test/**".intoKeyExpr().getOrThrow(), callback = { reply: Reply ->
            receivedReply = reply
        })

        Thread.sleep(1000)

        assertNotNull(receivedReply)
        token.close()
        sessionA.close()
        sessionB.close()
    }

    @Test
    fun `liveliness subscriber test`() {
        val sessionA = Zenoh.open(Config.default()).getOrThrow()

        var receivedSample: Sample? = null

        val subscriber = sessionA.liveliness().declareSubscriber("test/**".intoKeyExpr().getOrThrow(), callback = {sample -> receivedSample = sample}).getOrThrow()

        val sessionB = Zenoh.open(Config.default()).getOrThrow()
        val token = sessionB.liveliness().declareToken("test/liveliness".intoKeyExpr().getOrThrow()).getOrThrow()

        Thread.sleep(1000)

        assertNotNull(receivedSample)

        token.close()
        subscriber.close()
        sessionA.close()
        sessionB.close()
    }
}
