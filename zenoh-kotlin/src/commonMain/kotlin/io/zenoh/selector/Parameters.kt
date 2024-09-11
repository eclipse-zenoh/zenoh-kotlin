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

import java.net.URLDecoder

/**
 * Parameters of the [Selector].
 *
 * When in string form, the `parameters` should be encoded like the query section of a URL:
 *  - parameters are separated by `;`,
 *  - the parameter name and value are separated by the first `=`,
 *  - in the absence of `=`, the parameter value is considered to be the empty string,
 *  - both name and value should use percent-encoding to escape characters,
 *  - defining a value for the same parameter name twice is considered undefined behavior and an
 *    error result is returned.
 *
 * @see Selector
 */
data class Parameters internal constructor(private val params: MutableMap<String, String>) : IntoParameters {

    companion object {

        private const val LIST_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = "="
        private const val VALUE_SEPARATOR = "|"

        /**
         * Creates an empty Parameters.
         */
        fun empty() = Parameters(mutableMapOf())

        /**
         * Creates a [Parameters] instance from the provided map.
         */
        fun from(params: Map<String, String>): Parameters = Parameters(params.toMutableMap())

        /**
         * Attempts to create a [Parameters] from a string.
         *
         * When in string form, the `parameters` should be encoded like the query section of a URL:
         *  - parameters are separated by `;`,
         *  - the parameter name and value are separated by the first `=`,
         *  - in the absence of `=`, the parameter value is considered to be the empty string,
         *  - both name and value should use percent-encoding to escape characters,
         *  - defining a value for the same parameter name twice is considered undefined behavior and an
         *    error result is returned.
         */
        fun from(params: String): Result<Parameters> = runCatching {
            if (params.isBlank()) {
                return@runCatching Parameters(mutableMapOf())
            }
            params.split(LIST_SEPARATOR).fold(mutableMapOf<String, String>()) { parametersMap, parameter ->
                val (key, value) = parameter.split(FIELD_SEPARATOR).let { it[0] to it.getOrNull(1) }
                require(!parametersMap.containsKey(key)) { "Duplicated parameter `$key` detected." }
                parametersMap[key] = value?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) } ?: ""
                parametersMap
            }.let { Parameters(it) }
        }
    }

    override fun toString(): String =
        params.entries.joinToString(LIST_SEPARATOR) { "${it.key}$FIELD_SEPARATOR${it.value}" }

    override fun into(): Parameters = this

    /**
     * Returns empty if no parameters were provided.
     */
    fun isEmpty(): Boolean = params.isEmpty()

    /**
     * Returns true if the [key] is contained.
     */
    fun containsKey(key: String): Boolean = params.containsKey(key)

    /**
     * Returns the value of the [key] if present.
     */
    fun get(key: String): String? = params[key]

    /**
     * Returns the value of the [key] if present, or if not, the [default] value provided.
     */
    fun getOrDefault(key: String, default: String): String = params.getOrDefault(key, default)

    /**
     * Returns the values of the [key] if present.
     *
     * Example:
     * ```kotlin
     * val parameters = Parameters.from("a=1;b=2;c=1|2|3").getOrThrow()
     * assertEquals(listOf("1", "2", "3"), parameters.values("c"))
     * ```
     */
    fun values(key: String): List<String>? = params[key]?.split(VALUE_SEPARATOR)

    /**
     * Inserts the key-value pair into the parameters, returning the old value
     * in case of it being already present.
     */
    fun insert(key: String, value: String): String? = params.put(key, value)

    /**
     * Removes the [key] parameter, returning its value.
     */
    fun remove(key: String): String? = params.remove(key)

    /**
     * Extends the parameters with the [parameters] provided, overwriting
     * any conflicting params.
     */
    fun extend(parameters: IntoParameters) {
        params.putAll(parameters.into().params)
    }

    /**
     * Extends the parameters with the [parameters] provided, overwriting
     * any conflicting params.
     */
    fun extend(parameters: Map<String, String>) {
        params.putAll(parameters)
    }

    /**
     * Returns a map with the key value pairs of the parameters.
     */
    fun toMap(): Map<String, String> = params
}

interface IntoParameters {
    fun into(): Parameters
}
