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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.Path

@Serializable
data class ConfigData(
    @SerialName("connect") var connect: Connect? = null,
    @SerialName("listen") var listen: Listen? = null,
    @SerialName("mode") var mode: String? = null,
    @SerialName("scouting") var scouting: Scouting? = null,
)

@Serializable
data class Connect(
    @SerialName("endpoints") var endpoints: List<String>
)

@Serializable
data class Listen(
    @SerialName("endpoints") var endpoints: List<String>
)

@Serializable
data class Scouting(
    @SerialName("multicast") var multicast: Multicast,
)

@Serializable
data class Multicast(
    @SerialName("enabled") var enabled: Boolean,
)

internal fun loadConfig(
    emptyArgs: Boolean,
    configFile: String?,
    connectEndpoints: List<String>,
    listenEndpoints: List<String>,
    noMulticastScouting: Boolean,
    mode: String?
): Config {
    return if (emptyArgs) {
        Config.default()
    } else {
        configFile?.let {
            Config.fromFile(path = Path(it)).getOrThrow()
        } ?: run {
            val connect = Connect(connectEndpoints)
            val listen = Listen(listenEndpoints)
            val scouting = Scouting(Multicast(!noMulticastScouting))
            val configData = ConfigData(connect, listen, mode, scouting)
            val jsonConfig = Json.encodeToJsonElement(configData)
            Config.fromFile(jsonConfig).getOrThrow()
        }
    }
}
