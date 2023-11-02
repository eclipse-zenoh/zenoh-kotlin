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

package io.zenoh

import io.zenoh.exceptions.SessionException
import io.zenoh.handlers.Callback
import io.zenoh.jni.JNISession
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.publication.Delete
import io.zenoh.publication.Publisher
import io.zenoh.publication.Put
import io.zenoh.query.*
import io.zenoh.queryable.Query
import io.zenoh.queryable.Queryable
import io.zenoh.sample.Sample
import io.zenoh.selector.Selector
import io.zenoh.subscriber.Reliability
import io.zenoh.subscriber.Subscriber
import io.zenoh.value.Value
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * A Zenoh Session, the core interaction point with a Zenoh network.
 *
 * A session is typically associated with declarations such as [Publisher]s, [Subscriber]s, or [Queryable]s, which are
 * declared using [declarePublisher], [declareSubscriber], and [declareQueryable], respectively.
 * Other operations such as simple Put, Get or Delete can be performed from a session using [put], [get] and [delete].
 * Finally, it's possible to declare key expressions ([KeyExpr]) as well.
 *
 * Sessions are open upon creation and can be closed manually by calling [close]. Alternatively, the session will be
 * automatically closed when used with Java's try-with-resources statement or its Kotlin counterpart, [use].
 *
 * For optimal performance and adherence to good practices, it is recommended to have only one running session, which
 * is sufficient for most use cases. You should _never_ construct one session per publisher/subscriber, as this will
 * significantly increase the size of your Zenoh network, while preventing potential locality-based optimizations.
 */
class Session private constructor(private val config: Config) : AutoCloseable {

    private var jniSession: JNISession? = JNISession()

    companion object {

        private val sessionClosedException = SessionException("Session is closed.")

        /**
         * Open a [Session] with the default [Config].
         *
         * @return The opened [Session].
         * @throws [SessionException] in the case of a failure.
         */
        @Throws(SessionException::class)
        fun open(): Session {
            val session = Session(Config.default())
            return session.launch()
        }

        /**
         * Open a [Session] with the provided [Config].
         *
         * @param config The configuration for the session.
         * @return The opened [Session].
         * @throws [SessionException] in the case of a failure.
         */
        @Throws(SessionException::class)
        fun open(config: Config): Session {
            val session = Session(config)
            return session.launch()
        }
    }

    init {
        Zenoh.load()
    }

    /**
     * Close the session.
     *
     * Closing the session invalidates any attempt to perform a declaration or to perform an operation such as Put or Delete.
     * Attempting to do so will result in a failure.
     *
     * However, any session declaration that was still alive and bound to the session previous to closing it, will still be alive.
     */
    override fun close() {
        jniSession?.close()
        jniSession = null
    }

    protected fun finalize() {
        jniSession?.close()
    }

    /**
     * Declare a [Publisher] on the session.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess {
     *     it.use { session ->
     *         "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declarePublisher(keyExpr)
     *                 .priority(Priority.REALTIME)
     *                 .congestionControl(CongestionControl.DROP)
     *                 .res().onSuccess { pub ->
     *                     pub.use {
     *                         println("Publisher declared on $keyExpr.")
     *                         var i = 0
     *                         while (true) {
     *                             val payload = "Hello for the ${i}th time!"
     *                             println(payload)
     *                             pub.put(payload).res()
     *                             Thread.sleep(1000)
     *                             i++
     *                         }
     *                     }
     *                 }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the publisher will be associated to.
     * @return A resolvable [Publisher.Builder]
     */
    fun declarePublisher(keyExpr: KeyExpr): Publisher.Builder = Publisher.Builder(this, keyExpr)

    /**
     * Declare a [Subscriber] on the session.
     *
     * The default receiver is a [Channel], but can be changed with the [Subscriber.Builder.with] functions.
     *
     * Example:
     *
     * ```kotlin
     * Session.open().onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareSubscriber(keyExpr)
     *                 .bestEffort()
     *                 .res()
     *                 .onSuccess { subscriber ->
     *                     subscriber.use {
     *                         println("Declared subscriber on $keyExpr.")
     *                         runBlocking {
     *                             val receiver = subscriber.receiver!!
     *                             val iterator = receiver.iterator()
     *                             while (iterator.hasNext()) {
     *                                 val sample = iterator.next()
     *                                 println(sample)
     *                             }
     *                         }
     *                 }
     *            }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @return A [Subscriber.Builder] with a [Channel] receiver.
     */
    fun declareSubscriber(keyExpr: KeyExpr): Subscriber.Builder<Channel<Sample>> = Subscriber.newBuilder(this, keyExpr)

