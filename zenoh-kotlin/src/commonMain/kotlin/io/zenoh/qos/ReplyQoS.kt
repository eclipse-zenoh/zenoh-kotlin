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

package io.zenoh.qos

/**
 * Quality of service settings for reply messages.
 *
 * Unlike [QoS], only [express] is meaningful for replies.
 * Priority and congestion control are fixed by the protocol for reply messages.
 *
 * @property express If true, the message is not batched in order to reduce the latency.
 */
data class ReplyQoS(
    val express: Boolean = false
) {
    /**
     * Converts this [ReplyQoS] to a full [QoS] using the protocol-fixed defaults
     * for congestion control (BLOCK) and priority (DATA).
     */
    internal fun toQoS(): QoS = QoS(
        congestionControl = CongestionControl.BLOCK,
        priority = Priority.DATA,
        express = express
    )
}
