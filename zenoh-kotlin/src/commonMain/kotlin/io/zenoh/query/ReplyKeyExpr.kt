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
enum class ReplyKeyExpr {

    /**
     * Replies may have any key expression.
     */
    ANY,

    /**
     * Replies must have a key expression matching the query's.
     */
    MATCHING_QUERY;
}
