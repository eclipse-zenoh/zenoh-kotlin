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
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.bytes.ZBytes
import io.zenoh.qos.Reliability

/**
 * Put operation.
 *
 * @property keyExpr The [KeyExpr] to which the put operation will be performed.
 * @property payload The [ZBytes] to put.
 * @property encoding The [Encoding] of the payload.
 * @property qos The [QoS] configuration.
 * @property attachment An optional user attachment.
 * @property reliability The [Reliability] configuration.
 */
internal data class Put (
    val keyExpr: KeyExpr,
    val payload: ZBytes,
    val encoding: Encoding,
    val qos: QoS,
    val attachment: ZBytes?,
    val reliability: Reliability
)
