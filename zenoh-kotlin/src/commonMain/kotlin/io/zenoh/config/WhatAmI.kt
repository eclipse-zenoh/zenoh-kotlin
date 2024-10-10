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

package io.zenoh.config

/**
 * WhatAmI
 *
 * The role of the node sending the `hello` message.
 */
enum class WhatAmI(internal val value: Int) {
    Router(1),
    Peer(2),
    Client(4);

    companion object {
        internal fun fromInt(value: Int) = entries.first { value == it.value }
    }
}