    /**
     * Declare a [Queryable] on the session.
     *
     * The default receiver is a [Channel], but can be changed with the [Queryable.Builder.with] functions.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *         println("Declaring Queryable")
     *         session.declareQueryable(keyExpr).res().onSuccess { queryable ->
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
     *                                      .res()
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
     *
     *
     * @param keyExpr The [KeyExpr] the queryable will be associated to.
     * @return A [Queryable.Builder] with a [Channel] receiver.
     */
    fun declareQueryable(keyExpr: KeyExpr): Queryable.Builder<Channel<Query>> = Queryable.newBuilder(this, keyExpr)

    /**
     * Declare a [KeyExpr].
     *
     * Informs Zenoh that you intend to use the provided Key Expression repeatedly.
     *
     * It is generally not needed to declare key expressions, as declaring a subscriber,
     * a queryable, or a publisher will also inform Zenoh of your intent to use their
     * key expressions repeatedly.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session -> session.use {
     *     session.declareKeyExpr("demo/kotlin/example").res().onSuccess { keyExpr ->
     *         keyExpr.use {
     *             session.declarePublisher(it).res().onSuccess { publisher ->
     *                 // ...
     *             }
     *         }
     *     }
     * }}
     * ```
     *
     * @param keyExpr The intended Key expression.
     * @return A resolvable returning an optimized representation of the passed `keyExpr`.
     */
    fun declareKeyExpr(keyExpr: String): Resolvable<KeyExpr> = Resolvable {
        return@Resolvable jniSession?.run {
            declareKeyExpr(keyExpr)
        } ?: throw sessionClosedException
    }

    /**
     * Undeclare a [KeyExpr].
     *
     * The key expression must have been previously declared on the session with [declareKeyExpr],
     * otherwise the operation will result in a failure.
     *
     * @param keyExpr The key expression to undeclare.
     * @return A resolvable returning the status of the undeclare operation.
     */
    fun undeclare(keyExpr: KeyExpr): Resolvable<Unit> = Resolvable {
        return@Resolvable jniSession?.run {
            undeclareKeyExpr(keyExpr)
        } ?: throw (sessionClosedException)
    }

    /**
     * Declare a [Get] with a [Channel] receiver.
     *
     * ```kotlin
     * val timeout = Duration.ofMillis(10000)
     * println("Opening Session")
     * Session.open().onSuccess { session -> session.use {
     *     "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
     *         session.get(keyExpr)
     *             .consolidation(ConsolidationMode.NONE)
     *             .target(QueryTarget.BEST_MATCHING)
     *             .withValue("Get value example")
     *             .with { reply -> println("Received reply $reply") }
     *             .timeout(timeout)
     *             .res()
     *             .onSuccess {
     *                 // Leaving the session alive the same duration as the timeout for the sake of this example.
     *                 Thread.sleep(timeout.toMillis())
     *             }
     *         }
     *     }
     * }
     * ```
     * @param selector The [KeyExpr] to be used for the get operation.
     * @return a resolvable [Get.Builder] with a [Channel] receiver.
     */
    fun get(selector: Selector): Get.Builder<Channel<Reply>> = Get.newBuilder(this, selector)

    /**
     * Declare a [Get] with a [Channel] receiver.
     *
     * ```kotlin
     * val timeout = Duration.ofMillis(10000)
     * println("Opening Session")
     * Session.open().onSuccess { session -> session.use {
     *     "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
     *         session.get(keyExpr)
     *             .consolidation(ConsolidationMode.NONE)
     *             .target(QueryTarget.BEST_MATCHING)
     *             .withValue("Get value example")
     *             .with { reply -> println("Received reply $reply") }
     *             .timeout(timeout)
     *             .res()
     *             .onSuccess {
     *                 // Leaving the session alive the same duration as the timeout for the sake of this example.
     *                 Thread.sleep(timeout.toMillis())
     *             }
     *         }
     *     }
     * }
     * ```
     * @param keyExpr The [KeyExpr] to be used for the get operation.
     * @return a resolvable [Get.Builder] with a [Channel] receiver.
     */
    fun get(keyExpr: KeyExpr): Get.Builder<Channel<Reply>> = Get.newBuilder(this, Selector(keyExpr))

