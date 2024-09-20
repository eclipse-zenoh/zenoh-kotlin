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

import io.zenoh.*
import io.zenoh.exceptions.ZError
import io.zenoh.jni.JNIPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.into
import io.zenoh.session.SessionDeclaration

/**
 * # Publisher
 *
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * Example of a publisher declaration:
 * ```kotlin
 * val keyExpr = "demo/kotlin/greeting"
 * Session.open(Config.default()).onSuccess {
 *     it.use { session ->
 *         session
 *             .declarePublisher(keyExpr)
 *             .onSuccess { pub ->
 *                 var i = 0
 *                 while (true) {
 *                     pub.put("Hello for the ${i}th time!")
 *                     Thread.sleep(1000)
 *                     i++
 *                 }
 *             }
 *     }
 * }
 * ```
 *
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Publisher] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the publisher earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @property keyExpr The key expression the publisher will be associated to.
 * @property qos [QoS] configuration of the publisher.
 * @property jniPublisher Delegate class handling the communication with the native code.
 * @constructor Create empty Publisher with the default configuration.
 * @see Session.declarePublisher
 */
class Publisher internal constructor(
    val keyExpr: KeyExpr,
    val qos: QoS,
    val encoding: Encoding,
    private var jniPublisher: JNIPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val InvalidPublisherResult = Result.failure<Unit>(ZError("Publisher is not valid."))
    }

    /** Get the congestion control applied when routing the data. */
    fun congestionControl() = qos.congestionControl

    /** Get the priority of the written data. */
    fun priority() = qos.priority

    /** Performs a PUT operation on the specified [keyExpr] with the specified [payload]. */
    fun put(payload: IntoZBytes, encoding: Encoding? = null, attachment: IntoZBytes? = null) = jniPublisher?.put(payload, encoding ?: this.encoding, attachment) ?: InvalidPublisherResult

    /** Performs a PUT operation on the specified [keyExpr] with the specified string [message]. */
    fun put(message: String, encoding: Encoding? = null, attachment: IntoZBytes? = null) = jniPublisher?.put(message.into(), encoding ?: this.encoding, attachment) ?: InvalidPublisherResult

    /**
     * Performs a DELETE operation on the specified [keyExpr]
     */
    fun delete(attachment: IntoZBytes? = null) = jniPublisher?.delete(attachment) ?: InvalidPublisherResult

    fun isValid(): Boolean {
        return jniPublisher != null
    }

    override fun close() {
        undeclare()
    }

    override fun undeclare() {
        jniPublisher?.close()
        jniPublisher = null
    }
}
