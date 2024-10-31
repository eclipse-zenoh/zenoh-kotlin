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

/**
 * Adapter class to handle the interactions with Zenoh through JNI for a [io.zenoh.query.Queryable]
 *
 * @property ptr: raw pointer to the underlying native Queryable.
 */
internal class JNIQueryable(val ptr: Long) {

    fun close() {
        freePtrViaJNI(ptr)
    }

    /** Frees the underlying native Queryable. */
    private external fun freePtrViaJNI(ptr: Long)
}
