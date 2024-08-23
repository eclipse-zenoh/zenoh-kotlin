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
         * Open a [Session] with the provided [Config].
         *
         * @param config The configuration for the session.
         * @return A [Result] with the [Session] on success.
         */
        fun open(config: Config? = null): Result<Session> {
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
     * Session.open(Config.default()).onSuccess {
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
     * @return The result of the declaration, returning the publisher in case of success.
     */
    fun declarePublisher(keyExpr: KeyExpr, qos: QoS = QoS.default()): Result<Publisher> {
        return resolvePublisher(keyExpr, qos)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a callback to handle incoming samples.
     *
     * Example:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
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
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, Unit, reliability)
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
     * Session.open(Config.default()).onSuccess { session ->
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
        return resolveSubscriber(keyExpr, callback, resolvedOnClose, handler.receiver(), reliability)
    }

    /**
     * Declare a [Subscriber] on the session, specifying a channel pipe the received samples.
     *
     * Example:
     * ```kotlin
     *
     * Session.open(Config.default()).onSuccess { session ->
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
     * Declare a [Queryable] on the session with a callback.
     *
     * Example:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session -> session.use {
     *     "demo/kotlin/greeting".intoKeyExpr().onSuccess { keyExpr ->
     *         println("Declaring Queryable")
     *         val queryable = session.declareQueryable(keyExpr, callback = { query ->
     *              query.replySuccess(keyExpr, value = Value("Hello!"))
     *                   .onSuccess { println("Replied hello.") }
     *                   .onFailure { println(it) }
     *         }).getOrThrow()
     *     }
     * }}
     * ```
     *
     * @param keyExpr The [KeyExpr] the queryable will be associated to.
     * @param callback The callback to handle the received queries.
     * @param onClose Callback to be run upon closing the queryable.
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
     *     override fun handle(t: Query) = query.replySuccess(query.keyExpr, value = Value("Hello!"))
     *
     *     override fun receiver() = Unit
     *
     *     override fun onClose() = println("Closing handler")
     * }
     * ```
     *
     * Then we'd use it as follows:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session -> session.use {
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
     * @param onClose Callback to be run upon closing the queryable.
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
     * Session.open(config).onSuccess { session ->
     *     session.use {
     *         key.intoKeyExpr().onSuccess { keyExpr ->
     *             println("Declaring Queryable on $key...")
     *             session.declareQueryable(keyExpr, Channel()).onSuccess { queryable ->
     *                 runBlocking {
     *                     for (query in queryable.receiver) {
     *                         val valueInfo = query.value?.let { value -> " with value '$value'" } ?: ""
     *                         println(">> [Queryable] Received Query '${query.selector}' $valueInfo")
     *                         query.replySuccess(keyExpr, value = Value("Example reply"))
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
     * Session.open(Config.default()).onSuccess { session -> session.use {
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
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *             session.get(selector, callback = { reply -> println(reply) })
     *         }
     *     }
     * }
     * ```
     *
     * Additionally, other optional parameters to the query can be specified, and the result
     * of the operation can be checked as well:
     *
     * Example:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *             session.get(
     *                 selector,
     *                 callback = { reply -> println(reply) },
     *                 value = Value("Example value"),
     *                 target = QueryTarget.BEST_MATCHING,
     *                 attachment = ZBytes.from("Example attachment"),
     *                 timeout = Duration.ofMillis(1000),
     *                 onClose = { println("Query terminated.") }
     *             ).onSuccess {
     *                 println("Get query launched...")
     *             }.onFailure {
     *                 println("Error: $it")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param selector The [Selector] on top of which the get query will be performed.
     * @param callback [Callback] to handle the replies.
     * @param value Optional [Value] for the query.
     * @param attachment Optional attachment.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @return A [Result] with the status of the query. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun get(
        selector: Selector,
        callback: Callback<Reply>,
        value: Value? = null,
        attachment: ZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.NONE,
        onClose: (() -> Unit)? = null
    ) : Result<Unit> {
        return resolveGet (
            selector = selector,
            callback = callback,
            onClose = fun() { onClose?.invoke() },
            receiver = Unit,
            timeout = timeout,
            target = target,
            consolidation = consolidation,
            value = value,
            attachment = attachment
        )
    }

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
     * Session.open(Config.default()).onSuccess { session ->
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
     * Additionally, other optional parameters to the query can be specified, and the result
     * of the operation can be checked as well:
     *
     * Example:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *             val handler = QueueHandler<Reply>()
     *             session.get(
     *                 selector,
     *                 handler,
     *                 value = Value("Example value"),
     *                 target = QueryTarget.BEST_MATCHING,
     *                 attachment = ZBytes.from("Example attachment"),
     *                 timeout = Duration.ofMillis(1000),
     *                 onClose = { println("Query terminated.") }
     *             ).onSuccess { receiver ->
     *                 println("Get query launched...")
     *                 // ...
     *                 for (reply in receiver) {
     *                     println(reply)
     *                 }
     *             }.onFailure {
     *                 println("Error: $it")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param selector The [Selector] on top of which the get query will be performed.
     * @param handler [Handler] to handle the replies.
     * @param value Optional [Value] for the query.
     * @param attachment Optional attachment.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @return A [Result] with the [handler]'s receiver of type [R]. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun <R> get(
        selector: Selector,
        handler: Handler<Reply, R>,
        value: Value? = null,
        attachment: ZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.NONE,
        onClose: (() -> Unit)? = null
    ) : Result<R> {
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
            value = value,
            attachment = attachment
        )
    }

    /**
     * Performs a Get query on the [selector], handling the replies with a blocking [Channel].
     *
     * Example:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
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
     * Additionally, other optional parameters to the query can be specified, and the result
     * of the operation can be checked as well:
     *
     * Example:
     *
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoSelector().onSuccess { selector ->
     *               session.get(selector,
     *                   channel = Channel(),
     *                   value = Value("Example value"),
     *                   target = QueryTarget.BEST_MATCHING,
     *                   attachment = ZBytes.from("Example attachment"),
     *                   timeout = Duration.ofMillis(1000),
     *                   onClose = { println("Query terminated.") }
     *               ).onSuccess { channel ->
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
     * @param value Optional [Value] for the query.
     * @param attachment Optional attachment.
     * @param target The [QueryTarget] of the query.
     * @param consolidation The [ConsolidationMode] configuration.
     * @param onClose Callback to be executed when the query is terminated.
     * @return A [Result] with the [channel] on success. When [Result.success] is returned, that means
     *   the query was properly launched and not that it has received all the possible replies (this
     *   can't be known from the perspective of the query).
     */
    fun get(
        selector: Selector,
        channel: Channel<Reply>,
        value: Value? = null,
        attachment: ZBytes? = null,
        timeout: Duration = Duration.ofMillis(10000),
        target: QueryTarget = QueryTarget.BEST_MATCHING,
        consolidation: ConsolidationMode = ConsolidationMode.NONE,
        onClose: (() -> Unit)? = null
    ) : Result<Channel<Reply>> {
        val channelHandler = ChannelHandler(channel)
        return resolveGet(
            selector = selector,
            callback = { r: Reply -> channelHandler.handle(r) },
            onClose = fun() {
                channelHandler.onClose()
                onClose?.invoke()
            },
            receiver = channelHandler.receiver(),
            timeout = timeout,
            target = target,
            consolidation = consolidation,
            value = value,
            attachment = attachment
        )
    }

    /**
     * Declare a [Put] with the provided value on the specified key expression.
     *
     * Example:
     * ```kotlin
     * Session.open(config).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoKeyExpr().onSuccess { keyExpr ->
     *             session.put(keyExpr, value = Value("Example value")).getOrThrow()
     *         }
     *         // ...
     *     }
     * }
     * ```
     *
     * Additionally, a [QoS] configuration can be specified as well as an attachment, for instance:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoKeyExpr().onSuccess { keyExpr ->
     *             val exampleQoS = QoS(
     *                  congestionControl = CongestionControl.DROP,
     *                  express = true,
     *                  priority = Priority.DATA_HIGH)
     *             val exampleAttachment = "exampleAttachment".into()
     *             session.put(
     *                  keyExpr,
     *                  value = Value("Example value"),
     *                  qos = exampleQoS,
     *                  attachment = exampleAttachment).getOrThrow()
     *         }
     *         // ...
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the put operation.
     * @param value The [Value] to be put.
     * @param qos The [QoS] configuration.
     * @param attachment Optional attachment.
     * @return A [Result] with the status of the put operation.
     */
    fun put(keyExpr: KeyExpr, value: Value, qos: QoS = QoS.default(), attachment: ZBytes? = null) : Result<Unit> {
        val put = Put(keyExpr, value, qos, attachment)
        return resolvePut(keyExpr, put)
    }

    /**
     * Declare a [Put] with the provided message on the specified key expression.
     *
     * Example:
     * ```kotlin
     * Session.open(config).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoKeyExpr().onSuccess { keyExpr ->
     *             session.put(keyExpr, "Example message").getOrThrow()
     *         }
     *         // ...
     *     }
     * }
     * ```
     *
     * Additionally, a [QoS] configuration can be specified as well as an attachment, for instance:
     * ```kotlin
     * Session.open(Config.default()).onSuccess { session ->
     *     session.use {
     *         "a/b/c".intoKeyExpr().onSuccess { keyExpr ->
     *             val exampleQoS = QoS(
     *                  congestionControl = CongestionControl.DROP,
     *                  express = true,
     *                  priority = Priority.DATA_HIGH)
     *             val exampleAttachment = "exampleAttachment".into()
     *             session.put(
     *                  keyExpr,
     *                  message = "Example message",
     *                  qos = exampleQoS,
     *                  attachment = exampleAttachment).getOrThrow()
     *         }
     *         // ...
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the put operation.
     * @param message The [String] message to put.
     * @param qos The [QoS] configuration.
     * @param attachment Optional attachment.
     * @return A [Result] with the status of the put operation.
     */
    fun put(keyExpr: KeyExpr, message: String, qos: QoS = QoS.default(), attachment: ZBytes? = null) : Result<Unit> {
        val put = Put(keyExpr, Value(message), qos, attachment)
        return resolvePut(keyExpr, put)
    }

    /**
     * Perform a delete operation.
     *
     * Example:
     * ```kotlin
     * Session.open(config).onSuccess { session ->
     *     session.use {
     *         key.intoKeyExpr().onSuccess { keyExpr ->
     *             println("Deleting resources matching '$keyExpr'...")
     *             session.delete(keyExpr)
     *         }
     *     }
     * }
     * ```
     *
     * @param keyExpr The [KeyExpr] to be used for the delete operation.
     * @param qos The [QoS] configuration.
     * @param attachment Optional [ZBytes] attachment.
     * @return a [Result] with the status of the operation.
     */
    fun delete(keyExpr: KeyExpr, qos: QoS = QoS.default(), attachment: ZBytes? = null): Result<Unit> {
        val delete = Delete(keyExpr, qos, attachment)
        return resolveDelete(keyExpr, delete)
    }

    /** Returns if session is open or has been closed. */
    fun isOpen(): Boolean {
        return jniSession != null
    }

    private fun resolvePublisher(keyExpr: KeyExpr, qos: QoS): Result<Publisher> {
        return jniSession?.run {
            declarePublisher(keyExpr, qos).onSuccess { declarations.add(it) }
        } ?: Result.failure(sessionClosedException)
    }

    private fun <R> resolveSubscriber(
        keyExpr: KeyExpr,
        callback: Callback<Sample>,
        onClose: () -> Unit,
        receiver: R,
        reliability: Reliability
    ): Result<Subscriber<R>> {
        return jniSession?.run {
            declareSubscriber(keyExpr, callback, onClose, receiver, reliability).onSuccess { declarations.add(it) }
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
            declareQueryable(keyExpr, callback, onClose, receiver, complete).onSuccess { declarations.add(it) }
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
        value: Value?,
        attachment: ZBytes?,
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
                value?.payload,
                value?.encoding,
                attachment
            )
        } ?: Result.failure(sessionClosedException)
    }

    private fun resolvePut(keyExpr: KeyExpr, put: Put): Result<Unit> = runCatching {
        jniSession?.run { performPut(keyExpr, put) }
    }

    private fun resolveDelete(keyExpr: KeyExpr, delete: Delete): Result<Unit> = runCatching {
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
