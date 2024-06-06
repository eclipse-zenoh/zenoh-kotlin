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
import io.zenoh.jni.JNIQuery
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.query.Reply
import io.zenoh.sample.Attachment

/**
 * Represents a Zenoh Query in Kotlin.
 *
 * A Query is generated within the context of a [Queryable], when receiving a [Query] request.
 *
 * @property keyExpr The key expression to which the query is associated.
 * @property selector The selector
 * @property value Optional value in case the received query was declared using "with query".
 * @property attachment Optional attachment.
 * @property jniQuery Delegate object in charge of communicating with the underlying native code.
 * @constructor Instances of Query objects are only meant to be created through the JNI upon receiving
 * a query request. Therefore, the constructor is private.
 */
class Query internal constructor(
    val keyExpr: KeyExpr,
    val selector: Selector,
    val value: Value?,
    val attachment: Attachment?,
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
     * A query can not be replied more than once. After the reply is performed, the query is considered
     * to be no more valid and further attempts to reply to it will fail.
     *
     * @param reply The [Reply] to the Query.
     * @return A [Resolvable] that returns a [Result] with the status of the reply operation.
     */
    internal fun reply(reply: Reply): Resolvable<Unit> = Resolvable {
        jniQuery?.apply {
            val result: Result<Unit> = when (reply) {
                is Reply.Success -> {
                    replySuccess(reply.sample)
                }

                is Reply.Error -> {
                    replyError(reply.error)
                }
            }
            jniQuery = null
            return@Resolvable result
        }
        return@Resolvable Result.failure(SessionException("Query is invalid"))
    }
}
