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

const val NANOS_TO_SEC = 1_000_000_000L
var n = 50000L
var batchCount = 0
var count = 0
var startTimestampNs: Long = 0
var globalStartTimestampNs: Long = 0

fun listener() {
    if (count == 0) {
        startTimestampNs = System.nanoTime()
        if (globalStartTimestampNs == 0L) {
            globalStartTimestampNs = startTimestampNs
        }
        count++
        return
    }
    if (count < n) {
        count++
        return
    }
    val stop = System.nanoTime()
    val msgs = n * NANOS_TO_SEC / (stop - startTimestampNs)
    println("$msgs msgs/sec")
    batchCount++
    count = 0
}

fun report() {
    val end = System.nanoTime()
    val total = batchCount * n + count
    val msgs = (end - globalStartTimestampNs) / NANOS_TO_SEC
    val avg = total * NANOS_TO_SEC / (end - globalStartTimestampNs)
    print("Received $total messages in $msgs: averaged $avg msgs/sec")
}

fun main() {
    "test/thr".intoKeyExpr().onSuccess {
        it.use { keyExpr ->
            println("Opening Session")
            Session.open().onSuccess { it.use {
                session -> session.declareSubscriber(keyExpr)
                    .reliable()
                    .with { listener() }
                    .res()
                    .onSuccess {
                        while (readlnOrNull() != "q") { /* Do nothing */ }
                    }
                }
            }
        }
    }
    report()
}
