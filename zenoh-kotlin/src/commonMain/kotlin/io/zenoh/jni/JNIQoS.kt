//
// Copyright (c) 2024 ZettaScale Technology
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

import io.zenoh.prelude.CongestionControl;
import io.zenoh.prelude.Priority;

internal class JNIQoS internal constructor(internal val qos: Byte) {

    internal constructor(): this(getDefaultQoSViaJNI())

    fun getExpress(): Boolean {
        return getExpressViaJNI(qos)
    }

    fun getCongestionControl(): CongestionControl {
        return CongestionControl.fromInt(getCongestionControlViaJNI(qos))
    }

    fun getPriority(): Priority {
        return Priority.fromInt(getPriorityViaJNI(qos))
    }

    companion object {
        private external fun getDefaultQoSViaJNI(): Byte
    }

    private external fun getPriorityViaJNI(_qos: Byte): Int
    private external fun getCongestionControlViaJNI(_qos: Byte): Int
    private external fun getExpressViaJNI(_qos:Byte): Boolean
}