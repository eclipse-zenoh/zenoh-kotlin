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
 * Configure query for historical data.
 *
 * @property detectLatePublishers Enable detection of late joiner publishers and query for their historical data.
 * Late joiner detection can only be achieved for [AdvancedPublisher] that enable publisher_detection.
 * History can only be retransmitted by [AdvancedPublisher] that enable cache.
 * @property maxSamples Specify how many samples to query for each resource.
 * @property maxAgeSeconds Specify the maximum age of samples to query. 0.0 means that age filtering is not applied.
 */
data class HistoryConfig (
    val detectLatePublishers: Boolean = false,
    val maxSamples: Long? = null,
    val maxAgeSeconds: Double? = null
) {

    companion object {
        internal val default = HistoryConfig()
    }
}
