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

/** The congestion control to be applied when routing the data. */
enum class CongestionControl (val value: Int) {
    
    /**
     * Allows the message to be dropped if all buffers are full.
     */
    DROP(0),

    /**
     * Prevents the message from being dropped at all cost.
     * In the face of heavy congestion on a part of the network, this could result in your publisher node blocking.
     */
    BLOCK(1);

    companion object {
        fun fromInt(value: Int) = entries.first { it.value == value }

        fun default() = DROP
    }
}
