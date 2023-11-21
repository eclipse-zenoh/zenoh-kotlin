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
import kotlinx.coroutines.runBlocking

fun main() {
    println("Opening session...")
    Session.open().onSuccess { session ->
        session.use {
            "demo/example/**".intoKeyExpr().onSuccess { keyExpr ->
                keyExpr.use {
                    println("Declaring Subscriber on '$keyExpr'...")
                    session.declareSubscriber(keyExpr).bestEffort().res().onSuccess { subscriber ->
                        subscriber.use {
                            runBlocking {
                                val receiver = subscriber.receiver!!
                                val iterator = receiver.iterator()
                                while (iterator.hasNext()) {
                                    val sample = iterator.next()
                                    println(">> [Subscriber] Received ${sample.kind} ('${sample.keyExpr}': '${sample.value}')")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

