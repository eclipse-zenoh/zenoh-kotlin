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
import io.zenoh.keyexpr.KeyExpr
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
 * @property attachment Optional [Attachment].
 */
class Sample(
    val keyExpr: KeyExpr,
    val value: Value,
    val kind: SampleKind,
    val timestamp: TimeStamp?,
    val attachment: Attachment? = null
): ZenohType {
    override fun toString(): String {
        return if (kind == SampleKind.DELETE) "$kind($keyExpr)" else "$kind($keyExpr: $value)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample

        if (keyExpr != other.keyExpr) return false
        if (value != other.value) return false
        if (kind != other.kind) return false
        return timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = keyExpr.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        return result
    }
}
