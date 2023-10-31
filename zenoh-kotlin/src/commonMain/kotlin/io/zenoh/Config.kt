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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


/**
 * Config class to set the Zenoh configuration to be used through a [Session].
 *
 * @property path The path to the configuration file.
 * @constructor Create empty Config
 */
class Config private constructor(internal val path: Path? = null) {

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
    }
}

@Serializable
data class ConfigData(
    @SerialName("adminspace")
    var adminspace: Adminspace?,
    @SerialName("connect")
    var connect: Connect?,
    @SerialName("listen")
    var listen: Listen?,
    @SerialName("metadata")
    var metadata: Metadata?,
    @SerialName("mode")
    var mode: String?,
    @SerialName("queries_default_timeout")
    var queriesDefaultTimeout: Int?,
    @SerialName("routing")
    var routing: Routing?,
    @SerialName("scouting")
    var scouting: Scouting?,
    @SerialName("timestamping")
    var timestamping: Timestamping?,
    @SerialName("transport")
    var transport: Transport?
)

@Serializable
data class Adminspace(
    @SerialName("permissions")
    var permissions: Permissions
)

@Serializable
data class Connect(
    @SerialName("endpoints")
    var endpoints: List<String>
)

@Serializable
data class Listen(
    @SerialName("endpoints")
    var endpoints: List<String>
)

@Serializable
data class Metadata(
    @SerialName("location")
    var location: String,
    @SerialName("name")
    var name: String
)

@Serializable
data class Routing(
    @SerialName("peer")
    var peer: Peer,
    @SerialName("router")
    var router: Router
)

@Serializable
data class Scouting(
    @SerialName("delay")
    var delay: Int,
    @SerialName("gossip")
    var gossip: Gossip,
    @SerialName("multicast")
    var multicast: Multicast,
    @SerialName("timeout")
    var timeout: Int
)

@Serializable
data class Timestamping(
    @SerialName("drop_future_timestamp")
    var dropFutureTimestamp: Boolean,
    @SerialName("enabled")
    var enabled: Enabled
)

@Serializable
data class Transport(
    @SerialName("auth")
    var auth: Auth,
    @SerialName("link")
    var link: Link,
    @SerialName("qos")
    var qos: Qos,
    @SerialName("shared_memory")
    var sharedMemory: SharedMemory,
    @SerialName("unicast")
    var unicast: Unicast
)

@Serializable
data class Permissions(
    @SerialName("read")
    var read: Boolean,
    @SerialName("write")
    var write: Boolean
)

@Serializable
data class Peer(
    @SerialName("mode")
    var mode: String
)

@Serializable
data class Router(
    @SerialName("peers_failover_brokering")
    var peersFailoverBrokering: Boolean
)

@Serializable
data class Gossip(
    @SerialName("autoconnect")
    var autoconnect: Autoconnect,
    @SerialName("enabled")
    var enabled: Boolean,
    @SerialName("multihop")
    var multihop: Boolean
)

@Serializable
data class Multicast(
    @SerialName("address")
    var address: String,
    @SerialName("autoconnect")
    var autoconnect: Autoconnect,
    @SerialName("enabled")
    var enabled: Boolean,
    @SerialName("interface")
    var interfaceX: String,
    @SerialName("listen")
    var listen: Boolean
)

@Serializable
data class Autoconnect(
    @SerialName("peer")
    var peer: String,
    @SerialName("router")
    var router: String
)

@Serializable
data class Enabled(
    @SerialName("client")
    var client: Boolean,
    @SerialName("peer")
    var peer: Boolean,
    @SerialName("router")
    var router: Boolean
)

@Serializable
data class Auth(
    @SerialName("pubkey")
    var pubkey: Pubkey,
    @SerialName("usrpwd")
    var usrpwd: Usrpwd
)

@Serializable
data class Link(
    @SerialName("compression")
    var compression: Compression,
    @SerialName("rx")
    var rx: Rx,
    @SerialName("tls")
    var tls: Tls,
    @SerialName("tx")
    var tx: Tx
)

@Serializable
data class Qos(
    @SerialName("enabled")
    var enabled: Boolean
)

@Serializable
data class SharedMemory(
    @SerialName("enabled")
    var enabled: Boolean
)

@Serializable
data class Unicast(
    @SerialName("accept_pending")
    var acceptPending: Int?,
    @SerialName("accept_timeout")
    var acceptTimeout: Int?,
    @SerialName("lowlatency")
    var lowlatency: Boolean?,
    @SerialName("max_links")
    var maxLinks: Int?,
    @SerialName("max_sessions")
    var maxSessions: Int?
)

@Serializable
data class Pubkey(
    @SerialName("key_size")
    var keySize: Int?,
    @SerialName("known_keys_file")
    var knownKeysFile: String?,
    @SerialName("private_key_file")
    var privateKeyFile: String?,
    @SerialName("private_key_pem")
    var privateKeyPem: String?,
    @SerialName("public_key_file")
    var publicKeyFile: String?,
    @SerialName("public_key_pem")
    var publicKeyPem: String?
)

@Serializable
data class Usrpwd(
    @SerialName("dictionary_file")
    var dictionaryFile: String?,
    @SerialName("password")
    var password: String?,
    @SerialName("user")
    var user: String?
)

@Serializable
data class Compression(
    @SerialName("enabled")
    var enabled: Boolean
)

@Serializable
data class Rx(
    @SerialName("buffer_size")
    var bufferSize: Int,
    @SerialName("max_message_size")
    var maxMessageSize: Int
)

@Serializable
data class Tls(
    @SerialName("client_auth")
    var clientAuth: Boolean,
    @SerialName("client_certificate")
    var clientCertificate: String?,
    @SerialName("client_private_key")
    var clientPrivateKey: String?,
    @SerialName("root_ca_certificate")
    var rootCaCertificate: String?,
    @SerialName("server_certificate")
    var serverCertificate: String?,
    @SerialName("server_name_verification")
    var serverNameVerification: String?,
    @SerialName("server_private_key")
    var serverPrivateKey: String?
)

@Serializable
data class Tx(
    @SerialName("batch_size")
    var batchSize: Int,
    @SerialName("keep_alive")
    var keepAlive: Int,
    @SerialName("lease")
    var lease: Int,
    @SerialName("queue")
    var queue: Queue,
    @SerialName("sequence_number_resolution")
    var sequenceNumberResolution: String
)

@Serializable
data class Queue(
    @SerialName("backoff")
    var backoff: Int,
    @SerialName("size")
    var size: Size
)

@Serializable
data class Size(
    @SerialName("background")
    var background: Int,
    @SerialName("control")
    var control: Int,
    @SerialName("data")
    var `data`: Int,
    @SerialName("data_high")
    var dataHigh: Int,
    @SerialName("data_low")
    var dataLow: Int,
    @SerialName("interactive_high")
    var interactiveHigh: Int,
    @SerialName("interactive_low")
    var interactiveLow: Int,
    @SerialName("real_time")
    var realTime: Int
)
