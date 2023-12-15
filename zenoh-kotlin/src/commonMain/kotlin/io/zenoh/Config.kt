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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement


/**
 * Config class to set the Zenoh configuration to be used through a [Session].
 *
 * @property path The path to the configuration file.
 * @constructor Create empty Config
 */
class Config private constructor(internal val path: Path? = null, internal val jsonConfig: JsonElement? = null) {

    companion object {

        /**
         * Loads the default zenoh configuration.
         */
        fun default(): Config {
            return Config()
        }

        /**
         * Loads the configuration from the [File] specified.
         *
         * @param file The zenoh config file.
         */
        fun from(file: File): Config {
            return Config(file.toPath())
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
         * Loads the configuration from the [json] specified.
         *
         * @param json The zenoh raw zenoh config.
         */
        fun from(json: String): Config {
            return Config(jsonConfig = Json.decodeFromString(json))
        }
    }

    constructor(jsonConfig: JsonElement) : this(null, jsonConfig = jsonConfig)
}
