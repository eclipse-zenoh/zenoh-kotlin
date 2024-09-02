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

import io.zenoh.Config
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIScoutCallback
import io.zenoh.protocol.ZenohID
import io.zenoh.scouting.Hello
import io.zenoh.scouting.Scout
import io.zenoh.scouting.WhatAmI

/**
 * Adapter class to handle the interactions with Zenoh through JNI for a [io.zenoh.scouting.Scout]
 *
 * @property ptr: raw pointer to the underlying native scout.
 */
class JNIScout(private val ptr: Long) {

    companion object {
        fun <R> scout(
            whatAmI: Set<WhatAmI>,
            callback: Callback<Hello>,
            config: Config?,
            receiver: R
        ): Scout<R> {
            val scoutCallback = JNIScoutCallback { whatAmI2: Int, id: String, locators: List<String> ->
                callback.run(Hello(WhatAmI.fromInt(whatAmI2), ZenohID(id), locators))
            }
            val binaryWhatAmI: Int = whatAmI.map { it.value }.reduce { acc, it -> acc or it }
            val ptr = scoutViaJNI(binaryWhatAmI, scoutCallback, config?.jniConfig?.ptr)
            return Scout(receiver, JNIScout(ptr))
        }

        @Throws(Exception::class)
        private external fun scoutViaJNI(
            whatAmI: Int,
            callback: JNIScoutCallback,
            configPtr: Long?,
        ): Long

        @Throws(Exception::class)
        external fun freePtrViaJNI(ptr: Long)
    }

    fun close() {
        freePtrViaJNI(ptr)
    }
}
