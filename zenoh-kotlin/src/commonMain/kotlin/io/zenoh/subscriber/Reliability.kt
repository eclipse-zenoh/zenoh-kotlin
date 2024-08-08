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

package io.zenoh.subscriber

/**
 * The reliability policy.
 *
 * Used by subscribers to inform the network of the reliability it wishes to obtain.
 */
enum class Reliability {
    /**
     * Best Effort
     *
     * Informs the network that dropping some messages is acceptable.
     */
    BEST_EFFORT,

    /**
     * Reliable
     *
     * Informs the network that this subscriber wishes for all publications to reliably reach it.
     *
     * Note that if a publisher puts a sample with the [io.zenoh.prelude.CongestionControl.DROP] option,
     * this reliability requirement may be infringed to prevent slow readers from blocking the network.
     */
    RELIABLE,
}
