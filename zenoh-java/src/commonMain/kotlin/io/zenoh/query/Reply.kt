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
import io.zenoh.exceptions.ZenohException
import io.zenoh.sample.Sample
import io.zenoh.prelude.SampleKind
import io.zenoh.value.Value
import io.zenoh.keyexpr.KeyExpr
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
 * ```java
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
abstract class Reply private constructor(val replierId: String) : ZenohType {

    /**
     * Builder to construct a [Reply].
     *
     * This builder allows you to construct [Success] replies. **Error replies are not yet enabled since they are not yet
     * supported on Zenoh.**
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

//        TODO: uncomment line below when Zenoh enables Error replies.
//        fun error(value: Value) = Error.Builder(query, value)
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
        class Builder internal constructor(val query: Query, val keyExpr: KeyExpr, val value: Value): Resolvable<Unit> {

            private var kind = SampleKind.PUT
            private var timeStamp: TimeStamp? = null

            /**
             * Sets the [SampleKind] of the replied [Sample].
             */
            fun withKind(kind: SampleKind) = apply { this.kind = kind }

            /**
             * Sets the [TimeStamp] of the replied [Sample].
             */
            fun withTimeStamp(timeStamp: TimeStamp) = apply { this.timeStamp = timeStamp }

            /**
             * Constructs the reply sample with the provided parameters and triggers the reply to the query.
             */
            @Throws(ZenohException::class)
            override fun res() {
                val sample = Sample(keyExpr, value, kind, timeStamp)
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
        class Builder internal constructor(val query: Query, val value: Value): Resolvable<Unit> {

            /**
             * Triggers the error reply.
             */
            override fun res() {
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
}

