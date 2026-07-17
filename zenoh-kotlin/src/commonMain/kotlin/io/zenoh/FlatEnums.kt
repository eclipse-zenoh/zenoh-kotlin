//
// Copyright (c) 2026 ZettaScale Technology
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

import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.Reliability
import io.zenoh.query.ConsolidationMode
import io.zenoh.query.QueryTarget
import io.zenoh.query.ReplyKeyExpr

/**
 * Adapters from the SDK enums to the typed enums of the generated flat
 * bindings. The SDK enums carry the flat wire value in their `value` field,
 * so the mapping is a table lookup — no JNI crossing.
 */

internal val CongestionControl.jni: io.zenoh.jni.qos.CongestionControl
    get() = io.zenoh.jni.qos.CongestionControl.fromInt(value)

internal val Priority.jni: io.zenoh.jni.qos.Priority
    get() = io.zenoh.jni.qos.Priority.fromInt(value)

internal val Reliability.jni: io.zenoh.jni.qos.Reliability
    get() = io.zenoh.jni.qos.Reliability.fromInt(value)

internal val QueryTarget.jni: io.zenoh.jni.query.QueryTarget
    get() = io.zenoh.jni.query.QueryTarget.fromInt(value)

internal val ConsolidationMode.jni: io.zenoh.jni.query.ConsolidationMode
    get() = io.zenoh.jni.query.ConsolidationMode.fromInt(value)

internal val ReplyKeyExpr.jni: io.zenoh.jni.query.ReplyKeyExpr
    get() = io.zenoh.jni.query.ReplyKeyExpr.fromInt(value)
