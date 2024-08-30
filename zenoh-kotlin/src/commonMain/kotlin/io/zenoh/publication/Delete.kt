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

import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.QoS
import io.zenoh.protocol.ZBytes

/**
 * Delete operation.
 *
 * @property keyExpr The [KeyExpr] for the delete operation.
 * @property qos The [QoS] configuration.
 * @property attachment Optional attachment.
 */
internal class Delete (
    val keyExpr: KeyExpr, val qos: QoS, val attachment: ZBytes?
)
