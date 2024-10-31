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

package io.zenoh.query

import io.zenoh.exceptions.ZError
import io.zenoh.keyexpr.KeyExpr

/**
 * A selector is the combination of a [KeyExpr], which defines the
 * set of keys that are relevant to an operation, and a set of parameters
 * with a few intended uses:
 * - specifying arguments to a queryable, allowing the passing of Remote Procedure Call parameters
 * - filtering by value,
 * - filtering by metadata, such as the timestamp of a value,
 * - specifying arguments to zenoh when using the REST API.
 *
 * When in string form, selectors look a lot like a URI, with similar semantics:
 * - the `key_expr` before the first `?` must be a valid key expression.
 * - the `parameters` after the first `?` should be encoded like the query section of a URL:
 *     - parameters are separated by `;`,
 *     - the parameter name and value are separated by the first `=`,
 *     - in the absence of `=`, the parameter value is considered to be the empty string,
 *     - both name and value should use percent-encoding to escape characters,
 *     - defining a value for the same parameter name twice is considered undefined behavior,
 *       with the encouraged behaviour being to reject operations when a duplicate parameter is detected.
 *
 * Zenoh intends to standardize the usage of a set of parameter names. To avoid conflicting with RPC parameters,
 * the Zenoh team has settled on reserving the set of parameter names that start with non-alphanumeric characters.
 *
 * The full specification for selectors is available [here](https://github.com/eclipse-zenoh/roadmap/tree/main/rfcs/ALL/Selectors),
 * it includes standardized parameters.
 *
 * Queryable implementers are encouraged to prefer these standardized parameter names when implementing their
 * associated features, and to prefix their own parameter names to avoid having conflicting parameter names with other
 * queryables.
 *
 * @property keyExpr The [KeyExpr] of the selector.
 * @property parameters The [Parameters] of the selector.
 */
data class Selector(val keyExpr: KeyExpr, val parameters: Parameters? = null) : AutoCloseable {

    companion object {

        /**
         * Try from.
         *
         * The default way to construct a Selector.
         *
         * When in string form, selectors look a lot like a URI, with similar semantics:
         * - the `key_expr` before the first `?` must be a valid key expression.
         * - the `parameters` after the first `?` should be encoded like the query section of a URL:
         *     - parameters are separated by `;`,
         *     - the parameter name and value are separated by the first `=`,
         *     - in the absence of `=`, the parameter value is considered to be the empty string,
         *     - both name and value should use percent-encoding to escape characters,
         *     - defining a value for the same parameter name twice is considered undefined behavior,
         *       with the encouraged behaviour being to reject operations when a duplicate parameter is detected.
         *
         * @param selector The selector expression as a String.
         * @return a Result with the constructed Selector.
         */
        fun tryFrom(selector: String): Result<Selector> = runCatching {
            if (selector.isEmpty()) {
                throw ZError("Attempting to create a selector from an empty string.")
            }
            val result = selector.split('?', limit = 2)
            val keyExpr = KeyExpr.autocanonize(result[0]).getOrThrow()
            val params = if (result.size == 2) Parameters.from(result[1]).getOrThrow() else null

            Selector(keyExpr, params)
        }
    }

    override fun toString(): String {
        return parameters?.let { "$keyExpr?$parameters" } ?: keyExpr.toString()
    }

    /** Closes the selector's [KeyExpr]. */
    override fun close() {
        keyExpr.close()
    }
}

fun String.intoSelector(): Result<Selector> = Selector.tryFrom(this)
