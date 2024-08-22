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

import java.io.File
import java.nio.file.Path
import kotlinx.serialization.json.JsonElement

/**
 * # Config
 *
 * Config class to set the Zenoh configuration to be used through a [Session].
 *
 * The configuration can be specified in two different ways:
 * - By providing a file or a path to a file with the configuration
 * - By providing a raw string configuration.
 *
 * Either way, the supported formats are `yaml`, `json` and `json5`.
 *
 * ## Example:
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
 * val config = Config(config = json5Config, format = Config.Format.JSON5)
 * Session.open(config).onSuccess {
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
 * val config = Config(config = json5Config, format = Config.Format.JSON)
 * Session.open(config).onSuccess {
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
 * val config = Config(config = yamlConfig, format = Config.Format.YAML)
 * Session.open(config).onSuccess {
 *     // ...
 * }
 * ```
 *
 * Visit the [default configuration](https://github.com/eclipse-zenoh/zenoh/blob/main/DEFAULT_CONFIG.json5) for more
 * information on the Zenoh config parameters.
 *
 * @property path The path to the configuration file (supported types: JSON5, JSON and YAML).
 * @property config Raw string configuration, (supported types: JSON5, JSON and YAML).
 * @property format [Format] of the configuration.
 */
class Config private constructor(internal val path: Path? = null, internal val config: String? = null, val format: Format? = null) {

    enum class Format {
        YAML,
        JSON,
        JSON5
    }

    companion object {

        /**
         * Loads the configuration from the [File] specified.
         *
         * @param file The zenoh config file.
         */
        fun from(file: File): Config {
            return Config(path = file.toPath())
        }

        /**
         * Loads the configuration from the [Path] specified.
         *
         * @param path The zenoh config file path.
         */
        fun from(path: Path): Config {
            return Config(path)
        }

        /**
         * Loads the configuration from the [config] param. The [Format] needs to be specified explicitly.
         */
        fun from(config: String, format: Format): Config {
            return Config(config = config, format = format)
        }

        /**
         * Loads the configuration from the [jsonElement] specified.
         *
         * @param jsonElement The zenoh config as a [JsonElement].
         */
        fun from(jsonElement: JsonElement) = Config(config = jsonElement.toString(), format = Format.JSON)
    }
}