    /**
     * Declare a [Put] with the provided value on the specified key expression.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *     session.put(keyExpr, Value("Hello"))
     *         .congestionControl(CongestionControl.BLOCK)
     *         .priority(Priority.REALTIME)
     *         .kind(SampleKind.PUT)
     *         .res()
     *         .onSuccess { println("Put 'Hello' on $keyExpr.") }
     *     }}
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the put operation.
     * @param value The [Value] to be put.
     * @return A resolvable [Put.Builder].
     */
    fun put(keyExpr: KeyExpr, value: Value): Put.Builder = Put.newBuilder(this, keyExpr, value)

    /**
     * Declare a [Put] with the provided value on the specified key expression.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *     session.put(keyExpr, "Hello")
     *         .congestionControl(CongestionControl.BLOCK)
     *         .priority(Priority.REALTIME)
     *         .kind(SampleKind.PUT)
     *         .res()
     *         .onSuccess { println("Put 'Hello' on $keyExpr.") }
     *     }}
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the put operation.
     * @param message The message to be put.
     * @return A resolvable [Put.Builder].
     */
    fun put(keyExpr: KeyExpr, message: String): Put.Builder = Put.newBuilder(this, keyExpr, Value(message))

    /**
     * Declare a [Delete].
     *
     * Example:
     *
     * ```kotlin
     * println("Opening Session")
     * Session.open().onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/example".intoKeyExpr().onSuccess { keyExpr ->
     *         session.delete(keyExpr)
     *             .res()
     *             .onSuccess {
     *                 println("Performed a delete on $keyExpr.")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the delete operation.
     * @return a resolvable [Delete.Builder].
     */
    fun delete(keyExpr: KeyExpr): Delete.Builder = Delete.newBuilder(this, keyExpr)

    /** Returns if session is open or has been closed. */
    fun isOpen(): Boolean {
        return jniSession != null
    }

    @Throws(Exception::class)
    internal fun resolvePublisher(builder: Publisher.Builder): Publisher {
        return jniSession?.run {
            declarePublisher(builder)
        } ?: throw (sessionClosedException)
    }

    @Throws(Exception::class)
    internal fun <R> resolveSubscriber(
        keyExpr: KeyExpr, callback: Callback<Sample>, onClose: () -> Unit, receiver: R?, reliability: Reliability
    ): Subscriber<R> {
        return jniSession?.run {
            declareSubscriber(keyExpr, callback, onClose, receiver, reliability)
        } ?: throw (sessionClosedException)
    }

    @Throws(Exception::class)
    internal fun <R> resolveQueryable(
        keyExpr: KeyExpr, callback: Callback<Query>, onClose: () -> Unit, receiver: R?, complete: Boolean
    ): Queryable<R> {
        return jniSession?.run {
            declareQueryable(keyExpr, callback, onClose, receiver, complete)
        } ?: throw (sessionClosedException)
    }

    @Throws(Exception::class)
    internal fun <R> resolveGet(
        selector: Selector,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R?,
        timeout: Duration,
        target: QueryTarget,
        consolidation: ConsolidationMode,
        value: Value?
    ): R? {
        if (jniSession == null) {
            throw sessionClosedException
        }
        return jniSession?.performGet(selector, callback, onClose, receiver, timeout, target, consolidation, value)
    }

    @Throws(Exception::class)
    internal fun resolvePut(keyExpr: KeyExpr, put: Put) {
        jniSession?.run { performPut(keyExpr, put) }
    }

    @Throws(Exception::class)
    internal fun resolveDelete(keyExpr: KeyExpr, delete: Delete) {
        jniSession?.run { performPut(keyExpr, delete) }
    }

    /** Launches the session through the jni session, returning the [Session] on success. */
    @Throws(SessionException::class)
    private fun launch(): Session {
        jniSession!!.open(config)
        return this
    }
}

