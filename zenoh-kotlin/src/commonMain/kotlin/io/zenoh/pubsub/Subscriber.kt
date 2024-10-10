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

package io.zenoh.pubsub

import io.zenoh.*
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNISubscriber
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.session.SessionDeclaration

/**
 * # Subscriber
 *
 * A subscriber that allows listening to updates on a key expression and reacting to changes.
 *
 * Simple example using a callback to handle the received samples:
 * ```kotlin
 * val session = Session.open(Config.default()).getOrThrow()
 * val keyexpr = "a/b/c".intoKeyExpr().getOrThrow()
 * session.declareSubscriber(keyexpr, callback = { sample ->
 *     println(">> [Subscriber] Received $sample")
 * })
 * ```
 *
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Subscriber] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the subscriber earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @param R Receiver type of the [Handler] implementation.
 * @property keyExpr The [KeyExpr] to which the subscriber is associated.
 * @property receiver Optional [R] that is provided when specifying a [Handler] for the subscriber.
 * @property jniSubscriber Delegate object in charge of communicating with the underlying native code.
 * @see Session.declareSubscriber
 */
class Subscriber<R> internal constructor(
    val keyExpr: KeyExpr, val receiver: R, private var jniSubscriber: JNISubscriber?
) : AutoCloseable, SessionDeclaration {

    fun isValid(): Boolean {
        return jniSubscriber != null
    }

    override fun undeclare() {
        jniSubscriber?.close()
        jniSubscriber = null
    }

    override fun close() {
        undeclare()
    }
}
