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
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority
import io.zenoh.Session
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.QoS
import io.zenoh.protocol.ZBytes

/**
 * Delete operation to perform on Zenoh on a key expression.
 *
 * Example:
 * ```kotlin
 * Session.open().onSuccess { session ->
 *         session.use {
 *             "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
 *             session.delete(keyExpr)
 *                 .wait()
 *                 .onSuccess {
 *                     println("Performed a delete on $keyExpr")
 *                 }
 *             }
 *         }
 *     }
 * ```
 *
 * A delete operation is a special case of a Put operation, it is analogous to perform a Put with an empty value and
 * specifying the sample kind to be `DELETE`.
 */
internal class Delete (
    val keyExpr: KeyExpr, val qos: QoS, val attachment: ZBytes?
)
