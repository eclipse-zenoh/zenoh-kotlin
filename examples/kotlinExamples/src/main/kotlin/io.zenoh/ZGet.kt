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

import io.zenoh.query.Reply
import io.zenoh.selector.intoSelector
import kotlinx.coroutines.runBlocking
import java.time.Duration

fun main() {
    val timeout = Duration.ofMillis(1000)
    Session.open().onSuccess { session ->
        session.use {
            "demo/example/**".intoSelector().onSuccess { selector ->
                selector.use {
                    session.get(selector).timeout(timeout).res().onSuccess {
                        runBlocking {
                            val iterator = it!!.iterator()
                            while (iterator.hasNext()) {
                                val reply = iterator.next()
                                if (reply is Reply.Success) {
                                    println("Received ('${reply.sample.keyExpr}': '${reply.sample.value}')")
                                } else {
                                    reply as Reply.Error
                                    println("Received (ERROR: '${reply.error}')")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

