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
import io.zenoh.time.Timestamp

/**
 * Class representing a Zenoh Sample.
 *
 * @property keyExpr The [KeyExpr] of the sample.
 * @property payload [ZBytes] with the payload of the sample.
 * @property encoding [Encoding] of the payload.
 * @property kind The [SampleKind] of the sample.
 * @property timestamp Optional [Timestamp].
 * @property qos The Quality of Service settings used to deliver the sample.
 * @property attachment Optional attachment.
 */
data class Sample(
    val keyExpr: KeyExpr,
    val payload: ZBytes,
    val encoding: Encoding,
    val kind: SampleKind,
    val timestamp: Timestamp?,
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
            encSchema: ByteArray?,
            kindInt: Int,
            timestamp: io.zenoh.jni.time.Timestamp?,
            express: Boolean,
            prioInt: Int,
            ccInt: Int,
            attachH: io.zenoh.jni.bytes.ZBytes?,
            reliabilityInt: Int,
            sourceInfo: io.zenoh.jni.sample.SourceInfo?,
        ): Sample = Sample(
            KeyExpr(keStr),
            ZBytes.fromHandle(payloadH),
            // A schema is raw bytes on the wire and zenoh does not require it
            // to be UTF-8; this SDK's Encoding carries a String, so a schema
            // that is not valid UTF-8 decodes lossily rather than throwing on
            // a received message.
            Encoding(encId, schema = encSchema?.toString(Charsets.UTF_8)),
            SampleKind.fromInt(kindInt),
            timestamp?.let { Timestamp.fromJni(it) },
            QoS(CongestionControl.fromInt(ccInt), Priority.fromInt(prioInt), express),
            attachH?.let { ZBytes.fromHandle(it) }
        )
    }
}
