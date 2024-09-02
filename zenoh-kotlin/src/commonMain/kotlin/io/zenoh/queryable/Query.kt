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

package io.zenoh.queryable

import io.zenoh.ZenohType
import io.zenoh.selector.Selector
import io.zenoh.exceptions.SessionException
import io.zenoh.jni.JNIQuery
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.prelude.QoS
import io.zenoh.prelude.SampleKind
import io.zenoh.protocol.ZBytes
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
 * @property jniQuery Delegate object in charge of communicating with the underlying native code.
 * @constructor Instances of Query objects are only meant to be created through the JNI upon receiving
 * a query request. Therefore, the constructor is private.
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
        payload: ZBytes,
        encoding: Encoding = Encoding.default(),
        qos: QoS = QoS.default(),
        timestamp: TimeStamp? = null,
        attachment: ZBytes? = null
    ): Result<Unit> {
        val sample = Sample(keyExpr, payload, encoding, SampleKind.PUT, timestamp, qos, attachment)
        return jniQuery?.let {
            val result = it.replySuccess(sample)
            jniQuery = null
            result
        } ?: Result.failure(SessionException("Query is invalid"))
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
    fun replyErr(error: ZBytes, encoding: Encoding = Encoding.default()): Result<Unit> {
        return jniQuery?.let {
            val result = it.replyError(error, encoding)
            jniQuery = null
            result
        } ?: Result.failure(SessionException("Query is invalid"))
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
        attachment: ZBytes? = null
    ): Result<Unit> {
        return jniQuery?.let {
            val result = it.replyDelete(keyExpr, timestamp, attachment, qos)
            jniQuery = null
            result
        } ?: Result.failure(SessionException("Query is invalid"))
    }

    override fun close() {
        jniQuery?.apply {
            this.close()
            jniQuery = null
        }
    }
}
