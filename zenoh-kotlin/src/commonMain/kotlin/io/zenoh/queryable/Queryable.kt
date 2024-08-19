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

package io.zenoh.queryable

import io.zenoh.*
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQueryable
import io.zenoh.keyexpr.KeyExpr
import kotlinx.coroutines.channels.Channel

/**
 * # Queryable
 *
 * A queryable that allows to perform multiple queries on the specified [KeyExpr].
 *
 * Its main purpose is to keep the queryable active as long as it exists.
 *
 * Example using the default [Channel] handler:
 * ```kotlin
 * Session.open().onSuccess { session -> session.use {
 *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
 *         println("Declaring Queryable")
 *         session.declareQueryable(keyExpr).wait().onSuccess { queryable ->
 *             queryable.use {
 *                 it.receiver?.let { receiverChannel ->
 *                     runBlocking {
 *                         val iterator = receiverChannel.iterator()
 *                         while (iterator.hasNext()) {
 *                             iterator.next().use { query ->
 *                                 println("Received query at ${query.keyExpr}")
 *                                 query.reply(keyExpr)
 *                                      .success("Hello!")
 *                                      .withKind(SampleKind.PUT)
 *                                      .withTimeStamp(TimeStamp.getCurrentTime())
 *                                      .wait()
 *                                      .onSuccess { println("Replied hello.") }
 *                                      .onFailure { println(it) }
 *                             }
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }}
 * ```
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Queryable] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the queryable earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @param R Receiver type of the [Handler] implementation. If no handler is provided to the builder, [R] will be [Unit].
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @property jniQueryable Delegate object in charge of communicating with the underlying native code.
 * @constructor Internal constructor.
 */
class Queryable<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R, private var jniQueryable: JNIQueryable?
) : AutoCloseable, SessionDeclaration {

    fun isValid(): Boolean {
        return jniQueryable != null
    }

    override fun undeclare() {
        jniQueryable?.close()
        jniQueryable = null
    }

    override fun close() {
        undeclare()
    }
}

