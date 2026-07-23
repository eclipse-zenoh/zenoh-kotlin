//
// Copyright (c) 2025 ZettaScale Technology
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

package io.zenoh.pubsub

import io.zenoh.annotations.Unstable
import io.zenoh.jni.pubsub.SampleMissListener as JniSampleMissListener
import io.zenoh.session.SessionDeclaration

/**
 * A listener to detect missed samples.
 *
 * Missed samples can only be detected from [AdvancedPublisher] that enables miss detection config.
 *
 * A background sample-miss listener (declared via
 * [AdvancedSubscriber.declareBackgroundSampleMissListener]) has no handle to
 * undeclare — its [jniSampleMissListener] is `null` and it lives until the
 * advanced subscriber ends.
 */
@Unstable
class SampleMissListener internal constructor(
    private var jniSampleMissListener: JniSampleMissListener?,
) : SessionDeclaration, AutoCloseable {

    /**
     * Returns `true` if the listener is still running.
     */
    fun isValid(): Boolean {
        return jniSampleMissListener != null
    }

    /**
     * Closes the listener. This function is equivalent to [undeclare] and is called automatically when using
     * try-with-resources.
     */
    override fun close() {
        undeclare()
    }

    /**
     * Undeclares the listener.
     *
     * Further operations performed with the listener will not be valid anymore.
     */
    override fun undeclare() {
        jniSampleMissListener?.close()
        jniSampleMissListener = null
    }

    protected fun finalize() {
        undeclare()
    }
}
