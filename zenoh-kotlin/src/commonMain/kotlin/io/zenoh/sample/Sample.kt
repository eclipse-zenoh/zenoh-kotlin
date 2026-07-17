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
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.ZBytes
import org.apache.commons.net.ntp.TimeStamp

/**
 * Class representing a Zenoh Sample.
 *
 * @property keyExpr The [KeyExpr] of the sample.
 * @property payload [ZBytes] with the payload of the sample.
 * @property encoding [Encoding] of the payload.
 * @property kind The [SampleKind] of the sample.
 * @property timestamp Optional [TimeStamp].
 * @property qos The Quality of Service settings used to deliver the sample.
 * @property attachment Optional attachment.
 */
data class Sample(
    val keyExpr: KeyExpr,
    val payload: ZBytes,
    val encoding: Encoding,
    val kind: SampleKind,
    val timestamp: TimeStamp?,
    val qos: QoS,
    val attachment: ZBytes? = null,
): ZenohType {

    val express = qos.express
    val congestionControl = qos.congestionControl
    val priority = qos.priority

    companion object {
        /**
         * Builds a [Sample] from the decomposed leaves delivered by a generated
         * callback in one JNI crossing. The payload/attachment arrive as owned
         * native handles whose bytes are read lazily (see [ZBytes]). The
         * trailing `reliability` / `source*` leaves are part of the generated
         * decomposition but are not surfaced on the public [Sample] type.
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun fromParts(
            keStr: String,
            payloadH: io.zenoh.jni.bytes.ZBytes,
            encId: Int,
            encSchema: String?,
            kindInt: Int,
            ntp64: Long?,
            express: Boolean,
            prioInt: Int,
            ccInt: Int,
            attachH: io.zenoh.jni.bytes.ZBytes?,
            reliabilityInt: Int,
            sourceZid: io.zenoh.jni.config.ZenohId?,
            sourceEid: Int,
            sourceSn: Long,
        ): Sample = Sample(
            KeyExpr(keStr),
            ZBytes.fromHandle(payloadH),
            Encoding(encId, schema = encSchema),
            SampleKind.fromInt(kindInt),
            ntp64?.let { TimeStamp(it) },
            QoS(CongestionControl.fromInt(ccInt), Priority.fromInt(prioInt), express),
            attachH?.let { ZBytes.fromHandle(it) }
        )
    }
}
