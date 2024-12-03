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

import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes
import io.zenoh.exceptions.ZError
import io.zenoh.handlers.Callback
import io.zenoh.handlers.ChannelHandler
import io.zenoh.handlers.Handler
import io.zenoh.jni.JNIQuerier
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.qos.QoS
import io.zenoh.session.SessionDeclaration
import kotlinx.coroutines.channels.Channel

class Querier internal constructor(val keyExpr: KeyExpr, val qos: QoS, private var jniQuerier: JNIQuerier?) :
    SessionDeclaration, AutoCloseable {

    fun get(
        channel: Channel<Reply>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<Channel<Reply>> {
        val handler = ChannelHandler(channel)
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            handler::handle,
            handler::onClose,
            handler.receiver(),
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

    fun get(
        callback: Callback<Reply>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<Unit> {
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            callback,
            {},
            Unit,
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

    fun <R> get(
        handler: Handler<Reply, R>,
        parameters: Parameters? = null,
        payload: IntoZBytes? = null,
        encoding: Encoding? = null,
        attachment: IntoZBytes? = null
    ): Result<R> {
        return jniQuerier?.performGet(
            keyExpr,
            parameters,
            handler::handle,
            handler::onClose,
            handler.receiver(),
            attachment,
            payload,
            encoding
        ) ?: throw ZError("Querier is not valid.")
    }

    fun congestionControl() = qos.congestionControl

    fun priority() = qos.priority

    override fun undeclare() {
        jniQuerier?.close()
        jniQuerier = null
    }

    override fun close() {
        undeclare()
    }

    // matching status
    // matching listener
    // accepts replies
}
