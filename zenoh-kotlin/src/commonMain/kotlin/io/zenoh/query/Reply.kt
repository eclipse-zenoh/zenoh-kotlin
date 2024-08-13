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
 * Class to represent a Zenoh Reply to a [Get] operation and to a remote [Query].
 *
 * A reply can be either successful ([Success]) or an error ([Error]), both having different information. For instance,
 * the successful reply will contain a [Sample] while the error reply will only contain a [Value] with the error information.
 *
 * Replies can either be automatically created when receiving a remote reply after performing a [Get] (in which case the
 * [replierId] shows the id of the replier) or created through the builders while answering to a remote [Query] (in that
 * case the replier ID is automatically added by Zenoh).
 *
 * Example:
 * ```kotlin
 * TODO: provide example and update documentation
 * ```
 *
 * **IMPORTANT: Error replies are not yet fully supported by Zenoh, but the code for the error replies below has been
 * added for the sake of future compatibility.** TODO: double check
 *
 * @property replierId: unique ID identifying the replier.
 */
sealed class Reply private constructor(val replierId: ZenohID?) : ZenohType {

    /**
     * A successful [Reply].
     *
     * @property sample The [Sample] of the reply.
     * @constructor Internal constructor, since replies are only meant to be generated upon receiving a remote reply
     * or by calling [Query.reply] to reply to the specified [Query].
     *
     * @param replierId The replierId of the remotely generated reply.
     */
    class Success internal constructor(replierId: ZenohID? = null, val sample: Sample) : Reply(replierId) {

        override fun toString(): String {
            return "Success(sample=$sample)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false

            return sample == other.sample
        }

        override fun hashCode(): Int {
            return sample.hashCode()
        }
    }

    /**
     * An Error reply.
     *
     * @property error: value with the error information.*
     * @param replierId: unique ID identifying the replier.
     */
    class Error internal constructor(replierId: ZenohID? = null, val error: Value) : Reply(replierId) {

        override fun toString(): String {
            return "Error(error=$error)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Error) return false

            return error == other.error
        }

        override fun hashCode(): Int {
            return error.hashCode()
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
    class Delete internal constructor(
        replierId: ZenohID? = null,
        val keyExpr: KeyExpr,
        val timestamp: TimeStamp?,
        val attachment: ZBytes?,
        val qos: QoS
    ) : Reply(replierId) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Delete) return false
            return (keyExpr == other.keyExpr
                    && timestamp == other.timestamp
                    && attachment == other.attachment
                    && qos == other.qos)
        }

        override fun hashCode(): Int {
            return keyExpr.hashCode()
        }

        override fun toString(): String {
            return "Delete(keyexpr=$keyExpr)"
        }
    }
}

