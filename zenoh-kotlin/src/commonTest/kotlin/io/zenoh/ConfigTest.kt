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
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfigTest {
    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @Test
    fun configLoadsJsonTest() {
        // There are a bunch of different possible configurations.
        // For this test we do the following:
        // - Modifying the default endpoints.
        // - Disabling multicast scouting to avoid unintended connections.
        val clientCfg = "{\n" +
                "  \"mode\": \"peer\",\n" +
                "  \"connect\": {\n" +
                "    \"endpoints\": [\"tcp/localhost:7450\"]\n" +
                "  },\n" +
                "  \"scouting\": {\n" +
                "    \"multicast\": {\n" +
                "      \"enabled\": false\n" +
                "    }\n" +
                "  }\n" +
                "}\n"

        val serverCfg = "{\n" +
                "  \"mode\": \"peer\",\n" +
                "  \"listen\": {\n" +
                "      \"endpoints\": [\"tcp/localhost:7450\"]\n" +
                "  },\n" +
                "  \"scouting\": {\n" +
                "    \"multicast\": {\n" +
                "      \"enabled\": false\n" +
                "    }\n" +
                "  }\n" +
                "}\n"

        val sessionClient = Session.open(Config.from(clientCfg)).getOrThrow()
        val sessionServer = Session.open(Config.from(serverCfg)).getOrThrow()
        var receivedSample: Sample? = null
        val subscriber = sessionClient.declareSubscriber(TEST_KEY_EXP).with { sample -> receivedSample = sample }.res().getOrThrow()

        val value = Value("encrypted_message")
        sessionServer.put(TEST_KEY_EXP, value).res()
        Thread.sleep(1000)

        subscriber.close()
        sessionClient.close()
        sessionServer.close()

        assertNotNull(receivedSample)
        assertEquals(receivedSample!!.value, value)
    }
}
