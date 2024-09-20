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

import io.zenoh.ZenohType
import io.zenoh.sample.Sample
import io.zenoh.protocol.ZenohId
import io.zenoh.queryable.Query

/**
 * Class to represent a Zenoh Reply to a get query and to a remote [Query].
 *
 * Example:
 *
 * ```kotlin
 * session.get(selector, channel = Channel()).onSuccess { channelReceiver ->
 *     runBlocking {
 *         for (reply in channelReceiver) {
 *             reply.result.onSuccess { sample ->
 *                 when (sample.kind) {
 *                     SampleKind.PUT -> println("Received ('${sample.keyExpr}': '${sample.payload}')")
 *                     SampleKind.DELETE -> println("Received (DELETE '${sample.keyExpr}')")
 *                 }
 *             }.onFailure { error ->
 *                 println("Received (ERROR: '${error.message}')")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @property replierId: unique ID identifying the replier, may be null in case the network cannot provide it
 *   (@see https://github.com/eclipse-zenoh/zenoh/issues/709#issuecomment-2202763630).
 */
data class Reply(val replierId: ZenohId?, val result: Result<Sample>) : ZenohType
