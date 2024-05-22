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

package io.zenoh.jni

import io.zenoh.Zenoh
import io.zenoh.keyexpr.KeyExpr

internal class JNIKeyExpr(internal val ptr: Long) {

    companion object {
        fun tryFrom(keyExpr: String): Result<KeyExpr> = runCatching {
            Zenoh.load() // It may happen the zenoh library is not yet loaded when creating a key expression.
            KeyExpr(tryFromViaJNI(keyExpr))
        }

        fun autocanonize(keyExpr: String): Result<KeyExpr> = runCatching {
            Zenoh.load()
            KeyExpr(autocanonizeViaJNI(keyExpr))
        }

        fun intersects(keyExprA: KeyExpr, keyExprB: KeyExpr): Boolean = intersectsViaJNI(
            keyExprA.jniKeyExpr?.ptr ?: 0,
            keyExprA.keyExpr,
            keyExprB.jniKeyExpr?.ptr ?: 0,
            keyExprB.keyExpr
        )

        fun includes(keyExprA: KeyExpr, keyExprB: KeyExpr): Boolean = includesViaJNI(
            keyExprA.jniKeyExpr?.ptr ?: 0,
            keyExprA.keyExpr,
            keyExprB.jniKeyExpr?.ptr ?: 0,
            keyExprB.keyExpr
        )

        @Throws(Exception::class)
        private external fun tryFromViaJNI(keyExpr: String): String

        @Throws(Exception::class)
        private external fun autocanonizeViaJNI(keyExpr: String): String

        @Throws(Exception::class)
        private external fun intersectsViaJNI(ptrA: Long, keyExprA: String, ptrB: Long, keyExprB: String): Boolean

        @Throws(Exception::class)
        private external fun includesViaJNI(ptrA: Long, keyExprA: String, ptrB: Long, keyExprB: String): Boolean
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    /** Frees the underlying native KeyExpr. */
    private external fun freePtrViaJNI(ptr: Long)
}
