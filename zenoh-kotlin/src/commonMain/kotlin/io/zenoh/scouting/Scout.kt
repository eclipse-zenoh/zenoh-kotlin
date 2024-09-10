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

package io.zenoh.scouting

import io.zenoh.jni.JNIScout

/**
 * Scout for routers and/or peers.
 *
 * Scout spawns a task that periodically sends scout messages and waits for Hello replies.
 * Drop the returned Scout to stop the scouting task.
 *
 * To launch a scout, use [io.zenoh.Zenoh.scout]:
 * ```kotlin
 * Zenoh.scout(callback = { hello ->
 *     println(hello)
 * }).getOrThrow()
 * ```
 *
 * @param R The receiver type.
 * @param receiver Receiver to handle incoming hello messages.
 */
class Scout<R> internal constructor(
    val receiver: R,
    private var jniScout: JNIScout?
) : AutoCloseable {

    /**
     * Stops the scouting.
     */
    fun stop() {
        jniScout?.close()
        jniScout = null
    }

    /**
     * Equivalent to [stop].
     */
    override fun close() {
        stop()
    }

    protected fun finalize() {
        stop()
    }
}
