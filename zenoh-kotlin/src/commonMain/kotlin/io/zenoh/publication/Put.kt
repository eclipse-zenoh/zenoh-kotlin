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

package io.zenoh.publication

import io.zenoh.Resolvable
import io.zenoh.Session
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.*
import io.zenoh.protocol.ZBytes
import io.zenoh.value.Value

/**
 * Put operation.
 *
 * A put puts a [io.zenoh.sample.Sample] into the specified key expression.
 *
 * Example:
 * ```kotlin
 * Session.open().onSuccess { session -> session.use {
 *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
 *     session.put(keyExpr, "Hello")
 *         .congestionControl(CongestionControl.BLOCK)
 *         .priority(Priority.REALTIME)
 *         .wait()
 *         .onSuccess { println("Put 'Hello' on $keyExpr.") }
 *     }}
 * }
 * ```
 *
 * This class is an open class for the sake of the [Delete] operation, which is a special case of [Put] operation.
 *
 * @property keyExpr The [KeyExpr] to which the put operation will be performed.
 * @property value The [Value] to put.
 * @property qos The [QoS] configuration.
 * @property attachment An optional user attachment.
 */
internal class Put (
    val keyExpr: KeyExpr,
    val value: Value,
    val qos: QoS,
    val attachment: ZBytes?
)
