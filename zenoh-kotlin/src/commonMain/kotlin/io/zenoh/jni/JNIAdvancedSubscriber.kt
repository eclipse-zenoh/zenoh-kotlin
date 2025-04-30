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
import io.zenoh.handlers.MatchingCallback
import io.zenoh.handlers.SampleMissCallback
import io.zenoh.jni.callbacks.JNIMatchingListenerCallback
import io.zenoh.jni.callbacks.JNIOnCloseCallback
import io.zenoh.jni.callbacks.JNISampleMissListenerCallback
import io.zenoh.jni.callbacks.JNISubscriberCallback
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.pubsub.MatchingListener
import io.zenoh.pubsub.SampleMiss
import io.zenoh.pubsub.SampleMissListener

/**
 * Adapter class to handle the interactions with Zenoh through JNI for an [AdvancedSubscriber]
 *
 * @property ptr: raw pointer to the underlying native Subscriber.
 */
internal class JNIAdvancedSubscriber(private val ptr: Long) {

    fun declareSampleMissListener(
        callback: SampleMissCallback, onClose: () -> Unit
    ): Result<SampleMissListener> = runCatching {
        val sampleMissListenerCallback =
            JNISampleMissListenerCallback { zidLower: Long,
                                            zidUpper: Long,
                                            eid: Long,
                                            missedCount: Long ->
                val miss = SampleMiss(zidLower, zidUpper, eid, missedCount)
                callback.run(miss)
            }
        val sampleMissListenerRawPtr = declareSampleMissListenerViaJNI(
            ptr, sampleMissListenerCallback, onClose
        )
        SampleMissListener(JNISampleMissListener(sampleMissListenerRawPtr))
    }

    fun declareBackgroundSampleMissListener(
        callback: SampleMissCallback, onClose: () -> Unit
    ): Result<Unit> = runCatching {
        val sampleMissListenerCallback =
            JNISampleMissListenerCallback { zidLower: Long,
                                            zidUpper: Long,
                                            eid: Long,
                                            missedCount: Long ->
                val miss = SampleMiss(zidLower, zidUpper, eid, missedCount)
                callback.run(miss)
            }
        declareBackgroundSampleMissListenerViaJNI(
            ptr, sampleMissListenerCallback, onClose
        )
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    @Throws(ZError::class)
    private external fun declareDetectPublishersSubscriberViaJNI(
        sessionPtr: Long,
        callback: JNISubscriberCallback,
        onClose: JNIOnCloseCallback,
    ): Long

    @Throws(ZError::class)
    private external fun declareBackgroundSampleMissListenerViaJNI(
        ptr: Long,
        callback: JNISampleMissListenerCallback,
        onClose: JNIOnCloseCallback,
    )

    @Throws(ZError::class)
    private external fun declareSampleMissListenerViaJNI(
        ptr: Long,
        callback: JNISampleMissListenerCallback,
        onClose: JNIOnCloseCallback,
    ): Long

    /** Frees the underlying native Subscriber. */
    private external fun freePtrViaJNI(ptr: Long)

}
