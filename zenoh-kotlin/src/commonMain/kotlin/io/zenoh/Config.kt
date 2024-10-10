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

import io.zenoh.jni.JNIConfig
import java.io.File
import java.nio.file.Path
import kotlinx.serialization.json.JsonElement

/**
 * # Config
 *
 * Config class to set the Zenoh configuration to be used through a [io.zenoh.Session].
 *
 * The configuration can be specified in two different ways:
 * - By providing a file or a path to a file with the configuration
 * - By providing a raw string configuration.
 *
 * Either way, the supported formats are `yaml`, `json` and `json5`.
 *
 * A default configuration can be loaded using [Config.default].
 *
 * ## Examples:
 *
 * ### Loading default config:
 *
 * ```kotlin
 * val config = Config.default()
 * Zenoh.open(config).onSuccess {
 *   // ...
 * }
 * ```
 * ### Loading from file
 *
 * Using [Path]:
 * ```kotlin
 * val config = Config.fromFile(Path("example/path/config.json5")).getOrThrow()
 * Zenoh.open(config).onSuccess {
 *   // ...
 * }
 * ```
 *
 * or alternatively, using [File]
 * ```kotlin
 * val config = Config.fromFile(File("example/path/config.json5")).getOrThrow()
 * Zenoh.open(config).onSuccess {
 *   // ...
 * }
 * ```
 * ### Embedded string configuration
 * - Json5
 * ```kotlin
 * val json5config = """
 *     {
 *         mode: "peer",
 *         connect: {
 *             endpoints: ["tcp/localhost:7450"],
 *         },
 *         scouting: {
 *             multicast: {
 *                 enabled: false,
 *             }
 *         }
 *     }
 *     """.trimIndent()
 * val config = Config.fromJson5(json5config).getOrThrow()
 * Zenoh.open(config).onSuccess {
 *     // ...
 * }
 * ```
 *
 * - Json
 * ```kotlin
 * val jsonConfig = """
 *     {
 *         mode: "peer",
 *         listen: {
 *             endpoints: ["tcp/localhost:7450"],
 *         },
 *         scouting: {
 *             multicast: {
 *                 enabled: false,
 *             }
 *         }
 *     }
 *     """.trimIndent()
 * val config = Config.fromJson(jsonConfig).getOrThrow()
 * Zenoh.open(config).onSuccess {
 *     // ...
 * }
 * ```
 *
 * - Yaml
 * ```kotlin
 * val yamlConfig = """
 *     mode: peer
 *     connect:
 *       endpoints:
 *         - tcp/localhost:7450
 *     scouting:
 *       multicast:
 *         enabled: false
 *     """.trimIndent()
 * val config = Config.fromYaml(yamlConfig).getOrThrow()
 * Zenoh.open(config).onSuccess {
 *     // ...
 * }
 * ```
 *
 * Visit the [default configuration](https://github.com/eclipse-zenoh/zenoh/blob/main/DEFAULT_CONFIG.json5) for more
 * information on the Zenoh config parameters.
 */
class Config internal constructor(internal val jniConfig: JNIConfig) {

