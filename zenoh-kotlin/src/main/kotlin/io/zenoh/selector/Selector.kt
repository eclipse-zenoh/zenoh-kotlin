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

package io.zenoh.selector

import io.zenoh.keyexpr.KeyExpr

/**
 * A selector is the combination of a [KeyExpr], which defines the
 * set of keys that are relevant to an operation, and a [parameters], a set of key-value pairs with a few uses:
 *
 *  * specifying arguments to a queryable, allowing the passing of Remote Procedure Call parameters
 *  * filtering by value,
 *  * filtering by metadata, such as the timestamp of a value
 *
 * # Important
 *
 * **This class is still a work in progress. Functionality to decode the parameters is not yet implemented, nor we
 * can use the selector for queries. However, at this stage, within the context of a [io.zenoh.queryable.Query] being
 * received, if parameters are received from a remote query, the [parameters] property will be properly filled.**
 *
 * @property keyExpr The [KeyExpr] of the selector.
 * @property parameters The parameters of the selector.
 */
class Selector(val keyExpr: KeyExpr, val parameters: String) {

    override fun toString(): String {
        return if (parameters.isEmpty()) "$keyExpr" else "$keyExpr?$parameters"
    }
}
