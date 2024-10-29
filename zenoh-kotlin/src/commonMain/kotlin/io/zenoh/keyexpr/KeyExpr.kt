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

package io.zenoh.keyexpr

import io.zenoh.Session
import io.zenoh.session.SessionDeclaration
import io.zenoh.jni.JNIKeyExpr

/**
 * # Address space
 *
 * Zenoh's address space is designed around keys which serve as the names of resources.
 *
 * Keys are slash-separated lists of non-empty UTF8 strings. They may not contain the following characters: `$*#?`.
 *
 * Zenoh's operations are executed on key expressions, a small language that allows the definition
 * of sets of keys via the use of wildcards:
 *
 *  - `*` is the single-chunk wildcard, and will match any chunk: `a/*/c` will match `a/b/c`, `a/hello/c`, etc...
 *  - `**` is the 0 or more chunks wildcard: `a/**/c` matches `a/c`, `a/b/c`, `a/b/hello/c`, etc...
 *  - `$*` is the sub-chunk wildcard, it will match any amount of non-/ characters: `a/b$*` matches `a/b`, `a/because`, `a/blue`... but not `a/c` nor `a/blue/c`
 *
 * To allow for better performance and gain the property that two key expressions define the same
 * set if and only if they are the same string, the rules of canon form are mandatory for a key
 * expression to be propagated by a Zenoh network:
 *
 *  - `**/**` may not exist, as it could always be replaced by the shorter `**`,
 *  - `** /*` may not exist, and must be written as its equivalent `*/**` instead,
 *  - `$*` may not exist alone in a chunk, as it must be written `*` instead.
 *
 * The `KeyExpr.autocanonize` constructor exists to correct eventual infringements of the canonization rules.
 *
 * A KeyExpr is a string that has been validated to be a valid Key Expression.
 *
 * # Declaring a key expression from a session.
 *
 * A [KeyExpr] acts as a container for the string representation of a key expression. Operations like `intersects`,
 * `includes`, and `equals` are processed at the native layer using this string representation. For improved performance,
 * consider initializing a [KeyExpr] through [Session.declareKeyExpr]. This method associates the [KeyExpr] with a native
 * instance, thereby optimizing operation execution.
 *
 * For more information, checkout the [key expressions RFC](https://github.com/eclipse-zenoh/roadmap/blob/main/rfcs/ALL/Key%20Expressions.md).
 */
class KeyExpr internal constructor(internal val keyExpr: String, internal var jniKeyExpr: JNIKeyExpr? = null): AutoCloseable,
    SessionDeclaration {

    companion object {

        /**
         * Try from.
         *
         * The default way to construct a KeyExpr. This function will ensure that the passed expression is valid.
         * It won't however try to correct expressions that aren't canon.
         *
         * You may use [autocanonize] instead if you are unsure if the expression you will use for construction will be canon.
         *
         * @param keyExpr The intended key expression as a string.
         * @return a [Result] with the [KeyExpr] in case of success.
         */
        fun tryFrom(keyExpr: String) : Result<KeyExpr> {
            return JNIKeyExpr.tryFrom(keyExpr)
        }

        /**
         * Autocanonize.
         *
         * This alternative constructor for key expressions will attempt to canonize the passed
         * expression before checking if it is valid.
         *
         * @param keyExpr The intended key expression as a string.
         * @return a [Result] with the canonized [KeyExpr] in case of success.
         */
        fun autocanonize(keyExpr: String): Result<KeyExpr> {
            return JNIKeyExpr.autocanonize(keyExpr)
        }
    }

    /**
     * Intersects operation.
     *
     * This method returns `True` if there exists at least one key that belongs to both sets defined by `this` and the `other` key expressions.
     */
    fun intersects(other: KeyExpr): Boolean {
         return JNIKeyExpr.intersects(this, other)
    }

    /**
     * Includes operation.
     *
     * This method returns `true` when all the keys defined by `other` also belong to the set defined by `this`.
     */
    fun includes(other: KeyExpr): Boolean {
        return JNIKeyExpr.includes(this, other)
    }

    /**
     * Returns the relation between 'this' and `other` from 'this''s point of view (`SetIntersectionLevel::Includes`
     * signifies that `this` includes other). Note that this is slower than [intersects] and [includes],
     * so you should favor these methods for most applications.
     */
    fun relationTo(other: KeyExpr): SetIntersectionLevel {
        return JNIKeyExpr.relationTo(this, other)
    }

    /**
     * Joins both sides, inserting a `/` in between them.
     * This should be your preferred method when concatenating path segments.
     */
    fun join(other: String): Result<KeyExpr> {
        return JNIKeyExpr.joinViaJNI(this, other)
    }

    /**
     * Performs string concatenation and returns the result as a `KeyExpr` if possible.
     * You should probably prefer [join] as Zenoh may then take advantage of the hierarchical separation it inserts.
     */
    fun concat(other: String): Result<KeyExpr> {
        return JNIKeyExpr.concatViaJNI(this, other)
    }

    override fun toString(): String {
        return keyExpr
    }

    /**
     * Equivalent to [undeclare]. This function is automatically called when using try with resources.
     *
     * @see undeclare
     */
    override fun close() {
        undeclare()
    }

    /**
     * If the key expression was declared from a [Session], then [undeclare] frees the native key expression associated
     * to this instance. The KeyExpr instance is downgraded into a normal KeyExpr, which still allows performing
     * operations on it, but without the inner optimizations.
     */
    override fun undeclare() {
        jniKeyExpr?.close()
        jniKeyExpr = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyExpr

        return keyExpr == other.keyExpr
    }

    override fun hashCode(): Int {
        return keyExpr.hashCode()
    }
}
