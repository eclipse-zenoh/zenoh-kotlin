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

package io.zenoh.query

import io.zenoh.bytes.Encoding
import io.zenoh.bytes.ZBytes

/**
 * Reply error class.
 *
 * When receiving an error reply while performing a get operation, this is the error type returned:
 * ```kotlin
 * session.get(selector, callback = { reply ->
 *     reply.result.onSuccess { sample ->
 *         // ...
 *     }.onFailure { error ->
 *         error as ReplyError
 *         println("Received (ERROR: '${error.payload}' with encoding '${error.encoding}'.)")
 *         // ...
 *     }
 * })
 * ```
 *
 * This class is useful in case you need to apply different logic based on the encoding of the error or
 * need to deserialize the [payload] value into something else other than a string.
 *
 * Otherwise, the error payload can be obtained as a string under the [Throwable.message] parameter:
 * ```kotlin
 * session.get(selector, callback = { reply ->
 *     reply.result.onSuccess { sample ->
 *         // ...
 *     }.onFailure { error ->
 *         println("Received (ERROR: '${error.message}')")
 *         // ...
 *     }
 * })
 * ```
 */
data class ReplyError(val payload: ZBytes?, val encoding: Encoding?) : Throwable(message = payload.toString())
