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
import io.zenoh.sample.SampleKind
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
     * @param qos The [QoS] for the reply.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun reply(
        keyExpr: KeyExpr,
        payload: IntoZBytes,
        encoding: Encoding = Encoding.default(),
        qos: QoS = QoS.default(),
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        val sample = Sample(keyExpr, payload.into(), encoding, SampleKind.PUT, timestamp, qos, attachment?.into())
        return jniQuery?.let {
            val result = it.replySuccess(sample)
            jniQuery = null
            result
        } ?: Result.failure(ZError("Query is invalid"))
    }

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

    /**
     * Perform a delete reply operation to the remote [Query].
     *
     * A query can not be replied more than once. After the reply is performed, the query is considered
     * to be no more valid and further attempts to reply to it will fail.
     *
     * @param keyExpr Key expression to reply to. This parameter must not be necessarily the same
     * as the key expression from the Query, however it must intersect with the query key.
     * @param qos The [QoS] for the reply.
     * @param timestamp Optional timestamp for the reply.
     * @param attachment Optional attachment for the reply.
     */
    fun replyDel(
        keyExpr: KeyExpr,
        qos: QoS = QoS.default(),
        timestamp: TimeStamp? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        return jniQuery?.let {
            val result = it.replyDelete(keyExpr, timestamp, attachment, qos)
            jniQuery = null
            result
        } ?: Result.failure(ZError("Query is invalid"))
    }

    override fun close() {
        jniQuery?.apply {
            this.close()
            jniQuery = null
        }
    }
}
