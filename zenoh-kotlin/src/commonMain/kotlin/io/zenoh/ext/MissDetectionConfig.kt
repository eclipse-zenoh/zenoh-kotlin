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

package io.zenoh.ext

import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.qos.CongestionControl

/**
 * Configuration for sample miss detection
 * Enabling sample miss detection allows [AdvancedSubscriber] to detect missed samples through [AdvancedSubscriber]
 * sample miss listener and to recover missed samples through [RecoveryConfig] in heartbeat mode.
 *
 * There are two heartbeat modes: sporadic and periodic.
 *
 * Periodic heartbeat: periodically send the last published Sample's sequence number to allow last sample recovery.
 *
 * Sporadic heartbeat: each period, the last published Sample's sequence number is sent with [CongestionControl.BLOCK]
 * but only if it changed since last period.
 *
 * @property heartbeatMs Period argument for heartbeat. If 0, the sample miss detection is turned off.
 * @property heartbeatIsSporadic Determines if period is sporadic or periodic.
 *
 */
data class MissDetectionConfig (
    val heartbeatMs: Long = 0,
    val heartbeatIsSporadic: Boolean = false
) {

    companion object {
        internal val default = MissDetectionConfig()
    }
}
