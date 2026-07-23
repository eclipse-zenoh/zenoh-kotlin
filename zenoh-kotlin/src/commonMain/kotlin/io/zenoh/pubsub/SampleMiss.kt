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

package io.zenoh.pubsub

import io.zenoh.annotations.Unstable
import io.zenoh.config.EntityGlobalId

/**
 * # SampleMiss
 * A report of samples missed from one source.
 *
 * @param source: the global id (zenoh id + entity id) of the source of the missed samples.
 * @param missedCount: number of missed samples
 */
@Unstable
data class SampleMiss(
    val source: EntityGlobalId,
    val missedCount: Long
)
