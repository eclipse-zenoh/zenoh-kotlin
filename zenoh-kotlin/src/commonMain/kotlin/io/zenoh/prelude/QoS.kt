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

import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority

/**
 * Quality of service settings used to send zenoh message.
 */
class QoS internal constructor(internal val qos: Byte) {

    /**
     * Returns priority of the message.
     */
    fun priority(): Priority {
        return Priority.fromInt(getPriorityViaJNI(qos))
    }

    /**
     * Returns congestion control setting of the message.
     */
    fun congestionControl(): CongestionControl {
        return CongestionControl.fromInt(getCongestionControlViaJNI(qos))
    }

    /**
     * Returns express flag. If it is true, the message is not batched to reduce the latency.
     */
    fun express(): Boolean {
        return getExpressViaJNI(qos);
    }

    companion object {
        /**
        * Returns default QoS settings.
        */
        fun default(): QoS {
            return QoS(getDefaultViaJNI())
        } 

        private external fun getDefaultViaJNI(): Byte
    }

    private external fun getPriorityViaJNI(_qos: Byte): Int
    private external fun getCongestionControlViaJNI(_qos: Byte): Int
    private external fun getExpressViaJNI(_qos:Byte): Boolean
}