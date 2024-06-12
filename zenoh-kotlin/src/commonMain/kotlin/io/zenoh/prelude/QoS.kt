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

package io.zenoh.prelude

/**
 * Quality of service settings used to send zenoh message.
 *
 * @property express If true, the message is not batched in order to reduce the latency.
 * @property congestionControl [CongestionControl] policy used for the message.
 * @property priority [Priority] policy used for the message.
 */
class QoS internal constructor(
    internal val express: Boolean,
    internal val congestionControl: CongestionControl,
    internal val priority: Priority
) {

    internal constructor(express: Boolean, congestionControl: Int, priority: Int) : this(
        express, CongestionControl.fromInt(congestionControl), Priority.fromInt(priority)
    )

    /**
     * Returns priority of the message.
     */
    fun priority(): Priority = priority

    /**
     * Returns congestion control setting of the message.
     */
    fun congestionControl(): CongestionControl = congestionControl

    /**
     * Returns express flag. If it is true, the message is not batched to reduce the latency.
     */
    fun isExpress(): Boolean = express

    companion object {
        fun default() = QoS(false, CongestionControl.default(), Priority.default())
    }

    internal class Builder(
        private var express: Boolean = false,
        private var congestionControl: CongestionControl = CongestionControl.default(),
        private var priority: Priority = Priority.default(),
    ) {

        fun express(value: Boolean) = apply { this.express = value }

        fun priority(priority: Priority) = apply { this.priority = priority }

        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.congestionControl = congestionControl }

        fun build() = QoS(express, congestionControl, priority)
    }
}
