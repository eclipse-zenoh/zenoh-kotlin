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
import io.zenoh.jni.JNIQuery
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.qos.QoS
import io.zenoh.qos.ReplyQoS
import io.zenoh.bytes.IntoZBytes
import io.zenoh.bytes.ZBytes
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
    private var jniQuery: JNIQuery?
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
     * @param qos The [ReplyQoS] for the reply. Only [ReplyQoS.express] is meaningful for replies.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun reply(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: ReplyQoS = ReplyQoS(),
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        return jniQuery?.let {
            val result = it.replySuccess(keyExpr, payload.into(), encoding, timestamp, attachment?.into(), qos.express)
            jniQuery = null
            result
        } ?: Result.failure(ZError("Query is invalid"))
    }

    fun reply(
        keyExpr: KeyExpr,
        payload: String,
        encoding: Encoding = Encoding.default(),
        qos: ReplyQoS = ReplyQoS(),
        timestamp: TimeStamp? = null,
        attachment: String? = null
    ): Result<Unit> =
        reply(keyExpr, ZBytes.from(payload), encoding, qos, timestamp, attachment?.let { ZBytes.from(it) })

    @Deprecated(
        message = "Use the overload accepting ReplyQoS instead. The protocol uses request's priority and congestion control for replies, so only 'express' is meaningful.",
        replaceWith = ReplaceWith(
            "reply(keyExpr, payload, encoding, ReplyQoS(qos.express), timestamp, attachment)",
            "io.zenoh.qos.ReplyQoS"
        )
    )
    fun reply(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> = reply(keyExpr, payload, encoding, ReplyQoS(qos.express), timestamp, attachment)

    @Deprecated(
        message = "Use the overload accepting ReplyQoS instead. The protocol uses request's priority and congestion control for replies, so only 'express' is meaningful.",
        replaceWith = ReplaceWith(
            "reply(keyExpr, payload, encoding, ReplyQoS(qos.express), timestamp, attachment)",
            "io.zenoh.qos.ReplyQoS"
        )
    )
    fun reply(
        keyExpr: KeyExpr,
        payload: String,
        encoding: Encoding = Encoding.default(),
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: String? = null
    ): Result<Unit> = reply(keyExpr, ZBytes.from(payload), encoding, ReplyQoS(qos.express), timestamp, attachment?.let { ZBytes.from(it) })

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
        return jniQuery?.let {
            val result = it.replyError(error, encoding)
            jniQuery = null
            result
        } ?: Result.failure(ZError("Query is invalid"))
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
     * @param qos The [ReplyQoS] for the reply. Only [ReplyQoS.express] is meaningful for replies.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun replyDel(
        keyExpr: KeyExpr,
        qos: ReplyQoS = ReplyQoS(),
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        return jniQuery?.let {
            val result = it.replyDelete(keyExpr, timestamp, attachment, qos.express)
            jniQuery = null
            result
        } ?: Result.failure(ZError("Query is invalid"))
    }

    fun replyDel(
        keyExpr: KeyExpr,
        qos: ReplyQoS = ReplyQoS(),
        timestamp: TimeStamp? = null,
        attachment: String,
    ): Result<Unit> = replyDel(keyExpr, qos, timestamp, ZBytes.from(attachment))

    @Deprecated(
        message = "Use the overload accepting ReplyQoS instead. The protocol uses request's priority and congestion control for replies, so only 'express' is meaningful.",
        replaceWith = ReplaceWith(
            "replyDel(keyExpr, ReplyQoS(qos.express), timestamp, attachment)",
            "io.zenoh.qos.ReplyQoS"
        )
    )
    fun replyDel(
        keyExpr: KeyExpr,
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> = replyDel(keyExpr, ReplyQoS(qos.express), timestamp, attachment)

    @Deprecated(
        message = "Use the overload accepting ReplyQoS instead. The protocol uses request's priority and congestion control for replies, so only 'express' is meaningful.",
        replaceWith = ReplaceWith(
            "replyDel(keyExpr, ReplyQoS(qos.express), timestamp, attachment)",
            "io.zenoh.qos.ReplyQoS"
        )
    )
    fun replyDel(
        keyExpr: KeyExpr,
        qos: QoS,
        timestamp: TimeStamp? = null,
        attachment: String,
    ): Result<Unit> = replyDel(keyExpr, ReplyQoS(qos.express), timestamp, ZBytes.from(attachment))

    override fun close() {
        jniQuery?.apply {
            this.close()
            jniQuery = null
        }
    }
}
