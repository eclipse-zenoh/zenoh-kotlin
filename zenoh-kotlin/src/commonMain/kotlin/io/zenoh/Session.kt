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

import io.zenoh.annotations.Unstable
import io.zenoh.exceptions.ZError
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNISession
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.config.ZenohId
import io.zenoh.ext.CacheConfig
import io.zenoh.ext.HistoryConfig
import io.zenoh.ext.MissDetectionConfig
import io.zenoh.ext.RecoveryConfig
import io.zenoh.liveliness.Liveliness
import io.zenoh.pubsub.AdvancedPublisher
import io.zenoh.pubsub.AdvancedSubscriber
import io.zenoh.pubsub.Delete
import io.zenoh.pubsub.Publisher
import io.zenoh.pubsub.Put
import io.zenoh.query.*
import io.zenoh.query.Query
import io.zenoh.query.Queryable
import io.zenoh.sample.Sample
import io.zenoh.query.Selector
import io.zenoh.qos.Reliability
import io.zenoh.session.SessionDeclaration
import io.zenoh.session.SessionInfo
import io.zenoh.pubsub.Subscriber
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference
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

    internal var jniSession: JNISession? = null

    // AdvancedSubscribers, Subscribers and Queryables that keep running despite losing references to them.
    private var strongDeclarations = mutableListOf<SessionDeclaration>()

    // AdvancedPublishers, Publishers and queriers that shouldn't be kept alive when losing all references to them.
    private var weakDeclarations = mutableListOf<WeakReference<SessionDeclaration>>()

    companion object {

        internal val sessionClosedException = ZError("Session is closed.")

        /**
         * Open a [Session] with the provided [Config].
         *
         * Note: Use [Zenoh.open] to launch a session.
         *
         * @param config The configuration for the session.
         * @return A [Result] with the [Session] on success.
         */
        internal fun open(config: Config): Result<Session> {
            val session = Session(config)
            return session.launch()
        }
    }

    /** Close the session. */
    override fun close() {
        strongDeclarations.removeIf {
            it.undeclare()
            true
        }

        weakDeclarations.removeIf {
            it.get()?.undeclare()
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
     * Zenoh.open(Config.default()).onSuccess {
     *     it.use { session ->
     *         "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declarePublisher(keyExpr).onSuccess { pub ->
     *                 pub.use {
     *                     println("Publisher declared on $keyExpr.")
     *                     var i = 0
     *                     while (true) {
     *                         val payload = "Hello for the ${i}th time!"
     *                         println(payload)
     *                         pub.put(payload)
     *                         Thread.sleep(1000)
     *                         i++
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the publisher will be associated to.
     * @param qos The [QoS] configuration of the publisher.
     * @param encoding The default [Encoding] for the publications.
     * @param reliability The [Reliability] the publisher wishes to obtain from the network.
     * @return The result of the declaration, returning the publisher in case of success.
     */
    fun declarePublisher(
        keyExpr: KeyExpr,
        qos: QoS = QoS.defaultPush,
        encoding: Encoding = Encoding.default(),
        reliability: Reliability = Reliability.RELIABLE
    ): Result<Publisher> {
        return resolvePublisher(keyExpr, qos, encoding, reliability)
    }

    /**
     * Declare a [Publisher] on the session.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess {
     *     it.use { session ->
     *         "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareAdvancedPublisher(keyExpr).onSuccess { pub ->
     *                 pub.use {
     *                     println("Advanced publisher declared on $keyExpr.")
     *                     var i = 0
     *                     while (true) {
     *                         val payload = "Hello for the ${i}th time!"
     *                         println(payload)
     *                         pub.put(payload)
     *                         Thread.sleep(1000)
     *                         i++
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the publisher will be associated to.
     * @param qos The [QoS] configuration of the publisher.
     * @param encoding The default [Encoding] for the publications.
     * @param cacheConfig Cache configuration to attach to this publisher. The cache can be used for history and/or recovery.
     * @param sampleMissDetection Configure options to allow matching [AdvancedSubscriber] to detect lost samples
     * and optionally ask for retransmission. Retransmission can only be achieved if cache is enabled.
     * @param publisherDetection Allow this [AdvancedPublisher] to be detected by [AdvancedSubscriber].
     * @param reliability The [Reliability] the publisher wishes to obtain from the network.
     * @return The result of the declaration, returning the advanced publisher in case of success.
     */
    @Unstable
    fun declareAdvancedPublisher(
        keyExpr: KeyExpr,
        qos: QoS = QoS.defaultPush,
        encoding: Encoding = Encoding.default(),
        reliability: Reliability = Reliability.RELIABLE,
        cacheConfig: CacheConfig? = null,
        sampleMissDetection: MissDetectionConfig? = null,
        publisherDetection: Boolean = false
    ): Result<AdvancedPublisher> {
        return resolveAdvancedPublisher(keyExpr, qos, encoding, reliability,
            cacheConfig, sampleMissDetection, publisherDetection)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a callback to handle incoming samples.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
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
     * @return A result with the [Subscriber] in case of success.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        onClose: (() -> Unit)? = null,
    ): Result<Subscriber<Unit>> {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, Unit)
    }

    /**
     * Declare an [AdvancedSubscriber] on the session, specifying a callback to handle incoming samples.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareAdvancedSubscriber(keyExpr, callback = { sample -> println(sample) }).onSuccess {
     *                 println("Declared advanced subscriber on $keyExpr.")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param historyConfig Parameter to configure query for historical data
     * @param recoveryConfig Configuration for lost sample recovery
     * @param subscriberDetection Allow this subscriber to be detected through liveliness.
     * @param callback Callback to handle the received samples.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A result with the [Subscriber] in case of success.
     */
    @Unstable
    fun declareAdvancedSubscriber(
        keyExpr: KeyExpr,
        historyConfig: HistoryConfig? = null,
        recoveryConfig: RecoveryConfig? = null,
        subscriberDetection: Boolean = false,
        callback: Callback<Sample>,
        onClose: (() -> Unit)? = null,
    ): Result<AdvancedSubscriber<Unit>> {
        val resolvedOnClose = fun() {
            onClose?.invoke()
        }
        return resolveAdvancedSubscriber(keyExpr, historyConfig, recoveryConfig, subscriberDetection,
            callback, resolvedOnClose, Unit)
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
     * Zenoh.open(Config.default()).onSuccess { session ->
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
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called
     *  upon closing the session.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A result with the [Subscriber] in case of success.
     */
    fun <R> declareSubscriber(
        keyExpr: KeyExpr,
        handler: Handler<Sample, R>,
        onClose: (() -> Unit)? = null,
    ): Result<Subscriber<R>> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> handler.handle(t) }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, handler.receiver())
    }

    /**
     * Declare an [AdvancedSubscriber] on the session, specifying a handler to handle incoming samples.
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
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             session.declareAdvancedSubscriber(keyExpr, handler = ExampleHandler())
     *                 .onSuccess {
     *                     println("Declared advanced subscriber on $keyExpr.")
     *                 }
     *             }
     *         }
     *     }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param historyConfig Parameter to configure query for historical data
     * @param recoveryConfig Configuration for lost sample recovery
     * @param subscriberDetection Allow this subscriber to be detected through liveliness.
     * @param handler [Handler] implementation to handle the received samples. [Handler.onClose] will be called
     *  upon closing the session.
     * @param onClose Callback function to be called when the subscriber is closed.
     * @return A result with the [Subscriber] in case of success.
     */
    @Unstable
    fun <R> declareAdvancedSubscriber(
        keyExpr: KeyExpr,
        historyConfig: HistoryConfig? = null,
        recoveryConfig: RecoveryConfig? = null,
        subscriberDetection: Boolean = false,
        handler: Handler<Sample, R>,
        onClose: (() -> Unit)? = null,
    ): Result<AdvancedSubscriber<R>> {
        val resolvedOnClose = fun() {
            handler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> handler.handle(t) }
        return resolveAdvancedSubscriber(keyExpr, historyConfig, recoveryConfig, subscriberDetection,
            callback, resolvedOnClose, handler.receiver())
    }

    /**
     * Declare a [Subscriber] on the session, specifying a [Channel] to pipe the received samples.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             val samplesChannel = Channel()
     *             session.declareSubscriber(keyExpr, channel = samplesChannel)
     *                 .onSuccess {
     *                     println("Declared subscriber on $keyExpr.")
     *                 }
     *             }
     *             // ...
     *         }
     *     }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param channel [Channel] instance through which the received samples will be piped. Once the subscriber is
     *  closed, the channel is closed as well.
     * @param onClose Callback function to be called when the subscriber is closed. [Handler.onClose] will be called
     *  upon closing the session.
     * @return A result with the [Subscriber] in case of success.
     */
    fun declareSubscriber(
        keyExpr: KeyExpr,
        channel: Channel<Sample>,
        onClose: (() -> Unit)? = null,
    ): Result<Subscriber<Channel<Sample>>> {
        val channelHandler = ChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> channelHandler.handle(t) }
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, channelHandler.receiver())
    }

    /**
     * Declare an [AdvancedSubscriber] on the session, specifying a [Channel] to pipe the received samples.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "demo/kotlin/sub".intoKeyExpr().onSuccess { keyExpr ->
     *             val samplesChannel = Channel()
     *             session.declareAdvancedSubscriber(keyExpr, channel = samplesChannel)
     *                 .onSuccess {
     *                     println("Declared advanced subscriber on $keyExpr.")
     *                 }
     *             }
     *             // ...
     *         }
     *     }
     * ```
     *
     * @param keyExpr The [KeyExpr] the subscriber will be associated to.
     * @param historyConfig Parameter to configure query for historical data
     * @param recoveryConfig Configuration for lost sample recovery
     * @param subscriberDetection Allow this subscriber to be detected through liveliness.
     * @param channel [Channel] instance through which the received samples will be piped. Once the subscriber is
     *  closed, the channel is closed as well.
     * @param onClose Callback function to be called when the subscriber is closed. [Handler.onClose] will be called
     *  upon closing the session.
     * @return A result with the [Subscriber] in case of success.
     */
    @Unstable
    fun declareAdvancedSubscriber(
        keyExpr: KeyExpr,
        historyConfig: HistoryConfig? = null,
        recoveryConfig: RecoveryConfig? = null,
        subscriberDetection: Boolean = false,
        channel: Channel<Sample>,
        onClose: (() -> Unit)? = null,
    ): Result<AdvancedSubscriber<Channel<Sample>>> {
        val channelHandler = ChannelHandler(channel)
        val resolvedOnClose = fun() {
            channelHandler.onClose()
            onClose?.invoke()
        }
        val callback = Callback { t: Sample -> channelHandler.handle(t) }
        return resolveAdvancedSubscriber(keyExpr, historyConfig, recoveryConfig, subscriberDetection,
            callback, resolvedOnClose, channelHandler.receiver())
    }

    /**
     * Declare a [Queryable] on the session with a callback.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *         println("Declaring Queryable")
     *         val queryable = session.declareQueryable(keyExpr, callback = { query ->
     *              query.reply(keyExpr, payload = ZBytes.from("Hello!"))
     *                   .onSuccess { println("Replied hello.") }
     *                   .onFailure { println(it) }
     *         }).getOrThrow()
     *     }
     * }}
     * ```
     *
     * @param keyExpr The [KeyExpr] the queryable will be associated to.
     * @param callback The callback to handle the received queries.
     * @param onClose Optional callback to be run upon closing the queryable.
     * @param complete The queryable completeness.
     * @return A result with the queryable.
     * @see Query
     */
    fun declareQueryable(
        keyExpr: KeyExpr,
        callback: Callback<Query>,
        onClose: (() -> Unit)? = null,
        complete: Boolean = false
    ): Result<Queryable<Unit>> {
        return resolveQueryable(keyExpr, callback, fun() { onClose?.invoke() }, Unit, complete)
    }

    /**
     * Declare a [Queryable] on the session with a [Handler].
     *
     * Example: we create a class `ExampleHandler` that implements the [Handler] interface to reply
     * to the incoming queries:
     *
     * ```kotlin
     * class ExampleHandler: Handler<Query, Unit> {
     *     override fun handle(t: Query) = query.reply(query.keyExpr, ZBytes.from("Hello!"))
     *
     *     override fun receiver() = Unit
     *
     *     override fun onClose() = println("Closing handler")
     * }
     * ```
     *
     * Then we'd use it as follows:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *         println("Declaring Queryable")
     *         val exampleHandler = ExampleHandler()
     *         val queryable = session.declareQueryable(keyExpr, handler = exampleHandler).getOrThrow()
     *         // ...
     *     }
     * }}
     * ```
     *
     * @param keyExpr The [KeyExpr] the queryable will be associated to.
     * @param handler The [Handler] to handle the incoming queries. [Handler.onClose] will be called upon
     *  closing the queryable.
     * @param onClose Optional callback to be run upon closing the queryable.
     * @param complete The completeness of the queryable.
     * @return A result with the queryable.
     */
    fun <R> declareQueryable(
        keyExpr: KeyExpr,
        handler: Handler<Query, R>,
        onClose: (() -> Unit)? = null,
        complete: Boolean = false
    ): Result<Queryable<R>> {
        return resolveQueryable(keyExpr, { t: Query -> handler.handle(t) }, fun() {
            handler.onClose()
            onClose?.invoke()
        }, handler.receiver(), complete)
    }

    /**
     * Declare a [Queryable] with a [Channel] to pipe the incoming queries.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(config).onSuccess { session ->
     *     session.use {
     *         key.intoKeyExpr().onSuccess { keyExpr ->
     *             println("Declaring Queryable on $key...")
     *             session.declareQueryable(keyExpr, Channel()).onSuccess { queryable ->
     *                 runBlocking {
     *                     for (query in queryable.receiver) {
     *                         val valueInfo = query.value?.let { value -> " with value '$value'" } ?: ""
     *                         println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
     *                         query.reply(keyExpr, payload = ZBytes.from("Example reply"))
     *                             .onSuccess { println(">> [Queryable ] Performed reply...") }
     *                             .onFailure { println(">> [Queryable ] Error sending reply: $it") }
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] the queryable will be associated to.
     * @param channel The [Channel] to receive the incoming queries. It will be closed upon closing the queryable.
     * @param onClose Callback to be run upon closing the queryable.
     * @param complete The completeness of the queryable.
     * @return A result with the queryable, where the [Queryable.receiver] is the provided [Channel].
     */
    fun declareQueryable(
        keyExpr: KeyExpr,
        channel: Channel<Query>,
        onClose: (() -> Unit)? = null,
        complete: Boolean = false
    ): Result<Queryable<Channel<Query>>> {
        val handler = ChannelHandler(channel)
        return resolveQueryable(keyExpr, { t: Query -> handler.handle(t) }, fun() {
            handler.onClose()
            onClose?.invoke()
        }, handler.receiver(), complete)
    }

    /**
     * Declare a [Querier].
     *
     * A querier allows to send queries to a queryable.
     *
     * Queriers are automatically undeclared when dropped.
     *
     * Example:
     * ```kotlin
     * val session = Zenoh.open(config).getOrThrow();
     * val keyExpr = "a/b/c".intoKeyExpr().getOrThrow();
     *
     * val querier = session.declareQuerier(keyExpr).getOrThrow();
     * querier.get(callback = {
     *         it.result.onSuccess { sample ->
     *             println(">> Received ('${sample.keyExpr}': '${sample.payload}')")
     *         }.onFailure { error ->
     *             println(">> Received (ERROR: '${error.message}')")
     *         }
     *     }
     * )
     * ```
     */
    @Unstable
    fun declareQuerier(
        keyExpr: KeyExpr,
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        qos: QoS = QoS.defaultRequest,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        timeout: Duration = Duration.ofMillis(10000)
    ): Result<Querier> {
        return resolveQuerier(keyExpr, target, consolidation, qos, timeout)
    }

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
     * Zenoh.open(Config.default()).onSuccess { session -> session.use {
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
            declareKeyExpr(keyExpr).onSuccess { strongDeclarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    /**
     * Undeclare a [KeyExpr].
     *
     * The key expression must have been previously declared on the session with [declareKeyExpr],
     * otherwise the operation will result in a failure.
     *
     * @param keyExpr The key expression to undeclare.
     * @return A result with the status of the undeclare operation.
     */
    fun undeclare(keyExpr: KeyExpr): Result<Unit> {
        return jniSession?.run {
            undeclareKeyExpr(keyExpr)
        } ?: Result.failure(sessionClosedException)
    }

    /**
     * Performs a Get query on the [selector], handling the replies with a callback.
     *
     * A callback must be provided to handle the incoming replies. A basic query can be achieved
     * as follows:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *             session.get(selector, callback = { reply -> println(reply) })
     *         }
     *     }
     * }
     * ```
     *
     * @param selector The [Selector] on top of which the get query will be performed.
     * @param callback [Callback] to handle the replies.
     * @param payload Optional payload for the query.
     * @param encoding Encoding of the [payload].
     * @param attachment Optional attachment.
     * @param timeout Timeout of the query.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @param qos The [QoS] configuration.
     * @return A [Result] with the status of the query. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun get(
        selector: Selector,
        callback: Callback<Reply>,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<Unit> {
        return resolveGet(
            selector = selector,
            callback = callback,
            onClose = fun() { onClose?.invoke() },
            receiver = Unit,
            timeout = timeout,
            target = target,
            consolidation = consolidation,
            payload = payload,
            encoding = encoding,
            attachment = attachment,
            qos = qos
        )
    }

    fun get(
        selector: Selector,
        callback: Callback<Reply>,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<Unit> = get(
        selector,
        callback,
        ZBytes.from(payload),
        encoding,
        attachment?.let { ZBytes.from(it) },
        timeout,
        target,
        consolidation,
        onClose,
        qos
    )

    /**
     * Performs a Get query on the [selector], handling the replies with a [Handler].
     *
     * A handler must be provided to handle the incoming replies. For instance, imagine we implement
     * a `QueueHandler`:
     * ```kotlin
     * class QueueHandler<T : ZenohType> : Handler<T, ArrayDeque<T>> {
     *     private val queue: ArrayDeque<T> = ArrayDeque()
     *
     *     override fun handle(t: T) {
     *         queue.add(t)
     *     }
     *
     *     override fun receiver(): ArrayDeque<T> {
     *         return queue
     *     }
     *
     *     override fun onClose() {
     *         println("Received in total ${queue.size} elements.")
     *     }
     * }
     * ```
     *
     * then we could use it as follows:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *               val handler = QueueHandler<Reply>()
     *               val receiver = session.get(selector, handler).getOrThrow()
     *               // ...
     *               for (reply in receiver) {
     *                   println(reply)
     *               }
     *          }
     *     }
     * }
     * ```
     *
     * @param selector The [Selector] on top of which the get query will be performed.
     * @param handler [Handler] to handle the replies.
     * @param payload Optional payload for the query.
     * @param encoding Encoding of the [payload].
     * @param attachment Optional attachment.
     * @param timeout Timeout of the query.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @param qos The [QoS] configuration.
     * @return A [Result] with the [handler]'s receiver of type [R]. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun <R> get(
        selector: Selector,
        handler: Handler<Reply, R>,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<R> {
        return resolveGet(
            selector = selector,
            callback = { r: Reply -> handler.handle(r) },
            onClose = fun() {
                handler.onClose()
                onClose?.invoke()
            },
            receiver = handler.receiver(),
            timeout = timeout,
            target = target,
            consolidation = consolidation,
            payload = payload,
            encoding = encoding,
            attachment = attachment,
            qos = qos
        )
    }

    fun <R> get(
        selector: Selector,
        handler: Handler<Reply, R>,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<R> = get(
        selector,
        handler,
        ZBytes.from(payload),
        encoding,
        attachment?.let { ZBytes.from(it) },
        timeout,
        target,
        consolidation,
        onClose,
        qos
    )

    /**
     * Performs a Get query on the [selector], handling the replies with a blocking [Channel].
     *
     * Example:
     * ```kotlin
     * Zenoh.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *               session.get(selector, channel = Channel()).onSuccess { channel ->
     *                   runBlocking {
     *                       for (reply in channel) {
     *                           println("Received $reply")
     *                       }
     *                   }
     *               }.onFailure {
     *                    println("Error: $it")
     *               }
     *          }
     *     }
     * }
     * ```
     *
     * @param selector The [Selector] on top of which the get query will be performed.
     * @param channel Blocking [Channel] to handle the replies.
     * @param payload Optional payload for the query.
     * @param encoding Encoding of the [payload].
     * @param attachment Optional attachment.
     * @param timeout Timeout of the query.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @param qos The [QoS] configuration.
     * @return A [Result] with the [channel] on success. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun get(
        selector: Selector,
        channel: Channel<Reply>,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<Channel<Reply>> {
        val channelHandler = ChannelHandler(channel)
        return resolveGet(
            selector,
            callback = { r: Reply -> channelHandler.handle(r) },
            onClose = fun() {
                channelHandler.onClose()
                onClose?.invoke()
            },
            receiver = channelHandler.receiver(),
            timeout,
            target,
            consolidation,
            payload,
            encoding,
            attachment,
            qos
        )
    }

    fun get(
        selector: Selector,
        channel: Channel<Reply>,
        payload: String,
        encoding: Encoding? = null,
        attachment: String? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.AUTO,
        onClose: (() -> Unit)? = null,
        qos: QoS = QoS.defaultRequest
    ): Result<Channel<Reply>> = get(
        selector,
        channel,
        ZBytes.from(payload),
        encoding,
        attachment?.let { ZBytes.from(it) },
        timeout,
        target,
        consolidation,
        onClose,
        qos
    )

    /**
     * Declare a [Put] with the provided value on the specified key expression.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(config).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoKeyExpr().onSuccess { keyExpr ->
     *             session.put(keyExpr, payload = ZBytes.from("Example payload")).getOrThrow()
     *         }
     *         // ...
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the put operation.
     * @param payload The payload to be put.
     * @param encoding The [Encoding] of the payload.
     * @param qos The [QoS] configuration.
     * @param attachment Optional attachment.
     * @param reliability The [Reliability] configuration.
     * @return A [Result] with the status of the put operation.
     */
    fun put(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: QoS = QoS.defaultPush,
        attachment: IntoZBytes? = null,
        reliability: Reliability = Reliability.RELIABLE
    ): Result<Unit> {
        val put = Put(keyExpr, payload.into(), encoding, qos, attachment?.into(), reliability)
        return resolvePut(keyExpr, put)
    }

    fun put(
        keyExpr: KeyExpr,
        payload: String,
        encoding: Encoding = Encoding.default(),
        qos: QoS = QoS.defaultPush,
        attachment: String? = null,
        reliability: Reliability = Reliability.RELIABLE
    ): Result<Unit> =
        put(keyExpr, ZBytes.from(payload), encoding, qos, attachment?.let { ZBytes.from(it) }, reliability)

    /**
     * Perform a delete operation.
     *
     * Example:
     * ```kotlin
     * Zenoh.open(config).onSuccess { session ->
     *     session.use {
     *         key.intoKeyExpr().onSuccess { keyExpr ->
     *             session.delete(keyExpr).onSuccess {
     *                 println("Deleting resources matching '$keyExpr'...")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the delete operation.
     * @param qos The [QoS] configuration.
     * @param attachment Optional [ZBytes] attachment.
     * @param reliability The [Reliability] wished to be obtained from the network.
     * @return a [Result] with the status of the operation.
     */
    fun delete(
        keyExpr: KeyExpr,
        qos: QoS = QoS.defaultPush,
        attachment: IntoZBytes? = null,
        reliability: Reliability = Reliability.RELIABLE
    ): Result<Unit> {
        val delete = Delete(keyExpr, qos, attachment?.into(), reliability)
        return resolveDelete(keyExpr, delete)
    }

    fun delete(
        keyExpr: KeyExpr,
        qos: QoS = QoS.defaultPush,
        attachment: String,
        reliability: Reliability = Reliability.RELIABLE
    ): Result<Unit> {
        val delete = Delete(keyExpr, qos, ZBytes.from(attachment), reliability)
        return resolveDelete(keyExpr, delete)
    }

    /**
     * Obtain a [Liveliness] instance tied to this Zenoh session.
     */
    fun liveliness(): Liveliness {
        return Liveliness(this)
    }

    /** Returns if session is open or has been closed. */
    fun isClosed(): Boolean {
        return jniSession == null
    }

    /**
     * Returns the [SessionInfo] of this session.
     */
    fun info(): SessionInfo {
        return SessionInfo(this)
    }

    private fun resolvePublisher(
        keyExpr: KeyExpr,
        qos: QoS,
        encoding: Encoding,
        reliability: Reliability
    ): Result<Publisher> {
        return jniSession?.run {
            declarePublisher(keyExpr, qos, encoding, reliability).onSuccess { weakDeclarations.add(WeakReference(it)) }
        } ?: Result.failure(sessionClosedException)
    }

    @Unstable
    private fun resolveAdvancedPublisher(
        keyExpr: KeyExpr,
        qos: QoS,
        encoding: Encoding,
        reliability: Reliability,
        cacheConfig: CacheConfig? = null,
        sampleMissDetection: MissDetectionConfig? = null,
        publisherDetection: Boolean = false
    ): Result<AdvancedPublisher> {
        return jniSession?.run {
            declareAdvancedPublisher(keyExpr, qos, encoding, reliability,
                cacheConfig, sampleMissDetection, publisherDetection).onSuccess { weakDeclarations.add(WeakReference(it)) }
        } ?: Result.failure(sessionClosedException)
    }

    private fun <R> resolveSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        onClose: () -> Unit,
        receiver: R
    ): Result<Subscriber<R>> {
        return jniSession?.run {
            declareSubscriber(keyExpr, callback, onClose, receiver).onSuccess { strongDeclarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    @Unstable
    private fun <R> resolveAdvancedSubscriber(
        keyExpr: KeyExpr,
        history: HistoryConfig?,
        recovery: RecoveryConfig?,
        subscriberDetection: Boolean,
        callback: Callback<Sample>,
        onClose: () -> Unit,
        receiver: R
    ): Result<AdvancedSubscriber<R>> {
        return jniSession?.run {
            declareAdvancedSubscriber(keyExpr, history, recovery, subscriberDetection, callback, onClose, receiver).onSuccess { strongDeclarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    private fun <R> resolveQueryable(
        keyExpr: KeyExpr,
        callback: Callback<Query>,
        onClose: () -> Unit,
        receiver: R,
        complete: Boolean
    ): Result<Queryable<R>> {
        return jniSession?.run {
            declareQueryable(keyExpr, callback, onClose, receiver, complete).onSuccess { strongDeclarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    @OptIn(Unstable::class)
    private fun resolveQuerier(
        keyExpr: KeyExpr,
        target: QueryTarget,
        consolidation: ConsolidationMode,
        qos: QoS,
        timeout: Duration
    ): Result<Querier> {
        return jniSession?.run {
            declareQuerier(keyExpr, target, consolidation, qos, timeout).onSuccess { weakDeclarations.add(WeakReference(it)) }
        } ?: Result.failure(sessionClosedException)
    }

    private fun <R> resolveGet(
        selector: Selector,
        callback: Callback<Reply>,
        onClose: () -> Unit,
        receiver: R,
        timeout: Duration,
        target: QueryTarget,
        consolidation: ConsolidationMode,
        payload: IntoZBytes?,
        encoding: Encoding?,
        attachment: IntoZBytes?,
        qos: QoS
    ): Result<R> {
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
                attachment,
                qos
            )
        } ?: Result.failure(sessionClosedException)
    }

    private fun resolvePut(keyExpr: KeyExpr, put: Put): Result<Unit> = runCatching {
        jniSession?.run { performPut(keyExpr, put) }
    }

    private fun resolveDelete(keyExpr: KeyExpr, delete: Delete): Result<Unit> = runCatching {
        jniSession?.run { performDelete(keyExpr, delete) }
    }

    internal fun zid(): Result<ZenohId> {
        return jniSession?.zid() ?: Result.failure(sessionClosedException)
    }

    internal fun getPeersId(): Result<List<ZenohId>> {
        return jniSession?.peersZid() ?: Result.failure(sessionClosedException)
    }

    internal fun getRoutersId(): Result<List<ZenohId>> {
        return jniSession?.routersZid() ?: Result.failure(sessionClosedException)
    }

    /** Launches the session through the jni session, returning the [Session] on success. */
    private fun launch(): Result<Session> = runCatching {
        jniSession = JNISession()
        return jniSession!!.open(config)
            .map { this@Session }
            .onFailure { jniSession = null }
    }
}
