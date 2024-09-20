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

import io.zenoh.exceptions.ZError
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes

/**
 * Adapter class to handle the interactions with Zenoh through JNI for a [io.zenoh.publication.Publisher].
 *
 * @property ptr: raw pointer to the underlying native Publisher.
 */
internal class JNIPublisher(private val ptr: Long) {

    /**
     * Put operation.
     *
     * @param payload Payload of the put.
     * @param encoding Encoding of the payload.
     * @param attachment Optional attachment.
     */
    fun put(payload: IntoZBytes, encoding: Encoding?, attachment: IntoZBytes?): Result<Unit> = runCatching {
        val resolvedEncoding = encoding ?: Encoding.default()
        putViaJNI(payload.into().bytes, resolvedEncoding.id, resolvedEncoding.schema, attachment?.into()?.bytes, ptr)
    }

    /**
     * Delete operation.
     *
     * @param attachment Optional attachment.
     */
    fun delete(attachment: IntoZBytes?): Result<Unit> = runCatching {
        deleteViaJNI(attachment?.into()?.bytes, ptr)
    }

    /**
     * Close and free the underlying publisher pointer.
     *
     * Further operations with this publisher should not be performed anymore.
     */
    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(ZError::class)
    private external fun putViaJNI(
        valuePayload: ByteArray, encodingId: Int, encodingSchema: String?, attachment: ByteArray?, ptr: Long
    )

    @Throws(ZError::class)
    private external fun deleteViaJNI(attachment: ByteArray?, ptr: Long)

    private external fun freePtrViaJNI(ptr: Long)

}
