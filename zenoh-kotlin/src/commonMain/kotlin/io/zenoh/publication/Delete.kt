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
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.Priority
import io.zenoh.Session
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.QoS

/**
 * Delete operation to perform on Zenoh on a key expression.
 *
 * Example:
 * ```kotlin
 * Session.open().onSuccess { session ->
 *         session.use {
 *             "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
 *             session.delete(keyExpr)
 *                 .wait()
 *                 .onSuccess {
 *                     println("Performed a delete on $keyExpr")
 *                 }
 *             }
 *         }
 *     }
 * ```
 *
 * A delete operation is a special case of a Put operation, it is analogous to perform a Put with an empty value and
 * specifying the sample kind to be `DELETE`.
 */
class Delete private constructor(
    val keyExpr: KeyExpr, val qos: QoS, val attachment: ByteArray?
) {

    companion object {
        /**
         * Creates a new [Builder] associated with the specified [session] and [keyExpr].
         *
         * @param session The [Session] from which the Delete will be performed.
         * @param keyExpr The [KeyExpr] upon which the Delete will be performed.
         * @return A [Delete] operation [Builder].
         */
        fun newBuilder(session: Session, keyExpr: KeyExpr): Builder {
            return Builder(session, keyExpr)
        }
    }

    /**
     * Builder to construct a [Delete] operation.
     *
     * @property session The [Session] from which the Delete will be performed
     * @property keyExpr The [KeyExpr] from which the Delete will be performed
     * @constructor Create a [Delete] builder.
     */
    class Builder internal constructor(
        val session: Session,
        val keyExpr: KeyExpr,
    ) : Resolvable<Unit> {

        private var qosBuilder: QoS.Builder = QoS.Builder()
        private var attachment: ByteArray? = null

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

        /**
         * Performs a DELETE operation on the specified [keyExpr].
         *
         * A successful [Result] only states the Delete request was properly sent through the network, it doesn't mean it
         * was properly executed remotely.
         */
        override fun wait(): Result<Unit> = runCatching {
            val delete = Delete(this.keyExpr, qosBuilder.build(), attachment)
            session.resolveDelete(keyExpr, delete)
        }
    }
}
