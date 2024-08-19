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

package io.zenoh.sample

import io.zenoh.ZenohType
import io.zenoh.prelude.SampleKind
import io.zenoh.prelude.QoS
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.protocol.ZBytes
import io.zenoh.value.Value
import org.apache.commons.net.ntp.TimeStamp

/**
 * Class representing a Zenoh Sample.
 *
 * A sample consists of a [KeyExpr]-[Value] pair, annotated with the [SampleKind] (PUT or DELETE) of the publication
 * used to emit it and a timestamp.
 *
 * @property keyExpr The [KeyExpr] of the sample.
 * @property value The [Value] of the sample.
 * @property kind The [SampleKind] of the sample.
 * @property timestamp Optional [TimeStamp].
 * @property qos The Quality of Service settings used to deliver the sample.
 * @property attachment Optional attachment.
 */
data class Sample(
    val keyExpr: KeyExpr,
    val value: Value,
    val kind: SampleKind,
    val timestamp: TimeStamp?,
    val qos: QoS,
    val attachment: ZBytes? = null
): ZenohType
