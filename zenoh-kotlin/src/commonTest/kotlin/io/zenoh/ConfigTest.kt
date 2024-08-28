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
import io.zenoh.protocol.into
import io.zenoh.sample.Sample
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfigTest {
    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    private val json5ClientConfig = Config.from(
        config = """
        {
            mode: "peer",
            connect: {
                endpoints: ["tcp/localhost:7450"],
            },
            scouting: {
                multicast: {
                    enabled: false,
                }
            }
        }
        """.trimIndent(), format = Config.Format.JSON5
    )

    private val json5ServerConfig = Config.from(
        config = """
        {
            mode: "peer",
            listen: {
                endpoints: ["tcp/localhost:7450"],
            },
            scouting: {
                multicast: {
                    enabled: false,
                }
            }
        }
        """.trimIndent(), format = Config.Format.JSON5
    )


    private val jsonClientConfig = Config.from(
        config = """
        {
            "mode": "peer",
            "connect": {
                "endpoints": ["tcp/localhost:7450"]
            },
            "scouting": {
                "multicast": {
                    "enabled": false
                }
            }
        }
        """.trimIndent(), format = Config.Format.JSON
    )


    private val jsonServerConfig = Config.from(
        config = """
        {
            "mode": "peer",
            "listen": {
                "endpoints": ["tcp/localhost:7450"]
            },
            "scouting": {
                "multicast": {
                    "enabled": false
                }
            }
        }
        """.trimIndent(), format = Config.Format.JSON
    )


    private val yamlClientConfig = Config.from(
        config = """
        mode: peer
        connect:
          endpoints:
            - tcp/localhost:7450
        scouting:
          multicast:
            enabled: false
        """.trimIndent(), format = Config.Format.YAML
    )


    private val yamlServerConfig = Config.from(
        config = """
        mode: peer
        listen:
          endpoints:
            - tcp/localhost:7450
        scouting:
          multicast:
            enabled: false
        """.trimIndent(), format = Config.Format.YAML
    )


    private fun runSessionTest(clientConfig: Config, serverConfig: Config) {
        runBlocking {
            val sessionClient = Session.open(clientConfig).getOrThrow()
            val sessionServer = Session.open(serverConfig).getOrThrow()

            var receivedSample: Sample? = null
            val subscriber = sessionClient.declareSubscriber(TEST_KEY_EXP, callback = { sample ->
                receivedSample = sample
            }).getOrThrow()

            val payload = "example message".into()
            sessionClient.put(TEST_KEY_EXP, payload).getOrThrow()

            delay(1000)

            subscriber.close()
            sessionClient.close()
            sessionServer.close()

            assertNotNull(receivedSample)
            assertEquals(receivedSample!!.payload, payload)
        }
    }

    @Test
    fun `test config with JSON5`() = runSessionTest(json5ClientConfig, json5ServerConfig)

    @Test
    fun `test config loads from JSON string`() = runSessionTest(jsonClientConfig, jsonServerConfig)

    @Test
    fun `test config loads from YAML string`() = runSessionTest(yamlClientConfig, yamlServerConfig)

    @Test
    fun `test config loads from JsonElement`() {
        val clientConfigJson = Json.parseToJsonElement(
            """
        {
            "mode": "peer",
            "connect": {
                "endpoints": ["tcp/localhost:7450"]
            },
            "scouting": {
                "multicast": {
                    "enabled": false
                }
            }
        }
        """.trimIndent()
        )
        val serverConfigJson = Json.parseToJsonElement(
            """
        {
            "mode": "peer",
            "listen": {
                "endpoints": ["tcp/localhost:7450"]
            },
            "scouting": {
                "multicast": {
                    "enabled": false
                }
            }
        }
        """.trimIndent()
        )
        runSessionTest(Config.from(clientConfigJson), Config.from(serverConfigJson))
    }
}
