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

/**
 * # SampleMiss
 * A struct that represent missed samples.
 *
 * @param zidLower: lower 8 bytes of zenoh id (the Zenoh session) of the source of missed samples.
 * @param zidUpper: upper 8 bytes of zenoh id (the Zenoh session) of the source of missed samples.
 * @param eid: entity id (entity in a Zenoh session) of the source of missed samples.
 * @param missedCount: number of missed samples
 */
@Unstable
data class SampleMiss(
    val zidLower: Long,
    val zidUpper: Long,
    val eid: Long,
    val missedCount: Long
)
