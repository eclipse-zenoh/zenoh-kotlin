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

import io.zenoh.jni.query.Parameters as JniParameters

/**
 * Parameters of the [Selector].
 *
 * A thin facade over the shared string-backed implementation
 * ([io.zenoh.jni.query.Parameters]), which mirrors Rust's
 * `zenoh::query::Parameters` exactly. The string form follows the
 * `a=b;c=d|e;f=g` format:
 *  - parameters are separated by `;` (empty chunks are skipped),
 *  - the parameter name and value are separated by the first `=`,
 *  - in the absence of `=`, the parameter value is considered to be the empty string,
 *  - the value is taken verbatim (no percent-decoding),
 *  - a duplicated parameter name is allowed: [get] returns the FIRST
 *    occurrence, and the duplicates survive round-tripping until an
 *    [insert]/[remove] normalizes the string.
 *
 * @see Selector
 */
class Parameters internal constructor(internal val inner: JniParameters) : IntoParameters {

    companion object {

        /**
         * Creates an empty Parameters.
         */
        fun empty() = Parameters(JniParameters.empty())

        /**
         * Creates a [Parameters] instance from the provided map.
         */
        fun from(params: Map<String, String>): Parameters = Parameters(JniParameters.fromMap(params))

        /**
         * Creates a [Parameters] from a string in the `a=b;c=d|e;f=g` format.
         *
         * Construction is infallible — any string is accepted, exactly as in
         * the Rust layer (the result is always [Result.success]; the [Result]
         * signature is kept for source compatibility).
         */
        fun from(params: String): Result<Parameters> =
            Result.success(Parameters(JniParameters.fromString(params)))
    }

    override fun toString(): String = inner.toString()

    override fun into(): Parameters = this

    /**
     * Returns empty if no parameters were provided.
     */
    fun isEmpty(): Boolean = inner.isEmpty()

    /**
     * Returns true if the [key] is contained.
     */
    fun containsKey(key: String): Boolean = inner.containsKey(key)

    /**
     * Returns the value of the [key] if present (the first occurrence wins).
     */
    fun get(key: String): String? = inner.get(key)

    /**
     * Returns the value of the [key] if present, or if not, the [default] value provided.
     */
    fun getOrDefault(key: String, default: String): String = inner.getOrDefault(key, default)

    /**
     * Returns the values of the [key] if present.
     *
     * Example:
     * ```kotlin
     * val parameters = Parameters.from("a=1;b=2;c=1|2|3").getOrThrow()
     * assertEquals(listOf("1", "2", "3"), parameters.values("c"))
     * ```
     */
    fun values(key: String): List<String>? = if (inner.containsKey(key)) inner.values(key) else null

    /**
     * Inserts the key-value pair into the parameters, returning the old value
     * in case of it being already present.
     */
    fun insert(key: String, value: String): String? = inner.insert(key, value)

    /**
     * Removes the [key] parameter, returning its value.
     */
    fun remove(key: String): String? = inner.remove(key)

    /**
     * Extends the parameters with the [parameters] provided, overwriting
     * any conflicting params.
     */
    fun extend(parameters: IntoParameters) {
        inner.extend(parameters.into().inner)
    }

    /**
     * Extends the parameters with the [parameters] provided, overwriting
     * any conflicting params.
     */
    fun extend(parameters: Map<String, String>) {
        inner.extend(parameters)
    }

    /**
     * Returns a map with the key value pairs of the parameters. For a
     * duplicated key the last occurrence wins (unlike [get]).
     */
    fun toMap(): Map<String, String> = inner.toMap()

    override fun equals(other: Any?): Boolean = other is Parameters && inner == other.inner

    override fun hashCode(): Int = inner.hashCode()
}

interface IntoParameters {
    fun into(): Parameters
}
