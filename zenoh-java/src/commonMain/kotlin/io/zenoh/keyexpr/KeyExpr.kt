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
import io.zenoh.exceptions.KeyExprException
import io.zenoh.jni.JNIKeyExpr
import kotlin.jvm.Throws

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
 * # Memory
 *
 * Valid KeyExpr instances have associated an underlying native key expression, therefore we must be careful to properly
 * call [close] before the KeyExpr loses any of its references and becomes a phantom reference. As a precautionary measure,
 * this class overrides the [finalize] method which invokes [close] when the garbage collector attempts to remove the
 * instance. However, we should not fully rely on the [finalize] method, as per to the JVM specification we don't know
 * when the GC is going to be triggered, and even worse there is no guarantee it will be called at all.
 * Alternatively, we can use the key expression using a try with resources statement (`use` in Kotlin), since it will
 * automatically invoke the [close] function after using it.
 *
 * @param jniKeyExpr A [JNIKeyExpr] instance which delegates all the operations associated to this [KeyExpr] (intersects,
 * includes, etc.) which are done natively. It keeps track of the underlying key expression instance. Once it is freed,
 * the [KeyExpr] instance is considered to not be valid anymore.
 */
class KeyExpr internal constructor(internal var jniKeyExpr: JNIKeyExpr? = null): AutoCloseable {

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
         * @return The [KeyExpr] in case of success.
         * @throws KeyExprException in the case of failure.
         */
        @JvmStatic
        @Throws(KeyExprException::class)
        fun tryFrom(keyExpr: String): KeyExpr {
            return JNIKeyExpr.tryFrom(keyExpr)
        }

        /**
         * Autocanonize.
         *
         * This alternative constructor for key expressions will attempt to canonize the passed
         * expression before checking if it is valid.
         *
         * @param keyExpr The intended key expression as a string.
         * @return The canonized [KeyExpr].
         * @throws KeyExprException in the case of failure.
         */
        @JvmStatic
        @Throws(KeyExprException::class)
        fun autocanonize(keyExpr: String): KeyExpr {
            return JNIKeyExpr.autocanonize(keyExpr)
        }
    }

    /**
     * Intersects operation. This method returns `True` if there exists at least one key that belongs to both sets
     * defined by `this` and `other`.
     * Will return false as well if the key expression is not valid anymore.
     */
    fun intersects(other: KeyExpr): Boolean {
        return jniKeyExpr?.intersects(other) ?: false
    }

    /**
     * Includes operation. This method returns `true` when all the keys defined by `other` also belong to the set
     * defined by `this`.
     * Will return false as well if the key expression is not valid anymore.
     */
    fun includes(other: KeyExpr): Boolean {
        return jniKeyExpr?.includes(other) ?: false
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
        return this.jniKeyExpr?.toString() ?: ""
    }

    /**
     * Closes the key expression. Operations performed on this key expression won't be valid anymore after this call.
     */
    override fun close() {
        jniKeyExpr?.close()
        jniKeyExpr = null
    }

    @Suppress("removal")
    protected fun finalize() {
        jniKeyExpr?.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyExpr
        if (jniKeyExpr == null || other.jniKeyExpr == null) return false

        return jniKeyExpr == other.jniKeyExpr
    }

    override fun hashCode(): Int {
        return jniKeyExpr?.hashCode() ?: 0
    }
}