    companion object {

        private const val CONFIG_ENV = "ZENOH_CONFIG"

        /**
         * Returns the default config.
         */
        fun default(): Config {
            return JNIConfig.loadDefaultConfig()
        }

        /**
         * Loads the configuration from the [File] specified.
         *
         * @param file The Zenoh config file. Supported types are: JSON, JSON5 and YAML.
         *   Note the format is determined after the file extension.
         * @return A result with the [Config].
         */
        fun fromFile(file: File): Result<Config> {
            return JNIConfig.loadConfigFile(file)
        }

        /**
         * Loads the configuration from the [Path] specified.
         *
         * @param path Path to the Zenoh config file. Supported types are: JSON, JSON5 and YAML.
         *   Note the format is determined after the file extension.
         * @return A result with the [Config].
         */
        fun fromFile(path: Path): Result<Config> {
            return JNIConfig.loadConfigFile(path)
        }

        /**
         * Loads the configuration from json-formatted string.
         *
         * Example:
         * ```kotlin
         * val config = Config.fromJson(
         *     config = """
         *     {
         *         "mode": "peer",
         *         "connect": {
         *             "endpoints": ["tcp/localhost:7450"]
         *         },
         *         "scouting": {
         *             "multicast": {
         *                 "enabled": false
         *             }
         *         }
         *     }
         *     """.trimIndent()
         * ).getOrThrow()
         *
         * Zenoh.open(config).onSuccess {
         *  // ...
         * }
         * ```
         *
         * Visit the [default configuration](https://github.com/eclipse-zenoh/zenoh/blob/main/DEFAULT_CONFIG.json5) for more
         * information on the Zenoh config parameters.
         *
         * @param config Json formatted config.
         * @return A result with the [Config].
         */
        fun fromJson(config: String): Result<Config> {
            return JNIConfig.loadJsonConfig(config)
        }

        /**
         * Loads the configuration from json5-formatted string.
         *
         * Example:
         * ```kotlin
         * val config = Config.fromJson5(
         *     config = """
         *     {
         *         mode: "peer",
         *         connect: {
         *             endpoints: ["tcp/localhost:7450"],
         *         },
         *         scouting: {
         *             multicast: {
         *                 enabled: false,
         *             }
         *         }
         *     }
         *     """.trimIndent()
         * ).getOrThrow()
         *
         * Zenoh.open(config).onSuccess {
         *  // ...
         * }
         * ```
         *
         * Visit the [default configuration](https://github.com/eclipse-zenoh/zenoh/blob/main/DEFAULT_CONFIG.json5) for more
         * information on the Zenoh config parameters.
         *
         * @param config Json5 formatted config
         * @return A result with the [Config].
         */
        fun fromJson5(config: String): Result<Config> {
            return JNIConfig.loadJson5Config(config)
        }

        /**
         * Loads the configuration from yaml-formatted string.
         *
         * Example:
         * ```kotlin
         * val config = Config.fromYaml(
         *     config = """
         *     mode: peer
         *     connect:
         *       endpoints:
         *         - tcp/localhost:7450
         *     scouting:
         *       multicast:
         *         enabled: false
         *     """.trimIndent()
         * ).getOrThrow()
         *
         * Zenoh.open(config).onSuccess {
         *  // ...
         * }
         * ```
         *
         * Visit the [default configuration](https://github.com/eclipse-zenoh/zenoh/blob/main/DEFAULT_CONFIG.json5) for more
         * information on the Zenoh config parameters.
         *
         * @param config Yaml formatted config
         * @return A result with the [Config].
         */
        fun fromYaml(config: String): Result<Config> {
            return JNIConfig.loadYamlConfig(config)
        }

        /**
         * Loads the configuration from the [jsonElement] specified.
         *
         * @param jsonElement The zenoh config as a [JsonElement].
         */
        fun fromJsonElement(jsonElement: JsonElement): Result<Config> {
            return JNIConfig.loadJsonConfig(jsonElement.toString())
        }

        /**
         * Loads the configuration from the env variable [CONFIG_ENV].
         *
         * @return A result with the config.
         */
        fun fromEnv(): Result<Config> = runCatching {
            val envValue = System.getenv(CONFIG_ENV)
            if (envValue != null) {
                return fromFile(File(envValue))
            } else {
                throw Exception("Couldn't load env variable: $CONFIG_ENV.")
            }
        }
    }

    /**
     * Returns the json value associated to the [key].
     */
    fun getJson(key: String): Result<String> {
        return jniConfig.getJson(key)
    }

    /**
     * Inserts a json5 value associated to the [key] into the Config.
     *
     * Example:
     * ```kotlin
     * val config = Config.default()
     *
     * // ...
     * val scouting = """
     *     {
     *         multicast: {
     *             enabled: true,
     *         }
     *     }
     * """.trimIndent()
     * config.insertJson5("scouting", scouting).getOrThrow()
     * ```
     *
     * @return A result with the status of the operation.
     */
    fun insertJson5(key: String, value: String): Result<Unit> {
        return jniConfig.insertJson5(key, value)
    }

    protected fun finalize() {
        jniConfig.close()
    }
}
