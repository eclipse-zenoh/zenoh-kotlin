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

import io.zenoh.pubsub.AdvancedPublisher

/**
 * Sample recovery mode.
 */
sealed class RecoveryMode {
    /**
     * Enable periodic queries for not yet received Samples and specify their period.
     *
     * This allows to retrieve the last Sample(s) if the last Sample(s) is/are lost.
     * So it is useful for sporadic publications but useless for periodic publications
     * with a period smaller or equal to this period.
     * Retransmission can only be achieved by [AdvancedPublisher] that enable cache and
     * sample miss detection.
     */
    data class PeriodicQuery(val milliseconds: Long = 0) : RecoveryMode()

    /**
     * Subscribe to heartbeats of [AdvancedPublisher].
     *
     * This allows to receive the last published Sample's sequence number and check for misses.
     * Heartbeat subscriber must be paired with [AdvancedPublisher] that enable cache and
     * [HeartbeatMode.PeriodicHeartbeat] or [HeartbeatMode.SporadicHeartbeat].
     */
    object Heartbeat : RecoveryMode()
}

/**
 * Configure retransmission.
 */
data class RecoveryConfig (
    val mode: RecoveryMode,
) {
}