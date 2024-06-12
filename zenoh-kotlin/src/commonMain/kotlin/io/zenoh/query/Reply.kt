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

import io.zenoh.Resolvable
import io.zenoh.ZenohType
import io.zenoh.sample.Sample
import io.zenoh.prelude.SampleKind
import io.zenoh.value.Value
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority
import io.zenoh.prelude.QoS
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
 * Generating a reply only makes sense within the context of a [Query], therefore builders below are meant to only
 * be accessible from [Query.reply].
 *
 * Example:
 * ```kotlin
 * session.declareQueryable(keyExpr).with { query ->
 *     query.reply(keyExpr)
 *          .success(Value("Hello"))
 *          .withTimeStamp(TimeStamp(Date.from(Instant.now())))
 *          .res()
 *     }.res()
 * ...
 * ```
 *
 * **IMPORTANT: Error replies are not yet fully supported by Zenoh, but the code for the error replies below has been
 * added for the sake of future compatibility.**
 *
 * @property replierId: unique ID identifying the replier.
 */
sealed class Reply private constructor(val replierId: String) : ZenohType {

    /**
     * Builder to construct a [Reply].
     *
     * This builder allows you to construct [Success] and [Error] replies.
     *
     * @property query The received [Query] to reply to.
     * @property keyExpr The [KeyExpr] from the queryable, which is at least an intersection of the query's key expression.
     * @constructor Create empty Builder
     */
    class Builder internal constructor(val query: Query, val keyExpr: KeyExpr) {

        /**
         * Returns a [Success.Builder] with the provided [value].
         *
         * @param value The [Value] of the reply.
         */
        fun success(value: Value) = Success.Builder(query, keyExpr, value)

        /**
         * Returns a [Success.Builder] with a [Value] containing the provided [message].
         *
         * It is equivalent to calling `success(Value(message))`.
         *
         * @param message A string message for the reply.
         */
        fun success(message: String) = success(Value(message))

        /**
         * Returns an [Error.Builder] with the provided [value].
         *
         * @param value The [Value] of the error reply.
         */
        fun error(value: Value) = Error.Builder(query, value)

        /**
         * Returns an [Error.Builder] with a [Value] containing the provided [message].
         *
         * It is equivalent to calling `error(Value(message))`.
         *
         * @param message A string message for the error reply.
         */
        fun error(message: String) = error(Value(message))

        /**
         * Returns a [Delete.Builder].
         */
        fun delete() = Delete.Builder(query, keyExpr)

    }

    /**
     * A successful [Reply].
     *
     * @property sample The [Sample] of the reply.
     * @constructor Internal constructor, since replies are only meant to be generated upon receiving a remote reply
     * or by calling [Query.reply] to reply to the specified [Query].
     *
     * @param replierId The replierId of the remotely generated reply.
     */
    class Success internal constructor(replierId: String, val sample: Sample) : Reply(replierId) {

        /**
         * Builder for the [Success] reply.
         *
         * @property query The [Query] to reply to.
         * @property keyExpr The [KeyExpr] of the queryable.
         * @property value The [Value] with the reply information.
         */
        class Builder internal constructor(val query: Query, val keyExpr: KeyExpr, val value: Value) :
            Resolvable<Unit> {

            private val kind = SampleKind.PUT
            private var timeStamp: TimeStamp? = null
            private var attachment: ByteArray? = null
            private var qosBuilder = QoS.Builder()

            /**
             * Sets the [TimeStamp] of the replied [Sample].
             */
            fun timestamp(timeStamp: TimeStamp) = apply { this.timeStamp = timeStamp }

            /**
             * Appends an attachment to the reply.
             */
            fun attachment(attachment: ByteArray) = apply { this.attachment = attachment }

            /**
             * Sets the express flag. If true, the reply won't be batched in order to reduce the latency.
             */
            fun express(express: Boolean) = apply { qosBuilder.express(express) }

            /**
             * Sets the [Priority] of the reply.
             */
            fun priority(priority: Priority) = apply { qosBuilder.priority(priority) }

            /**
             * Sets the [CongestionControl] of the reply.
             *
             * @param congestionControl
             */
            fun congestionControl(congestionControl: CongestionControl) =
                apply { qosBuilder.congestionControl(congestionControl) }

            /**
             * Constructs the reply sample with the provided parameters and triggers the reply to the query.
             */
            override fun res(): Result<Unit> {
                val sample = Sample(keyExpr, value, kind, timeStamp, qosBuilder.build(), attachment)
                return query.reply(Success("", sample)).res()
            }
        }

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
     * @property error: value with the error information.
     * @constructor The constructor is private since reply instances are created through JNI when receiving a reply to a query.
     *
     * @param replierId: unique ID identifying the replier.
     */
    class Error internal constructor(replierId: String, val error: Value) : Reply(replierId) {

        /**
         * Builder for the [Error] reply.
         *
         * @property query The [Query] to reply to.
         * @property value The [Value] with the reply information.
         */
        class Builder internal constructor(val query: Query, val value: Value) : Resolvable<Unit> {

            /**
             * Triggers the error reply.
             */
            override fun res(): Result<Unit> {
                return query.reply(Error("", value)).res()
            }
        }

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
     * @property keyExpr
     * @constructor
     *
     * @param replierId
     */
    class Delete internal constructor(
        replierId: String,
        val keyExpr: KeyExpr,
        val timestamp: TimeStamp?,
        val attachment: ByteArray?,
        val qos: QoS
    ) : Reply(replierId) {

        class Builder internal constructor(val query: Query, val keyExpr: KeyExpr) : Resolvable<Unit> {

            private val kind = SampleKind.DELETE
            private var timeStamp: TimeStamp? = null
            private var attachment: ByteArray? = null
            private var qosBuilder = QoS.Builder()

            /**
             * Sets the [TimeStamp] of the replied [Sample].
             */
            fun timestamp(timeStamp: TimeStamp) = apply { this.timeStamp = timeStamp }

            /**
             * Appends an attachment to the reply.
             */
            fun attachment(attachment: ByteArray) = apply { this.attachment = attachment }

            /**
             * Sets the express flag. If true, the reply won't be batched in order to reduce the latency.
             */
            fun express(express: Boolean) = apply { qosBuilder.express(express) }

            /**
             * Sets the [Priority] of the reply.
             */
            fun priority(priority: Priority) = apply { qosBuilder.priority(priority) }

            /**
             * Sets the [CongestionControl] of the reply.
             *
             * @param congestionControl
             */
            fun congestionControl(congestionControl: CongestionControl) =
                apply { qosBuilder.congestionControl(congestionControl) }

            /**
             * Triggers the delete reply.
             */
            override fun res(): Result<Unit> {
                return query.reply(Delete("", keyExpr, timeStamp, attachment, qosBuilder.build())).res()
            }
        }
    }
}

