//
// Copyright (c) 2025 ZettaScale Technology
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

import io.zenoh.annotations.Unstable
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.qos.CongestionControl
import io.zenoh.pubsub.SampleMissListener

/**
 * Heartbeat sample miss detection mode
 */
@Unstable
sealed class HeartbeatMode {
    /**
     * Allow last sample miss detection through periodic heartbeat.
     * Periodically send the last published Sample's sequence number to allow last sample recovery.
     */
    data class PeriodicHeartbeat(val milliseconds: Long = 0) : HeartbeatMode()

    /**
     * Allow last sample miss detection through sporadic heartbeat.
     * Each period, the last published Sample's sequence number is sent with [CongestionControl.BLOCK]
     * but only if it changed since last period.
     */
    data class SporadicHeartbeat(val milliseconds: Long = 0) : HeartbeatMode()
}

/**
 * Configuration for sample miss detection
 * Enabling sample miss detection allows [AdvancedSubscriber] to detect missed samples through
 * [SampleMissListener] and to recover missed samples through [RecoveryConfig] in heartbeat mode.
 *
 * @property heartbeat Enable heartbeat functionality for sample miss detection
 */
@Unstable
data class MissDetectionConfig (
    val heartbeat: HeartbeatMode? = null,
) {
    companion object {
        internal val default = MissDetectionConfig()
    }
}

