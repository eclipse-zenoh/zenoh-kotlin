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

import io.zenoh.Resolvable
import io.zenoh.Session
import io.zenoh.SessionDeclaration
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
 * instance, thereby optimizing operation execution. However, it is crucial to manually invoke [close] on each [KeyExpr]
 * instance before it is garbage collected to prevent memory leaks.
 *
 * As an alternative, employing a try-with-resources pattern using Kotlin's `use` block is recommended. This approach
 * ensures that [close] is automatically called, safely managing the lifecycle of the [KeyExpr] instance.
 *
 * @param keyExpr The string representation of the key expression.
 * @param jniKeyExpr An optional [JNIKeyExpr] instance, present when the key expression was declared through [Session.declareKeyExpr],
 *  it represents the native instance of the key expression.
 */
class KeyExpr internal constructor(internal val keyExpr: String, internal var jniKeyExpr: JNIKeyExpr? = null): AutoCloseable, SessionDeclaration {

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
     * Intersects operation. This method returns `True` if there exists at least one key that belongs to both sets
     * defined by `this` and `other`.
     * Will return false as well if the key expression is not valid anymore.
     */
    fun intersects(other: KeyExpr): Boolean {
         return JNIKeyExpr.intersects(this, other)
    }

    /**
     * Includes operation. This method returns `true` when all the keys defined by `other` also belong to the set
     * defined by `this`.
     * Will return false as well if the key expression is not valid anymore.
     */
    fun includes(other: KeyExpr): Boolean {
        return JNIKeyExpr.includes(this, other)
    }

    /**
     * Undeclare the key expression if it was previously declared on the specified [session].
     *
     * @param session The session from which the key expression was previously declared.
     * @return An empty [Resolvable].
     */
    fun undeclare(session: Session): Resolvable<Unit> {
        return session.undeclare(this)
    }

    /**
     * Returns true if the [KeyExpr] has still associated a native key expression allowing it to perform operations.
     */
    fun isValid(): Boolean {
        return jniKeyExpr != null
    }

    override fun toString(): String {
        return keyExpr
    }

    /**
     * Closes the key expression. Operations performed on this key expression won't be valid anymore after this call.
     */
    override fun close() {
        jniKeyExpr?.close()
        jniKeyExpr = null
    }

    override fun undeclare() {
        close()
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
