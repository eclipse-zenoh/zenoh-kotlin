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

import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.queryable.Query
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ntp.TimeStamp

fun main() {
    Session.open().onSuccess { session ->
        session.use {
            "demo/example/zenoh-kotlin-queryable".intoKeyExpr().onSuccess { keyExpr ->
                keyExpr.use {
                    println("Declaring Queryable")
                    session.declareQueryable(keyExpr).res().onSuccess { queryable ->
                        queryable.use {
                            queryable.receiver?.let { receiverChannel -> //  The default receiver is a Channel we can process on a coroutine.
                                runBlocking {
                                    handleRequests(receiverChannel, keyExpr)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun handleRequests(
    receiverChannel: Channel<Query>, keyExpr: KeyExpr
) {
    val iterator = receiverChannel.iterator()
    while (iterator.hasNext()) {
        iterator.next().use { query ->
            val valueInfo = query.value?.let { value -> " with value '$value'" } ?: ""
            println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
            query.reply(keyExpr).success("Queryable from Kotlin!").withKind(SampleKind.PUT)
                .withTimeStamp(TimeStamp.getCurrentTime()).res()
                .onFailure { println(">> [Queryable ] Error sending reply: $it") }
        }
    }
}
