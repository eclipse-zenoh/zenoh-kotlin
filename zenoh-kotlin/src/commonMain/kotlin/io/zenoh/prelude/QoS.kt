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

class QoS internal constructor(internal val qos: Byte) {
    
    fun priority(): Priority {
        return Priority.fromInt(getPriorityViaJNI(qos))
    }

    fun congestionControl(): CongestionControl {
        return CongestionControl.fromInt(getCongestionControlViaJNI(qos))
    }

    fun express(): Boolean {
        return getExpressViaJNI(qos);
    }

    companion object {
        fun default(): QoS {
            return QoS(getDefaultViaJNI())
        } 

        private external fun getDefaultViaJNI(): Byte
    }

    private external fun getPriorityViaJNI(_qos: Byte): Int
    private external fun getCongestionControlViaJNI(_qos: Byte): Int
    private external fun getExpressViaJNI(_qos:Byte): Boolean
}