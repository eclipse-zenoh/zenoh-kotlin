//
// Copyright (c) 2026 ZettaScale Technology
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

package io.zenoh.qos

/**
 * Quality of service settings for reply messages.
 *
 * Unlike [QoS], ReplyQoS only exposes the [express] setting,
 * as the protocol uses the request's priority and congestion control for reply messages.
 *
 * @property express If true, the message is not batched in order to reduce the latency.
 */
data class ReplyQoS(
    val express: Boolean = false
) {
    internal fun toQoS(): QoS = QoS(express = express)

    companion object {
        internal val default = ReplyQoS()
    }
}
