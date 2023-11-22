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

import io.zenoh.Resolvable
import io.zenoh.ZenohType
import io.zenoh.selector.Selector
import io.zenoh.value.Value
import io.zenoh.exceptions.SessionException
import io.zenoh.exceptions.ZenohException
import io.zenoh.jni.JNIQuery
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.query.Reply

/**
 * Represents a Zenoh Query in Kotlin.
 *
 * A Query is generated within the context of a [Queryable], when receiving a [Query] request.
 *
 * @property keyExpr The key expression to which the query is associated.
 * @property selector The selector
 * @property value Optional value in case the received query was declared using "with query".
 * @property jniQuery Delegate object in charge of communicating with the underlying native code.
 * @constructor Instances of Query objects are only meant to be created through the JNI upon receiving
 * a query request. Therefore, the constructor is private.
 */
class Query internal constructor(
    val keyExpr: KeyExpr,
    val selector: Selector,
    val value: Value?,
    private var jniQuery: JNIQuery?
) : AutoCloseable, ZenohType {

    /** Shortcut to the [selector]'s parameters. */
    val parameters = selector.parameters

    /**
     * Reply to the specified key expression.
     *
     * @param keyExpr Key expression to reply to. This parameter must not be necessarily the same
     * as the key expression from the Query, however it must intersect with the query key.
     * @return a [Reply.Builder]
     */
    fun reply(keyExpr: KeyExpr) = Reply.Builder(this, keyExpr)

    override fun close() {
        jniQuery?.apply {
            this.close()
            jniQuery = null
        }
    }

    protected fun finalize() {
        close()
    }

    /**
     * Perform a reply operation to the remote [Query].
     *
     * @param reply The [Reply] to the Query.
     * @return A [Resolvable] that either performs the reply operation or throws an [Exception] if the query is invalid.
     */
    @Throws(ZenohException::class)
    internal fun reply(reply: Reply): Resolvable<Unit> = Resolvable {
        jniQuery?.apply {
            reply as Reply.Success // Since error replies are not yet supported, we assume a reply is a Success reply.
            val result = replySuccess(reply.sample)
            this.close()
            jniQuery = null
            return@Resolvable result
        }
        throw(SessionException("Query is invalid"))
    }
}
