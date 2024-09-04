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

import io.zenoh.ZenohLoad
import io.zenoh.protocol.ZenohID

internal object JNIZenohID {

    init {
        ZenohLoad
    }

    external fun toStringViaJNI(bytes: ByteArray): String

    /* Internal function for testing. */
    internal fun getDefault(): ZenohID {
        val id = getDefaultViaJNI()
        return ZenohID(id)
    }

    private external fun getDefaultViaJNI(): ByteArray
}
