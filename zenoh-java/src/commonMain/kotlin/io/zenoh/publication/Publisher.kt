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
import io.zenoh.exceptions.ZenohException
import io.zenoh.jni.JNIPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.SampleKind
import io.zenoh.value.Value
import kotlin.Throws

/**
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * In order to declare a publisher, [Session.declarePublisher] must be called, which returns a [Publisher.Builder] from
 * which we can specify the [Priority], and the [CongestionControl].
 *
 * Example:
 * ```java
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/java/greeting")) {
 *         System.out.println("Declaring publisher on '" + keyExpr + "'...");
 *         try (Publisher publisher = session.declarePublisher(keyExpr).res()) {
 *             int i = 0;
 *             while (true) {
 *                 publisher.put("Hello for the " + i + "th time!").res();
 *                 Thread.sleep(1000);
 *                 i++;
 *             }
 *         }
 *     }
 * } catch (ZenohException | InterruptedException e) {
 *     System.out.println("Error: " + e);
 * }
 * ```
 *
 * The publisher configuration parameters can be later changed using the setter functions.
 *
 * @property keyExpr The key expression the publisher will be associated to.
 * @property jniPublisher Delegate class handling the communication with the native code.
 * @property congestionControl The congestion control policy.
 * @property priority The priority policy.
 * @constructor Create empty Publisher with the default configuration.
 */
class Publisher internal constructor(
    val keyExpr: KeyExpr,
    private var jniPublisher: JNIPublisher?,
    private var congestionControl: CongestionControl,
    private var priority: Priority
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val sessionException = SessionException("Publisher is not valid.")
    }

    /** Performs a PUT operation on the specified [keyExpr] with the specified [value]. */
    @Throws(ZenohException::class)
    fun put(value: Value): Resolvable<Unit> = Resolvable {
        return@Resolvable jniPublisher?.put(value) ?: throw(sessionException)
    }

    /** Performs a PUT operation on the specified [keyExpr] with the specified string [value]. */
    @Throws(ZenohException::class)
    fun put(value: String): Resolvable<Unit> = Resolvable {
        return@Resolvable jniPublisher?.put(Value(value)) ?: throw(sessionException)
    }

    /**
     * Performs a WRITE operation on the specified [keyExpr]
     *
     * @param kind The [SampleKind] of the data.
     * @param value The [Value] to send.
     * @return A [Resolvable] operation.
     */
    @Throws(ZenohException::class)
    fun write(kind: SampleKind, value: Value): Resolvable<Unit> = Resolvable {
        return@Resolvable jniPublisher?.write(kind, value) ?: throw(sessionException)
    }

    /**
     * Performs a DELETE operation on the specified [keyExpr]
     *
     * @return A [Resolvable] operation.
     */
    @Throws(ZenohException::class)
    fun delete(): Resolvable<Unit> = Resolvable {
        return@Resolvable jniPublisher?.delete() ?: throw(sessionException)
    }

    /** Get congestion control policy. */
    fun getCongestionControl(): CongestionControl {
        return congestionControl
    }

    /**
     * Set the congestion control policy of the publisher.
     *
     * This function is not thread safe.
     *
     * @param congestionControl: The [CongestionControl] policy.
     */
    @Throws(ZenohException::class)
    fun setCongestionControl(congestionControl: CongestionControl) {
         jniPublisher?.setCongestionControl(congestionControl)
        this.congestionControl = congestionControl
    }

    /** Get priority policy. */
    fun getPriority(): Priority {
        return priority
    }

    /**
     * Set the priority policy of the publisher.
     *
     * This function is not thread safe.
     *
     * @param priority: The [Priority] policy.
     */
    @Throws(ZenohException::class)
    fun setPriority(priority: Priority) {
         jniPublisher?.setPriority(priority)
        this.priority = priority
    }

    override fun isValid(): Boolean {
        return jniPublisher != null
    }

    override fun close() {
        undeclare()
    }

    override fun undeclare() {
        jniPublisher?.close()
        jniPublisher = null
    }

    protected fun finalize() {
        jniPublisher?.close()
    }

    /**
     * Publisher Builder.
     *
     * @property session The [Session] from which the publisher is declared.
     * @property keyExpr The key expression the publisher will be associated to.
     * @property congestionControl The congestion control policy, defaults to [CongestionControl.DROP].
     * @property priority The priority policy, defaults to [Priority.DATA]
     * @constructor Create empty Builder.
     */
    class Builder internal constructor(
        val session: Session,
        val keyExpr: KeyExpr,
        var congestionControl: CongestionControl = CongestionControl.DROP,
        var priority: Priority = Priority.DATA,
    ) {

        /** Change the `congestion_control` to apply when routing the data. */
        fun congestionControl(congestionControl: CongestionControl) =
            apply { this.congestionControl = congestionControl }

        /** Change the priority of the written data. */
        fun priority(priority: Priority) = apply { this.priority = priority }

        fun res(): Publisher {
            return session.run { resolvePublisher(this@Builder) }
        }
    }
}

