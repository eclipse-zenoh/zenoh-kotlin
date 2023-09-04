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
            val keyExprPtr = tryFromViaJNI(keyExpr)
            KeyExpr(JNIKeyExpr(keyExprPtr))
        }

        fun autocanonize(keyExpr: String): Result<KeyExpr> = runCatching {
            Zenoh.load()
            val keyExprPtr = autocanonizeViaJNI(keyExpr)
            KeyExpr(JNIKeyExpr(keyExprPtr))
        }

        @Throws(Exception::class)
        private external fun tryFromViaJNI(keyExpr: String): Long

        @Throws(Exception::class)
        private external fun autocanonizeViaJNI(keyExpr: String): Long
    }

    override fun toString(): String {
        return getStringValueViaJNI(ptr)
    }

    fun intersects(other: KeyExpr): Boolean {
        if (other.jniKeyExpr == null) {
            return false
        }
        return intersectsViaJNI(ptr, other.jniKeyExpr!!.ptr)
    }

    fun includes(other: KeyExpr): Boolean {
        if (other.jniKeyExpr == null) {
            return false
        }
        return includesViaJNI(ptr, other.jniKeyExpr!!.ptr)
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JNIKeyExpr

        return equalsViaJNI(ptr, other.ptr)
    }

    override fun hashCode(): Int {
        return ptr.hashCode()
    }

    private external fun equalsViaJNI(ptrA: Long, ptrB: Long): Boolean

    private external fun intersectsViaJNI(ptrA: Long, ptrB: Long): Boolean

    private external fun includesViaJNI(ptrA: Long, ptrB: Long): Boolean

    @Throws(Exception::class)
    private external fun getStringValueViaJNI(ptr: Long): String

    /** Frees the underlying native KeyExpr. */
    private external fun freePtrViaJNI(ptr: Long)
}
