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

import io.zenoh.*
import io.zenoh.prelude.SampleKind
import io.zenoh.publication.CongestionControl
import io.zenoh.publication.Priority
import io.zenoh.sample.Attachment
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
     * @param attachment Optional [Attachment].
     */
    fun put(value: Value, attachment: Attachment?): Result<Unit> = runCatching {
        putViaJNI(value.payload, value.encoding.knownEncoding.ordinal, attachment?.let { encodeAttachment(it) }, ptr)
    }

    /**
     * Write operation.
     *
     * @param kind The [SampleKind].
     * @param value The [Value]  to be written.
     * @param attachment Optional [Attachment].
     */
    fun write(kind: SampleKind, value: Value, attachment: Attachment?): Result<Unit> = runCatching {
        writeViaJNI(
            value.payload,
            value.encoding.knownEncoding.ordinal,
            kind.ordinal,
            attachment?.let { encodeAttachment(it) },
            ptr
        )
    }

    /**
     * Delete operation.
     *
     * @param attachment Optional [Attachment].
     */
    fun delete(attachment: Attachment?): Result<Unit> = runCatching {
        deleteViaJNI(attachment?.let { encodeAttachment(it) }, ptr)
    }

    /**
     * Close and free the underlying publisher pointer.
     *
     * Further operations with this publisher should not be performed anymore.
     */
    fun close() {
        freePtrViaJNI(ptr)
    }

    /**
     * Set the congestion control policy of the publisher.
     *
     * This function is not thread safe.
     *
     * @param congestionControl: The [CongestionControl] policy.
     * @return A [Result] with the status of the operation.
     */
    fun setCongestionControl(congestionControl: CongestionControl): Result<Unit> = runCatching {
        setCongestionControlViaJNI(congestionControl.ordinal, ptr)
    }

    /**
     * Set the priority policy of the publisher.
     *
     * This function is not thread safe.
     *
     * @param priority: The [Priority] policy.
     * @return A [Result] with the status of the operation.
     */
    fun setPriority(priority: Priority): Result<Unit> = runCatching {
        setPriorityViaJNI(priority.value, ptr)
    }

    /**
     * Set the congestion control policy of the publisher through JNI.
     *
     * This function is NOT thread safe.
     *
     * @param congestionControl The congestion control policy.
     * @param ptr Pointer to the publisher.
     */
    private external fun setCongestionControlViaJNI(congestionControl: Int, ptr: Long)

    /**
     * Set the priority policy of the publisher through JNI.
     *
     * This function is NOT thread safe.
     *
     * @param priority The priority policy.
     * @param ptr Pointer to the publisher.
     */
    private external fun setPriorityViaJNI(priority: Int, ptr: Long)


    /** Puts through the native Publisher. */
    @Throws(Exception::class)
    private external fun putViaJNI(
        valuePayload: ByteArray, valueEncoding: Int, encodedAttachment: ByteArray?, ptr: Long
    )

    @Throws(Exception::class)
    private external fun writeViaJNI(
        payload: ByteArray, encoding: Int, sampleKind: Int, encodedAttachment: ByteArray?, ptr: Long
    )

    @Throws(Exception::class)
    private external fun deleteViaJNI(encodedAttachment: ByteArray?, ptr: Long)

    /** Frees the underlying native Publisher. */
    private external fun freePtrViaJNI(ptr: Long)

}
