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

import io.zenoh.qos.QoS
import io.zenoh.pubsub.AdvancedPublisher

/**
 * Configure an [AdvancedPublisher] cache.
 *
 * @property maxSamples Specify how many samples to keep for each resource.
 * @property repliesQoS The [QoS] to apply to replies.
 */
data class CacheConfig (
    val maxSamples: Long = 1,
    val repliesQoS: QoS = QoS.defaultPush
) {

    companion object {
        internal val default = CacheConfig()
    }
}
