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
import io.zenoh.exceptions.throwZError
import io.zenoh.exceptions.throwZError0
import io.zenoh.exceptions.zCall
import io.zenoh.jni.ErrorHandler
import io.zenoh.jni.JniErrorHandler
import io.zenoh.jni.keyexpr.KeyExpr as JniKeyExpr
import io.zenoh.session.SessionDeclaration

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
 * A [KeyExpr] is its validated string. A native instance exists ONLY behind a
 * [Session.declareKeyExpr] result — the single case where zenoh attaches a wire
 * declaration (a compact id replacing the string on the wire) worth carrying;
 * such an instance should be [close]d (or `use`d) when no longer needed.
 * Every other [KeyExpr] — constructed via [tryFrom]/[autocanonize] or received
 * with a sample — is a plain value: nothing to close, no native resource.
 *
 * For more information, checkout the [key expressions RFC](https://github.com/eclipse-zenoh/roadmap/blob/main/rfcs/ALL/Key%20Expressions.md).
 */
class KeyExpr internal constructor(
    internal val keyExpr: String,
    /**
     * The owned native handle — non-null ONLY for [Session.declareKeyExpr]
     * results, whose wire declaration makes sends through the declaring
     * session compact. Everything else is string-backed.
     */
    internal var jniKeyExpr: JniKeyExpr? = null
) : AutoCloseable, SessionDeclaration {

    /** Clone the native handle before passing it to a consuming Rust API. */
    internal fun cloneHandle(): JniKeyExpr? = jniKeyExpr?.newClone(throwZError0)

    /**
     * Run [body] with a native handle: the declared handle when present,
     * else a transient one validated from the string (closed afterwards).
     * Used by the native keyexpr algebra ops.
     */
    private inline fun <R> withHandle(body: (JniKeyExpr) -> R): R {
        val h = jniKeyExpr
        if (h != null) return body(h)
        val tmp = JniKeyExpr.newTryFrom(keyExpr, throwZError0, throwZError)
        try {
            return body(tmp)
        } finally {
            tmp.close()
        }
    }

    companion object {

        /**
         * Builds a [KeyExpr] from the canonical string of a probe handle:
         * the probe construction failure (native, via the sink) and the
         * (binding-only) string read failure both surface as
         * [Result.failure].
         */
        private inline fun fromProbe(
            crossinline makeProbe: (JniErrorHandler<JniKeyExpr>, ErrorHandler<JniKeyExpr>) -> JniKeyExpr
        ): Result<KeyExpr> =
            zCall({ JniKeyExpr(0L) }) { onBindingError, onError -> makeProbe(onBindingError, onError) }
                .mapCatching { probe ->
                    try {
                        KeyExpr(probe.getStr(throwZError0))
                    } finally {
                        probe.close()
                    }
                }

        /**
         * Try from.
         *
         * The default way to construct a KeyExpr. This function will ensure that the passed expression is valid.
         * It won't however try to correct expressions that aren't canon.
         *
         * You may use [autocanonize] instead if you are unsure if the expression you will use for construction will be canon.
         *
         * The result is string-backed: the expression is validated natively
         * once and the probe handle is released — an undeclared native
         * keyexpr carries no state beyond its string.
         *
         * @param keyExpr The intended key expression as a string.
         * @return a [Result] with the [KeyExpr] in case of success.
         */
        fun tryFrom(keyExpr: String): Result<KeyExpr> =
            zCall({ JniKeyExpr(0L) }) { onBindingError, onError ->
                JniKeyExpr.newTryFrom(keyExpr, onBindingError, onError)
            }
                .map { probe ->
                    probe.close()
                    KeyExpr(keyExpr)
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
        fun autocanonize(keyExpr: String): Result<KeyExpr> =
            fromProbe { onBindingError, onError ->
                JniKeyExpr.newAutocanonize(keyExpr, onBindingError, onError)
            }
    }

    /**
     * Intersects operation.
     *
     * This method returns `True` if there exists at least one key that belongs to both sets defined by `this` and the `other` key expressions.
     */
    fun intersects(other: KeyExpr): Boolean = withHandle { h ->
        other.jniKeyExpr?.let { h.intersects(it, throwZError0) }
            ?: h.intersects(other.keyExpr, throwZError0)
    }

    /**
     * Includes operation.
     *
     * This method returns `true` when all the keys defined by `other` also belong to the set defined by `this`.
     */
    fun includes(other: KeyExpr): Boolean = withHandle { h ->
        other.jniKeyExpr?.let { h.includes(it, throwZError0) }
            ?: h.includes(other.keyExpr, throwZError0)
    }

    /**
     * Returns the relation between 'this' and `other` from 'this''s point of view (`SetIntersectionLevel::Includes`
     * signifies that `this` includes other). Note that this is slower than [intersects] and [includes],
     * so you should favor these methods for most applications.
     */
    fun relationTo(other: KeyExpr): SetIntersectionLevel = withHandle { h ->
        val raw = other.jniKeyExpr?.let { h.relationTo(it, throwZError0) }
            ?: h.relationTo(other.keyExpr, throwZError0)
        SetIntersectionLevel.fromInt(raw.value)
    }

    /**
     * Joins both sides, inserting a `/` in between them.
     * This should be your preferred method when concatenating path segments.
     */
    fun join(other: String): Result<KeyExpr> = fromProbe { onBindingError, onError ->
        jniKeyExpr?.let { JniKeyExpr.newJoin(it, other, onBindingError, onError) }
            ?: JniKeyExpr.newJoin(keyExpr, other, onBindingError, onError)
    }

    /**
     * Performs string concatenation and returns the result as a `KeyExpr` if possible.
     * You should probably prefer [join] as Zenoh may then take advantage of the hierarchical separation it inserts.
     */
    fun concat(other: String): Result<KeyExpr> = fromProbe { onBindingError, onError ->
        jniKeyExpr?.let { JniKeyExpr.newConcat(it, other, onBindingError, onError) }
            ?: JniKeyExpr.newConcat(keyExpr, other, onBindingError, onError)
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

// The three slots of the generated key-expr selector block (`keyExprSel,
// keyExpr0, keyExpr1`), computed from one [KeyExpr] in a single expression per
// slot so every send call site stays one flat call: declared -> the bare
// handle (arm 1), string-backed -> the validated string rebuilt Rust-side
// inside the same call (arm 0). For CONSUMING params use [KeyExpr.cloneHandle]
// in the third slot instead — the handle arm takes the value by move.

internal val KeyExpr.jniSel: Int
    get() = if (jniKeyExpr != null) 1 else 0

internal val KeyExpr.jniStr: String?
    get() = if (jniKeyExpr == null) keyExpr else null

internal val KeyExpr.jniHandle: JniKeyExpr?
    get() = jniKeyExpr
