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

package io.zenoh.prelude
import io.zenoh.jni.JNIQoS;

import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority
/**
 * Quality of service settings used to send zenoh message.
 */
class QoS internal constructor(internal val jniQoS: JNIQoS) {
    internal constructor(qos: Byte): this(JNIQoS(qos))

    /**
     * Returns priority of the message.
     */
    fun priority(): Priority = jniQoS.getPriority()

    /**
     * Returns congestion control setting of the message.
     */
    fun congestionControl(): CongestionControl = jniQoS.getCongestionControl()

    /**
     * Returns express flag. If it is true, the message is not batched to reduce the latency.
     */
    fun express(): Boolean = jniQoS.getExpress()

    companion object {
        
        /**
        * Returns default QoS settings.
        */
        fun default(): QoS {
            return QoS(JNIQoS())
        } 
    }
}
