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

import io.zenoh.value.Value

/**
 * Adapter class to handle the interactions with Zenoh through JNI for a [io.zenoh.publication.Publisher].
 *
 * @property ptr: raw pointer to the underlying native Publisher.
 */
internal class JNIPublisher(private val ptr: Long) {

    /**
     * Put operation.
     *
     * @param value The [Value] to be put.
     * @param attachment Optional attachment.
     */
    fun put(value: Value, attachment: ByteArray?): Result<Unit> = runCatching {
        putViaJNI(value.payload, value.encoding.id.ordinal, value.encoding.schema, attachment, ptr)
    }

    /**
     * Delete operation.
     *
     * @param attachment Optional attachment.
     */
    fun delete(attachment: ByteArray?): Result<Unit> = runCatching {
        deleteViaJNI(attachment, ptr)
    }

    /**
     * Close and free the underlying publisher pointer.
     *
     * Further operations with this publisher should not be performed anymore.
     */
    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(Exception::class)
    private external fun putViaJNI(
        valuePayload: ByteArray, encodingId: Int, encodingSchema: String?, attachment: ByteArray?, ptr: Long
    )

    @Throws(Exception::class)
    private external fun deleteViaJNI(attachment: ByteArray?, ptr: Long)

    private external fun freePtrViaJNI(ptr: Long)

}
