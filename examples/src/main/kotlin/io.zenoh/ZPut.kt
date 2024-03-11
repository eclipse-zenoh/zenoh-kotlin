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

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority

fun main() {
    println("Opening Session")
    Session.open().onSuccess { session ->
        session.use {
            val keyExpressionResult = "demo/example/zenoh-kotlin-put".intoKeyExpr()
            keyExpressionResult.onSuccess { keyExpr ->
                keyExpr.use {
                    val value = "Put from Kotlin!"
                    session.put(keyExpr, value)
                        .congestionControl(CongestionControl.BLOCK)
                        .priority(Priority.REALTIME)
                        .kind(SampleKind.PUT)
                        .res()
                        .onSuccess { println("Putting Data ('$keyExpr': '$value')...") }
                }
            }
        }
    }
}
