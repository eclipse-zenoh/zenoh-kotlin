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
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNISession
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.QoS
import io.zenoh.protocol.ZBytes
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
 * declared using [declarePublisher], [declareSubscriber], and [declareQueryable], respectively. It is also possible to
 * declare key expressions ([KeyExpr]) as well with [declareKeyExpr] for optimization purposes.
 *
 * Other operations such as simple Put, Get or Delete can be performed from a session using [put], [get] and [delete].
 *
 * Sessions are open upon creation and can be closed manually by calling [close]. Alternatively, the session will be
 * automatically closed when used with Java's try-with-resources statement or its Kotlin counterpart, [use].
 *
 * For optimal performance and adherence to good practices, it is recommended to have only one running session, which
 * is sufficient for most use cases. You should _never_ construct one session per publisher/subscriber, as this will
 * significantly increase the size of your Zenoh network, while preventing potential locality-based optimizations.
 */
class Session private constructor(private val config: Config) : AutoCloseable {

    private var jniSession: JNISession? = null

    private var declarations = mutableListOf<SessionDeclaration>()

    companion object {

        private val sessionClosedException = SessionException("Session is closed.")

        /**
         * Open a [Session] with the default [Config].
         *
         * @return a [Result] with the [Session] on success.
         */
        fun open(): Result<Session> {
            val session = Session(Config.default())
            return session.launch()
        }

        /**
         * Open a [Session] with the provided [Config].
         *
         * @param config The configuration for the session.
         * @return A [Result] with the [Session] on success.
         */
        fun open(config: Config): Result<Session> {
            val session = Session(config)
            return session.launch()
        }
    }

    /** Close the session. */
    override fun close() {
        declarations.removeIf {
            it.undeclare()
            true
        }

        jniSession?.close()
        jniSession = null
    }

    protected fun finalize() {
        close()
    }

