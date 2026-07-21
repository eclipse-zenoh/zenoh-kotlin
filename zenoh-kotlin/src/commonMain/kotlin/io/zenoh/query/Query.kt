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

import io.zenoh.ZenohType
import io.zenoh.exceptions.ZError
import io.zenoh.exceptions.zCallUnit
import io.zenoh.jni.query.Query as JniQuery
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.jniHandle
import io.zenoh.keyexpr.jniSel
import io.zenoh.keyexpr.jniStr
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.jniHandle
import io.zenoh.bytes.jniId
import io.zenoh.bytes.jniSchema
import io.zenoh.bytes.jniSel
import io.zenoh.qos.QoS
import io.zenoh.qos.ReplyQoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
import io.zenoh.sample.Sample
import org.apache.commons.net.ntp.TimeStamp

/**
 * Represents a Zenoh Query in Kotlin.
 *
 * A Query is generated within the context of a [Queryable], when receiving a [Query] request.
 *
 * @property keyExpr The key expression to which the query is associated.
 * @property selector The selector
 * @property payload Optional payload in case the received query was declared using "with query".
 * @property encoding Encoding of the [payload].
 * @property attachment Optional attachment.
 */
class Query internal constructor(
    val keyExpr: KeyExpr,
    val selector: Selector,
    val payload: ZBytes?,
    val encoding: Encoding?,
    val attachment: ZBytes?,
    private var jniQuery: JniQuery?,
    private val acceptRepliesValue: ReplyKeyExpr = ReplyKeyExpr.MATCHING_QUERY
) : AutoCloseable, ZenohType {

    /** Shortcut to the [selector]'s parameters. */
    val parameters = selector.parameters

    /**
     * Reply success to the remote [Query].
     *
     * A query can not be replied more than once. After the reply is performed, the query is considered
     * to be no more valid and further attempts to reply to it will fail.
     *
     * @param keyExpr Key expression to reply to. This parameter must not be necessarily the same
     * as the key expression from the Query, however it must intersect with the query key.
     * @param payload The payload with the reply information.
     * @param encoding Encoding of the payload.
     * @param qos The [ReplyQoS] for the reply.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun reply(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: ReplyQoS = ReplyQoS.default,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        val q = jniQuery ?: return Result.failure(ZError("Query is invalid"))
        val result = zCallUnit { onBindingError, onError ->
            q.replySuccess(
                keyExpr.jniSel, keyExpr.jniStr, keyExpr.jniHandle,
                payload.into().bytes,
                encoding.jniSel, encoding.jniId, encoding.jniSchema, encoding.jniHandle,
                timestamp?.ntpValue(),
                attachment?.into()?.bytes,
                qos.express,
                onBindingError, onError
            )
        }
        // Single-reply model: dropping the native query finalizes the reply
        // stream so the querier's get completes. Safe whether the query came
        // straight from the callback or was carried across a channel.
        q.close()
        jniQuery = null
        return result
    }

    fun reply(
        keyExpr: KeyExpr,
        payload: String,
        encoding: Encoding = Encoding.default(),
        qos: ReplyQoS = ReplyQoS.default,
        timestamp: TimeStamp? = null,
        attachment: String? = null
    ): Result<Unit> =
        reply(keyExpr, ZBytes.from(payload), encoding, qos, timestamp, attachment?.let { ZBytes.from(it) })

    @Deprecated(
        message = "Use reply with ReplyQoS instead of QoS. Priority and congestion control are not applicable to replies.",
        replaceWith = ReplaceWith("reply(keyExpr, payload, encoding, ReplyQoS(express = qos.express), timestamp, attachment)", "io.zenoh.qos.ReplyQoS")
    )
    fun reply(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> = reply(keyExpr, payload, encoding, ReplyQoS(express = qos.express), timestamp, attachment)

    @Deprecated(
        message = "Use reply with ReplyQoS instead of QoS. Priority and congestion control are not applicable to replies.",
        replaceWith = ReplaceWith("reply(keyExpr, payload, encoding, ReplyQoS(express = qos.express), timestamp, attachment)", "io.zenoh.qos.ReplyQoS")
    )
    fun reply(
        keyExpr: KeyExpr,
        payload: String,
        encoding: Encoding = Encoding.default(),
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: String? = null
    ): Result<Unit> =
        reply(keyExpr, ZBytes.from(payload), encoding, ReplyQoS(express = qos.express), timestamp, attachment?.let { ZBytes.from(it) })

    /**
     * Reply error to the remote [Query].
     *
     * A query can not be replied more than once. After the reply is performed, the query is considered
     * to be no more valid and further attempts to reply to it will fail.
     *
     * @param error The error information.
     * @param encoding The encoding of the [error].
     */
    fun replyErr(error: IntoZBytes, encoding: Encoding = Encoding.default()): Result<Unit> {
        val q = jniQuery ?: return Result.failure(ZError("Query is invalid"))
        val result = zCallUnit { onBindingError, onError ->
            q.replyError(
                error.into().bytes,
                encoding.jniSel, encoding.jniId, encoding.jniSchema, encoding.jniHandle,
                onBindingError, onError
            )
        }
        q.close()
        jniQuery = null
        return result
    }


    fun replyErr(error: String, encoding: Encoding = Encoding.default()): Result<Unit> =
        replyErr(ZBytes.from(error), encoding)

    /**
     * Perform a delete reply operation to the remote [Query].
     *
     * A query can not be replied more than once. After the reply is performed, the query is considered
     * to be no more valid and further attempts to reply to it will fail.
     *
     * @param keyExpr Key expression to reply to. This parameter must not be necessarily the same
     * as the key expression from the Query, however it must intersect with the query key.
     * @param qos The [ReplyQoS] for the reply.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun replyDel(
        keyExpr: KeyExpr,
        qos: ReplyQoS = ReplyQoS.default,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        val q = jniQuery ?: return Result.failure(ZError("Query is invalid"))
        val result = zCallUnit { onBindingError, onError ->
            q.replyDelete(
                keyExpr.jniSel, keyExpr.jniStr, keyExpr.jniHandle,
                timestamp?.ntpValue(),
                attachment?.into()?.bytes,
                qos.express,
                onBindingError, onError
            )
        }
        q.close()
        jniQuery = null
        return result
    }

    fun replyDel(
        keyExpr: KeyExpr,
        qos: ReplyQoS = ReplyQoS.default,
        timestamp: TimeStamp? = null,
        attachment: String,
    ): Result<Unit> = replyDel(keyExpr, qos, timestamp, ZBytes.from(attachment))

    /**
     * Returns the [ReplyKeyExpr] accepted by this query.
     */
    fun acceptsReplies(): ReplyKeyExpr = acceptRepliesValue
    @Deprecated(
        message = "Use replyDel with ReplyQoS instead of QoS. Priority and congestion control are not applicable to replies.",
        replaceWith = ReplaceWith("replyDel(keyExpr, ReplyQoS(express = qos.express), timestamp, attachment)", "io.zenoh.qos.ReplyQoS")
    )
    fun replyDel(
        keyExpr: KeyExpr,
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> = replyDel(keyExpr, ReplyQoS(express = qos.express), timestamp, attachment)

    @Deprecated(
        message = "Use replyDel with ReplyQoS instead of QoS. Priority and congestion control are not applicable to replies.",
        replaceWith = ReplaceWith("replyDel(keyExpr, ReplyQoS(express = qos.express), timestamp, attachment)", "io.zenoh.qos.ReplyQoS")
    )
    fun replyDel(
        keyExpr: KeyExpr,
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: String,
    ): Result<Unit> = replyDel(keyExpr, ReplyQoS(express = qos.express), timestamp, ZBytes.from(attachment))

    override fun close() {
        jniQuery?.apply {
            this.close()
            jniQuery = null
        }
    }

    companion object {
        /**
         * Builds a [Query] from the decomposed leaves delivered by the
         * generated queryable callback in one JNI crossing, plus the owned
         * native query handle `zq`, **retained** because the reply methods
         * consume it (replying keeps working after the callback returns).
         */
        internal fun fromParts(
            keStr: String,
            parameters: String,
            payloadH: io.zenoh.jni.bytes.ZBytes?,
            encId: Int?,
            encSchema: String?,
            attachH: io.zenoh.jni.bytes.ZBytes?,
            acceptsRepliesInt: Int,
            zq: JniQuery,
        ): Query {
            val ke = KeyExpr(keStr)
            // The parameters string is ATTACKER-CONTROLLED (the Rust layer
            // forwards any selector parameters untouched) — the shared
            // string-backed Parameters accepts any input, never throwing.
            val selector = if (parameters.isEmpty()) Selector(ke)
                else Selector(ke, Parameters.from(parameters).getOrThrow())
            return Query(
                ke,
                selector,
                payloadH?.let { ZBytes.fromHandle(it) },
                encId?.let { Encoding(it, schema = encSchema) },
                attachH?.let { ZBytes.fromHandle(it) },
                zq,
                ReplyKeyExpr.fromInt(acceptsRepliesInt)
            )
        }
    }
}
