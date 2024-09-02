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

import io.zenoh.exceptions.SessionException
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.protocol.into
import io.zenoh.sample.Sample
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigTest {
    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    private val json5ClientConfigString = """
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
        """.trimIndent()

    private val json5ServerConfigString = """
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
        """.trimIndent()

    private val jsonClientConfigString = """
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

    private val jsonServerConfigString = """
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

    private val yamlClientConfigString = """
        mode: peer
        connect:
          endpoints:
            - tcp/localhost:7450
        scouting:
          multicast:
            enabled: false
        """.trimIndent()

    private val yamlServerConfigString = """
        mode: peer
        listen:
          endpoints:
            - tcp/localhost:7450
        scouting:
          multicast:
            enabled: false
        """.trimIndent()

    private val json5ClientConfig = Config.fromJson5(
        config = json5ClientConfigString
    ).getOrThrow()

    private val json5ServerConfig = Config.fromJson5(
        config = json5ServerConfigString
    ).getOrThrow()

    private val jsonClientConfig = Config.fromJson(
        config = jsonClientConfigString
    ).getOrThrow()

    private val jsonServerConfig = Config.fromJson(
        config = jsonServerConfigString
    ).getOrThrow()

    private val yamlServerConfig = Config.fromYaml(
        config = yamlServerConfigString
    ).getOrThrow()

    private val yamlClientConfig = Config.fromYaml(
        config = yamlClientConfigString
    ).getOrThrow()

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
            jsonClientConfigString
        )
        val serverConfigJson = Json.parseToJsonElement(
            jsonServerConfigString
        )
        runSessionTest(
            Config.fromJsonElement(clientConfigJson).getOrThrow(),
            Config.fromJsonElement(serverConfigJson).getOrThrow()
        )
    }

    @Test
    fun `test default config`() {
        val config = Config.default()
        val session = Session.open(config).getOrThrow()
        session.close()
    }

    @Test
    fun `test config returns result failure with ill formated json`() {
        val illFormatedConfig = """
        {
            mode: "peer",
            connect: {
                endpoints: ["tcp/localhost:7450"],
        }
        """.trimIndent()
        val config = Config.fromJson(illFormatedConfig)
        assertTrue(config.isFailure)
        assertThrows<SessionException> { config.getOrThrow() }
    }

    @Test
    fun `test config returns result failure with ill formated yaml`() {
        val illFormatedConfig = """
        mode: peer
        connect:
          endpoints:
            - tcp/localhost:7450
        scouting
        """.trimIndent()
        val config = Config.fromJson(illFormatedConfig)
        assertTrue(config.isFailure)
        assertThrows<SessionException> { config.getOrThrow() }
    }

    @Test
    fun `test config loads from JSON file`() {
        val clientConfigFile = File.createTempFile("clientConfig", ".json")
        val serverConfigFile = File.createTempFile("serverConfig", ".json")

        try {
            clientConfigFile.writeText(jsonClientConfigString)
            serverConfigFile.writeText(jsonServerConfigString)

            val loadedClientConfig = Config.fromFile(clientConfigFile).getOrThrow()
            val loadedServerConfig = Config.fromFile(serverConfigFile).getOrThrow()

            runSessionTest(loadedClientConfig, loadedServerConfig)
        } finally {
            clientConfigFile.delete()
            serverConfigFile.delete()
        }
    }

    @Test
    fun `test config loads from YAML file`() {
        val clientConfigFile = File.createTempFile("clientConfig", ".yaml")
        val serverConfigFile = File.createTempFile("serverConfig", ".yaml")

        try {
            clientConfigFile.writeText(yamlClientConfigString)
            serverConfigFile.writeText(yamlServerConfigString)

            val loadedClientConfig = Config.fromFile(clientConfigFile).getOrThrow()
            val loadedServerConfig = Config.fromFile(serverConfigFile).getOrThrow()

            runSessionTest(loadedClientConfig, loadedServerConfig)
        } finally {
            clientConfigFile.delete()
            serverConfigFile.delete()
        }
    }

    @Test
    fun `test config loads from JSON5 file`() {
        val clientConfigFile = File.createTempFile("clientConfig", ".json5")
        val serverConfigFile = File.createTempFile("serverConfig", ".json5")

        try {
            clientConfigFile.writeText(json5ClientConfigString)
            serverConfigFile.writeText(json5ServerConfigString)

            val loadedClientConfig = Config.fromFile(clientConfigFile).getOrThrow()
            val loadedServerConfig = Config.fromFile(serverConfigFile).getOrThrow()

            runSessionTest(loadedClientConfig, loadedServerConfig)
        } finally {
            clientConfigFile.delete()
            serverConfigFile.delete()
        }
    }

    @Test
    fun `test config loads from JSON5 file providing path`() {
        val clientConfigFile = File.createTempFile("clientConfig", ".json5")
        val serverConfigFile = File.createTempFile("serverConfig", ".json5")

        try {
            clientConfigFile.writeText(json5ClientConfigString)
            serverConfigFile.writeText(json5ServerConfigString)

            val loadedClientConfig = Config.fromFile(clientConfigFile.toPath()).getOrThrow()
            val loadedServerConfig = Config.fromFile(serverConfigFile.toPath()).getOrThrow()

            runSessionTest(loadedClientConfig, loadedServerConfig)
        } finally {
            clientConfigFile.delete()
            serverConfigFile.delete()
        }
    }
}
