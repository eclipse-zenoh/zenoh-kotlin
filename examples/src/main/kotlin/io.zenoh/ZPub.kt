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

fun main() {
    println("Opening session...")
    Session.open().onSuccess { session ->
        session.use {
            "demo/example/zenoh-kotlin-pub".intoKeyExpr().onSuccess { keyExpr ->
                keyExpr.use {
                    println("Declaring publisher on '$keyExpr'...")
                    session.declarePublisher(keyExpr).res().onSuccess { pub ->
                        pub.use {
                            var idx = 0
                            while (true) {
                                Thread.sleep(1000)
                                val payload = "Pub from Kotlin!"
                                println(
                                    "Putting Data ('$keyExpr': '[${
                                        idx.toString().padStart(4, ' ')
                                    }] $payload')..."
                                )
                                pub.put(payload).res()
                                idx++
                            }
                        }
                    }
                }
            }
        }
    }
}
