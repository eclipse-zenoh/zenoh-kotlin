//
// Copyright (c) 2026 ZettaScale Technology
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

package io.zenoh.query

/** The key expressions accepted by a query for replies. */
// NOTE: the wire values follow the flat bindings enum (ANY=0,
// MATCHING_QUERY=1) and deliberately differ from the declaration order.
enum class ReplyKeyExpr(internal val value: Int) {

    /**
     * Replies must have a key expression matching the query's.
     */
    MATCHING_QUERY(1),

    /**
     * Replies may have any key expression.
     */
    ANY(0);

    internal companion object {
        fun fromInt(value: Int) = entries.first { it.value == value }
    }
}
