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

import io.zenoh.prelude.SampleKind
import io.zenoh.Session
import io.zenoh.exceptions.ZenohException
import io.zenoh.value.Value
import io.zenoh.keyexpr.KeyExpr
import kotlin.jvm.Throws

/**
 * Delete operation to perform on Zenoh on a key expression.
 *
 * Example:
 * ```java
 * public void deleteExample() throws ZenohException {
 *     System.out.println("Opening session...");
 *     try (Session session = Session.open()) {
 *         try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/java/example")) {
 *             session.delete(keyExpr).res();
 *             System.out.println("Performed a delete on '" + keyExpr);
 *         }
 *     }
 * }
 * ```
 *
 * A delete operation is a special case of a Put operation, it is analogous to perform a Put with an empty value and
 * specifying the sample kind to be `DELETE`.
 */
class Delete private constructor(
    keyExpr: KeyExpr,
    value: Value,
    congestionControl: CongestionControl,
    priority: Priority,
    kind: SampleKind
) : Put(keyExpr, value, congestionControl, priority, kind) {

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
     * @property congestionControl The [CongestionControl] to be applied when routing the data,
     *  defaults to [CongestionControl.DROP]
     * @property priority The [Priority] of zenoh messages, defaults to [Priority.DATA].
     * @constructor Create a [Delete] builder.
     */
    class Builder internal constructor(
        val session: Session,
        val keyExpr: KeyExpr,
        private var congestionControl: CongestionControl = CongestionControl.DROP,
        private var priority: Priority = Priority.DATA,
    ) {

        /** Change the [CongestionControl] to apply when routing the data. */
        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.congestionControl = congestionControl }

        /** Change the [Priority] of the written data. */
        fun priority(priority: Priority) = apply { this.priority = priority }

        /**
         * Performs a DELETE operation on the specified [keyExpr].
         *
         * A successful resolution only states the Delete request was properly sent through the network, it doesn't mean it
         * was properly executed remotely.
         */
        @Throws(ZenohException::class)
        fun res() {
            val delete = Delete(
                this.keyExpr, Value.empty(), this.congestionControl, this.priority, SampleKind.DELETE
            )
            session.resolveDelete(keyExpr, delete)
        }
    }
}
