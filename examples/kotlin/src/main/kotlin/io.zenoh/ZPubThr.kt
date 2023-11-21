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

package io.zenoh

import io.zenoh.prelude.KnownEncoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.publication.CongestionControl
import io.zenoh.value.Value

fun main() {
    val size = 8
    val data = ByteArray(size)
    for (i in 0..<size) {
        data[i] = (i % 10).toByte()
    }
    val value = Value(data, Encoding(KnownEncoding.EMPTY))
    Session.open().onSuccess {
        it.use { session ->
            session
                .declarePublisher("test/thr".intoKeyExpr().getOrThrow())
                .congestionControl(CongestionControl.BLOCK)
                .res()
                .onSuccess { pub ->
                    pub.use {
                        println("Publisher declared on test/thr.")
                        while (true) {
                            pub.put(value).res()
                        }
                    }
                }
        }
    }
}
