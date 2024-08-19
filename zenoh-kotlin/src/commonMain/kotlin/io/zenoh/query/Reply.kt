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
import io.zenoh.value.Value
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.QoS
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.ZenohID
import io.zenoh.queryable.Query
import org.apache.commons.net.ntp.TimeStamp

/**
 * Class to represent a Zenoh Reply to a get query and to a remote [Query].
 *
 * A reply can be either successful ([Success]), an error ([Error]) or a delete request ([Delete]), both having different
 * information.
 * For instance, the successful reply will contain a [Sample] while the error reply will only contain a [Value]
 * with the error information.
 *
 * Example:
 * ```kotlin
 * Session.open(config).onSuccess { session ->
 *     session.use {
 *         key.intoKeyExpr().onSuccess { keyExpr ->
 *             session.declareQueryable(keyExpr, Channel()).onSuccess { queryable ->
 *                 runBlocking {
 *                     for (query in queryable.receiver) {
 *                         val valueInfo = query.value?.let { value -> " with value '$value'" } ?: ""
 *                         println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
 *                         query.replySuccess(
 *                             keyExpr,
 *                             value = Value("Example value"),
 *                             timestamp = TimeStamp.getCurrentTime()
 *                         ).getOrThrow()
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @property replierId: unique ID identifying the replier, may be null in case the network cannot provide it
 *   (@see https://github.com/eclipse-zenoh/zenoh/issues/709#issuecomment-2202763630).
 */
sealed class Reply private constructor(open val replierId: ZenohID?) : ZenohType {

    /**
     * A successful [Reply].
     *
     * @property sample The [Sample] of the reply.
     * @constructor Internal constructor, since replies are only meant to be generated upon receiving a remote reply
     * or by calling [Query.reply] to reply to the specified [Query].
     *
     * @param replierId The replierId of the remotely generated reply.
     */
    data class Success internal constructor(override val replierId: ZenohID? = null, val sample: Sample) : Reply(replierId) {

        override fun toString(): String {
            return "Success(sample=$sample)"
        }
    }

    /**
     * An Error reply.
     *
     * @property error: value with the error information.*
     * @param replierId: unique ID identifying the replier.
     */
    data class Error internal constructor(override val replierId: ZenohID? = null, val error: Value) : Reply(replierId) {

        override fun toString(): String {
            return "Error(error=$error)"
        }
    }

    /**
     * A Delete reply.
     *
     * @property replierId Unique ID identifying the replier.
     * @property keyExpr Key expression to reply to. This parameter must not be necessarily the same
     * as the key expression from the Query, however it must intersect with the query key.
     * @property attachment Optional attachment for the delete reply.
     * @property qos QoS for the reply.
     */
    data class Delete internal constructor(
        override val replierId: ZenohID? = null,
        val keyExpr: KeyExpr,
        val timestamp: TimeStamp?,
        val attachment: ZBytes?,
        val qos: QoS
    ) : Reply(replierId) {

        override fun toString(): String {
            return "Delete(keyexpr=$keyExpr)"
        }
    }
}

