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
import io.zenoh.exceptions.SessionException
import io.zenoh.jni.JNIPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Priority
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.QoS
import io.zenoh.value.Value

/**
 * # Publisher
 *
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * In order to declare a publisher, [Session.declarePublisher] must be called, which returns a [Publisher.Builder] from
 * which we can specify the [Priority], and the [CongestionControl].
 *
 * Example:
 * ```
 * val keyExpr = "demo/kotlin/greeting"
 * Session.open().onSuccess {
 *     it.use { session ->
 *         session
 *             .declarePublisher(keyExpr)
 *             .priority(Priority.REALTIME)
 *             .congestionControl(CongestionControl.DROP)
 *             .wait()
 *             .onSuccess { pub ->
 *                 pub.use {
 *                     var i = 0
 *                     while (true) {
 *                         pub.put("Hello for the ${i}th time!").wait()
 *                         Thread.sleep(1000)
 *                         i++
 *                     }
 *                 }
 *             }
 *     }
 * }
 * ```
 *
 * The publisher configuration parameters can be later changed using the setter functions.
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
 */
class Publisher internal constructor(
    val keyExpr: KeyExpr,
    private var qos: QoS,
    private var jniPublisher: JNIPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val InvalidPublisherResult = Result.failure<Unit>(SessionException("Publisher is not valid."))
    }

    /** Performs a PUT operation on the specified [keyExpr] with the specified [value]. */
    fun put(value: Value) = Put(jniPublisher, value)

    /** Performs a PUT operation on the specified [keyExpr] with the specified string [value]. */
    fun put(value: String) = Put(jniPublisher, Value(value))

    /**
     * Performs a DELETE operation on the specified [keyExpr]
     *
     * @return A [Resolvable] operation.
     */
    fun delete() = Delete(jniPublisher)

    /** Get congestion control policy. */
    fun getCongestionControl(): CongestionControl {
        return qos.congestionControl()
    }

    /** Get priority policy. */
    fun getPriority(): Priority {
        return qos.priority()
    }

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

    class Put internal constructor(
        private var jniPublisher: JNIPublisher?,
        val value: Value,
        var attachment: ByteArray? = null
    ) : Resolvable<Unit> {

        fun withAttachment(attachment: ByteArray) = apply { this.attachment = attachment }

        override fun wait(): Result<Unit> = run {
            jniPublisher?.put(value, attachment) ?: InvalidPublisherResult
        }
    }

    class Delete internal constructor(
        private var jniPublisher: JNIPublisher?,
        var attachment: ByteArray? = null
    ) : Resolvable<Unit> {

        fun withAttachment(attachment: ByteArray) = apply { this.attachment = attachment }

        override fun wait(): Result<Unit> = run {
            jniPublisher?.delete(attachment) ?: InvalidPublisherResult
        }
    }

    /**
     * Publisher Builder.
     *
     * @property session The [Session] from which the publisher is declared.
     * @property keyExpr The key expression the publisher will be associated to.
     * @constructor Create empty Builder.
     */
    class Builder internal constructor(
        internal val session: Session,
        internal val keyExpr: KeyExpr,
    ) {
        private var qosBuilder: QoS.Builder = QoS.Builder()

        /** Change the [CongestionControl] to apply when routing the data. */
        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.qosBuilder.congestionControl(congestionControl) }

        /** Change the [Priority] of the written data. */
        fun priority(priority: Priority) = apply { this.qosBuilder.priority(priority) }

        /**
         * Sets the express flag. If true, the reply won't be batched in order to reduce the latency.
         */
        fun express(isExpress: Boolean) = apply { this.qosBuilder.express(isExpress) }

        fun wait(): Result<Publisher> {
            return session.run { resolvePublisher(keyExpr, qosBuilder.build()) }
        }
    }
}

