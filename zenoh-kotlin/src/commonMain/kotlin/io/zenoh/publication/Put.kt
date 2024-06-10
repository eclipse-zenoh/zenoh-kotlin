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
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.*
import io.zenoh.value.Value

/**
 * Put operation.
 *
 * A put puts a [io.zenoh.sample.Sample] into the specified key expression.
 *
 * Example:
 * ```kotlin
 * Session.open().onSuccess { session -> session.use {
 *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
 *     session.put(keyExpr, "Hello")
 *         .congestionControl(CongestionControl.BLOCK)
 *         .priority(Priority.REALTIME)
 *         .res()
 *         .onSuccess { println("Put 'Hello' on $keyExpr.") }
 *     }}
 * }
 * ```
 *
 * This class is an open class for the sake of the [Delete] operation, which is a special case of [Put] operation.
 *
 * @property keyExpr The [KeyExpr] to which the put operation will be performed.
 * @property value The [Value] to put.
 * @property qos The [QoS] configuration.
 * @property attachment An optional user attachment.
 */
class Put private constructor(
    val keyExpr: KeyExpr,
    val value: Value,
    val qos: QoS,
    val attachment: ByteArray?
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
        internal fun newBuilder(session: Session, keyExpr: KeyExpr, value: Value): Builder {
            return Builder(session, keyExpr, value)
        }
    }

    /**
     * Builder to construct a [Put] operation.
     *
     * @property session The [Session] from which the put operation will be performed.
     * @property keyExpr The [KeyExpr] upon which the put operation will be performed.
     * @property value The [Value] to put.
     * @constructor Create a [Put] builder.
     */
    class Builder internal constructor(
        private val session: Session,
        private val keyExpr: KeyExpr,
        private var value: Value,
    ): Resolvable<Unit> {

        private var qosBuilder: QoS.Builder = QoS.Builder()
        private var attachment: ByteArray? = null

        /** Change the [Encoding] of the written data. */
        fun encoding(encoding: Encoding) = apply {
            this.value = Value(value.payload, encoding)
        }

        /** Change the [CongestionControl] to apply when routing the data. */
        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.qosBuilder.congestionControl(congestionControl) }

        /** Change the [Priority] of the written data. */
        fun priority(priority: Priority) = apply { this.qosBuilder.priority(priority) }

        /**
         * Sets the express flag. If true, the reply won't be batched in order to reduce the latency.
         */
        fun express(isExpress: Boolean) = apply { this.qosBuilder.express(isExpress) }

        /** Set an attachment to the put operation. */
        fun withAttachment(attachment: ByteArray) = apply { this.attachment = attachment }

        /** Resolves the put operation, returning a [Result]. */
        override fun res(): Result<Unit> = runCatching {
            val put = Put(keyExpr, value, qosBuilder.build(), attachment)
            session.run { resolvePut(keyExpr, put) }
        }
    }
}