    /**
     * Declare a [Publisher] on the session.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess {
     *     it.use { session ->
     *         "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declarePublisher(keyExpr).onSuccess { pub ->
     *                     pub.use {
     *                         println("Publisher declared on $keyExpr.")
     *                         var i = 0
     *                         while (true) {
     *                             val payload = "Hello for the ${i}th time!"
     *                             println(payload)
     *                             pub.put(payload)
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
     * @return The result of the declaration, returning the publisher in case of success.
     */
    fun declarePublisher(keyExpr: KeyExpr, qos: QoS = QoS()): Result<Publisher> {
        return resolvePublisher(keyExpr, qos)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a callback to handle incoming samples.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareSubscriber(keyExpr, callback = { sample -> println(sample) }).onSuccess {
     *                 println("Declared subscriber on $keyExpr.")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param callback Callback to handle the received samples.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @param reliability The reliability the subscriber wishes to obtain from the network.
     * @return A result with the [Subscriber] in case of success.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        onClose: (() -> Unit)? = null,
        reliability: Reliability = Reliability.BEST_EFFORT
    ): Result<Subscriber<Unit>> {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, null, reliability)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a handler to handle incoming samples.
     *
     * Example:
     * ```kotlin
     *
     * class ExampleHandler: Handler<Sample, Unit> {
     *     override fun handle(t: Sample) = println(t)
     *
     *     override fun receiver() = Unit
     *
     *     override fun onClose() = println("Closing handler")
     * }
     *
     * Session.open().onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareSubscriber(keyExpr, handler = ExampleHandler())
     *                 .onSuccess {
     *                     println("Declared subscriber on $keyExpr.")
     *                 }
     *             }
     *         }
     *     }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param handler [Handler] implementation to handle the received samples.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @param reliability The reliability the subscriber wishes to obtain from the network.
     * @return A result with the [Subscriber] in case of success.
     */
    fun <R> declareSubscriber(
        keyExpr: KeyExpr,
        handler: Handler<Sample, R>,
        onClose: (() -> Unit)? = null,
        reliability: Reliability = Reliability.BEST_EFFORT
    ): Result<Subscriber<R>> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> handler.handle(t) }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, null, reliability)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a handler to handle incoming samples.
     *
     * Example:
     * ```kotlin
     *
     * Session.open().onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareSubscriber(keyExpr, channel = Channel())
     *                 .onSuccess {
     *                     println("Declared subscriber on $keyExpr.")
     *                 }
     *             }
     *         }
     *     }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param channel [Channel] instance through which the received samples will be piped. Once the subscriber is
     *  closed, the channel is closed as well.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @param reliability The reliability the subscriber wishes to obtain from the network.
     * @return A result with the [Subscriber] in case of success.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        channel: Channel<Sample>,
        onClose: (() -> Unit)? = null,
        reliability: Reliability = Reliability.BEST_EFFORT
    ): Result<Subscriber<Channel<Sample>>> {
        val channelHandler = ChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> channelHandler.handle(t) }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, channelHandler.receiver(), reliability)
    }

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
     * Also, declared key expression provide additional optimizations by associating
     * it with a native key expression representation, minimizing the amount of operations
     * performed between the JVM and the Rust layer of this library.
     *
     * A declared key expression is associated to the session from which it was declared.
     * It can be undeclared with the function [undeclare], or alternatively when closing
     * the session it will be automatically undeclared. Undeclaring a key expression causes
     * it to be downgraded to a regular key expression without optimizations, this means
     * that operations can still be performed with it.
     *
     * When declaring a subscriber, a queryable, or a publisher, it is not necessary
     * to declare the key expression beforehand, since Zenoh is already informed of your
     * intent to use their key expressions repeatedly. It can be handy when doing instead
     * many repeated puts or reply operations.
     *
     * Example:
     * ```kotlin
     * Session.open().onSuccess { session -> session.use {
     *     val keyExpr = session.declareKeyExpr("demo/kotlin/example").getOrThrow()
     *     for (i in 0..999) {
     *         put(keyExpr, "Put number $i!")
     *     }
     * }}
     * ```
     *
     * @param keyExpr The intended key expression.
     * @return A result with the declared key expression.
     */
    fun declareKeyExpr(keyExpr: String): Result<KeyExpr> {
        return jniSession?.run {
            declareKeyExpr(keyExpr).onSuccess { declarations.add(it) }
        } ?: Result.failure(sessionClosedException)
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
    fun undeclare(keyExpr: KeyExpr): Result<Unit> {
        return jniSession?.run {
            undeclareKeyExpr(keyExpr)
        } ?: Result.failure(sessionClosedException)
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
     *             .payload("Payload example")
     *             .with { reply -> println("Received reply $reply") }
     *             .timeout(timeout)
     *             .wait()
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
     *             .payload("Payload example")
     *             .with { reply -> println("Received reply $reply") }
     *             .timeout(timeout)
     *             .wait()
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
     *         .wait()
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
     *         .wait()
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
     *             .wait()
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

    private fun resolvePublisher(keyExpr: KeyExpr, qos: QoS): Result<Publisher> {
        return jniSession?.run {
            declarePublisher(keyExpr, qos).onSuccess { declarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    internal fun <R> resolveSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        onClose: () -> Unit,
        receiver: R? = null,
        reliability: Reliability
    ): Result<Subscriber<R>> {
        return jniSession?.run {
            declareSubscriber(keyExpr, callback, onClose, receiver, reliability).onSuccess { declarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    internal fun <R> resolveQueryable(
        keyExpr: KeyExpr,
        callback: Callback<Query>,
        onClose: () -> Unit,
        receiver: R?,
        complete: Boolean
    ): Result<Queryable<R>> {
        return jniSession?.run {
            declareQueryable(keyExpr, callback, onClose, receiver, complete).onSuccess { declarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    internal fun <R> resolveGet(
        selector: Selector,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R?,
        timeout: Duration,
        target: QueryTarget,
        consolidation: ConsolidationMode,
        payload: ZBytes?,
        encoding: Encoding?,
        attachment: ZBytes?,
    ): Result<R?> {
        return jniSession?.run {
            performGet(
                selector,
                callback,
                onClose,
                receiver,
                timeout,
                target,
                consolidation,
                payload,
                encoding,
                attachment
            )
        } ?: Result.failure(sessionClosedException)
    }

    internal fun resolvePut(keyExpr: KeyExpr, put: Put): Result<Unit> = runCatching {
        jniSession?.run { performPut(keyExpr, put) }
    }

    internal fun resolveDelete(keyExpr: KeyExpr, delete: Delete): Result<Unit> = runCatching {
        jniSession?.run { performDelete(keyExpr, delete) }
    }

    /** Launches the session through the jni session, returning the [Session] on success. */
    private fun launch(): Result<Session> = runCatching {
        jniSession = JNISession()
        return jniSession!!.open(config)
            .map { this@Session }
            .onFailure { jniSession = null }
    }
}

