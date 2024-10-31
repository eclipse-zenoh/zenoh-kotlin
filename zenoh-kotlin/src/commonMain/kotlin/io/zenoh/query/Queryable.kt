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

import io.zenoh.*
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQueryable
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

/**
 * # Queryable
 *
 * A queryable that allows to perform multiple queries on the specified [KeyExpr].
 *
 * Its main purpose is to keep the queryable active as long as it exists.
 *
 * Example using a callback:
 * ```kotlin
 * session.declareQueryable(keyExpr, callback = { query ->
 *     val valueInfo = query.payload?.let { value -> " with value '$value'" } ?: ""
 *     println(">> Received Query '${query.selector}' $valueInfo")
 *     query.reply(
 *         keyExpr,
 *         payload = ZBytes.from("Example reply"),
 *     )}
 * )
 * ```
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Queryable] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the queryable earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @param R Receiver type of the [Handler] implementation.
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @see Session.declareQueryable
 */
class Queryable<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R, private var jniQueryable: JNIQueryable?
) : AutoCloseable, SessionDeclaration {

    /**
     * Returns `true` if the queryable is still running.
     */
    fun isValid(): Boolean {
        return jniQueryable != null
    }

    /**
     * Undeclares the queryable. After this function is called, no more queries will be received.
     */
    override fun undeclare() {
        jniQueryable?.close()
        jniQueryable = null
    }

    /**
     * Closes the queryable. Equivalent to [undeclare]. This function is automatically called when using try-with-resources.
     */
    override fun close() {
        undeclare()
    }
}
