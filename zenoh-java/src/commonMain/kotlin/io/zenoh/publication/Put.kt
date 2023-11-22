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

package io.zenoh.publication

import io.zenoh.Resolvable
import io.zenoh.Session
import io.zenoh.exceptions.ZenohException
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.SampleKind
import io.zenoh.value.Value

/**
 * Put operation.
 *
 * A put puts a [io.zenoh.sample.Sample] into the specified key expression.
 *
 * Example:
 * ```java
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/zenoh-java-put")) {
 *         String value = "Put from Java!";
 *         session.put(keyExpr, value)
 *             .congestionControl(CongestionControl.BLOCK)
 *             .priority(Priority.REALTIME)
 *             .kind(SampleKind.PUT)
 *             .res();
 *         System.out.println("Putting Data ('" + keyExpr + "': '" + value + "')...");
 *     }
 * }
 * ```
 *
 * This class is an open class for the sake of the [Delete] operation, which is a special case of [Put] operation.
 *
 * @property keyExpr The [KeyExpr] to which the put operation will be performed.
 * @property value The [Value] to put.
 * @property congestionControl The [CongestionControl] to be applied when routing the data.
 * @property priority The [Priority] of zenoh messages.
 * @property kind The [SampleKind] of the sample (put or delete).
 */
open class Put protected constructor(
    val keyExpr: KeyExpr,
    val value: Value,
    val congestionControl: CongestionControl,
    val priority: Priority,
    val kind: SampleKind
) {

    companion object {

        /**
         * Creates a bew [Builder] associated to the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the put will be performed.
         * @param keyExpr The [KeyExpr] upon which the put will be performed.
         * @param value The [Value] to put.
         * @return A [Put] operation [Builder].
         */
        fun newBuilder(session: Session, keyExpr: KeyExpr, value: Value): Builder {
            return Builder(session, keyExpr, value)
        }
    }

    /**
     * Builder to construct a [Put] operation.
     *
     * @property session The [Session] from which the put operation will be performed.
     * @property keyExpr The [KeyExpr] upon which the put operation will be performed.
     * @property value The [Value] to put.
     * @property congestionControl The [CongestionControl] to be applied when routing the data,
     *  defaults to [CongestionControl.DROP]
     * @property priority The [Priority] of zenoh messages, defaults to [Priority.DATA].
     * @property kind The [SampleKind] of the sample (put or delete), defaults to [SampleKind.PUT].
     * @constructor Create a [Put] builder.
     */
    class Builder internal constructor(
        private val session: Session,
        private val keyExpr: KeyExpr,
        private var value: Value,
        private var congestionControl: CongestionControl = CongestionControl.DROP,
        private var priority: Priority = Priority.DATA,
        private var kind: SampleKind = SampleKind.PUT
    ): Resolvable<Unit> {

        /** Change the [Encoding] of the written data. */
        fun encoding(encoding: Encoding) = apply {
            this.value = Value(value.payload, encoding)
        }

        /** Change the [CongestionControl] to apply when routing the data. */
        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.congestionControl = congestionControl }

        /** Change the [Priority] of the written data. */
        fun priority(priority: Priority) = apply { this.priority = priority }

        /** Change the [SampleKind] of the sample. If set to [SampleKind.DELETE], performs a delete operation. */
        fun kind(kind: SampleKind) = apply { this.kind = kind }

        /** Resolves the put operation. */
        @Throws(ZenohException::class)
        override fun res() {
            val put = Put(keyExpr, value, congestionControl, priority, kind)
            session.run { resolvePut(keyExpr, put) }
        }
    }
}
