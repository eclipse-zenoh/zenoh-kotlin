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
 * Enable periodic queries for not yet received Samples and specify their period.
 *
 * There are two recovery modes: periodic query and heartbeat.
 *
 * Periodic query: This allows to retrieve the last Sample(s) if the last Sample(s) is/are lost.
 * It is useful for sporadic publications but useless for periodic publications with a period
 * smaller or equal to period that is .
 * Retransmission can only be achieved by [AdvancedPublisher] that enables [CacheConfig] and [MissDetectionConfig].
 *
 * @property queryPeriodMs If 0, the recovery works in heartbeat mode, otherwise in periodic query mode.
 */
data class RecoveryConfig (
    val queryPeriodMs: Long = 0
) {

    companion object {
        internal val default = RecoveryConfig()
    }
}
