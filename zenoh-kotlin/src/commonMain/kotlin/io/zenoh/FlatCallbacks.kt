//
// Copyright (c) 2026 ZettaScale Technology
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

import io.zenoh.bytes.Encoding
import io.zenoh.bytes.ZBytes
import io.zenoh.config.EntityGlobalId
import io.zenoh.config.WhatAmI
import io.zenoh.config.ZenohId
import io.zenoh.query.Query
import io.zenoh.query.Reply
import io.zenoh.query.ReplyError
import io.zenoh.sample.Sample
import io.zenoh.scouting.Hello

/**
 * Adapters from the generated JNI callback lambdas to a plain
 * `(SdkType) -> Unit`. A callback argument whose type has a canonical output
 * is decomposed natively — a `Sample`, `Query`, `Hello` or `Reply` arrives
 * as its leaves in ONE JNI crossing (no per-field accessor calls) and the
 * SDK object graph is built from them via [Sample.fromParts] /
 * [Query.fromParts] / the [Hello] constructor. A `Query` additionally delivers
 * its owned handle as the final leaf so the [Query] can reply. A `Reply` is a
 * sum type decomposed as a product: both arms' leaves are always in the
 * signature and the not-taken arm's are null — `isOk` discriminates.
 */

internal fun sampleCallbackOf(
    f: (Sample) -> Unit
): io.zenoh.jni.sample.SampleCallback =
    io.zenoh.jni.sample.SampleCallback { keStr, payloadH, encId, encSchema, kindInt, ntp64, express, prioInt, ccInt, attachH, reliabilityInt, sourceZid, sourceEid, sourceSn ->
        f(Sample.fromParts(keStr, payloadH, encId, encSchema, kindInt, ntp64, express, prioInt, ccInt, attachH, reliabilityInt, sourceZid, sourceEid, sourceSn))
    }

internal fun queryCallbackOf(
    f: (Query) -> Unit
): io.zenoh.jni.query.QueryCallback =
    io.zenoh.jni.query.QueryCallback { keStr, parameters, payloadH, encId, encSchema, attachH, acceptsReplies, zq ->
        // The decomposed leaves — including the key-expr string and
        // the owned `zq` query handle — are folded into the SDK [Query]. Unlike
        // the decomposed read-only types (Sample/Hello), the query OWNS `zq` and
        // is NOT closed here: it may be retained beyond this callback (e.g. put
        // on a channel by a queue handler) and replied to later. The native query
        // is dropped when it is replied to (see [Query.reply]) or when [Query] is
        // closed — that drop is what finalizes the querier's get.
        val query = try {
            Query.fromParts(keStr, parameters, payloadH, encId, encSchema, attachH, acceptsReplies, zq)
        } catch (t: Throwable) {
            // Defense in depth: the leaves carry remote (attacker-controlled)
            // data, and fromParts must be total on it — but if decomposition
            // ever throws, free the owned native leaves (payload/attachment
            // have no GC backstop) and finalize the query so the remote get
            // completes, instead of leaking them. The rethrown exception is
            // swallowed by the native callback bridge, by design.
            payloadH?.close()
            attachH?.close()
            zq.close()
            throw t
        }
        f(query)
    }

internal fun replyCallbackOf(
    f: (Reply) -> Unit
): io.zenoh.jni.query.ReplyCallback =
    io.zenoh.jni.query.ReplyCallback { zid, eid, isOk, keStr, payloadH, encId, encSchema, kindInt, ntp64, express, prioInt, ccInt, attachH, reliabilityInt, sourceZid, sourceEid, sourceSn, errPayloadH, errEncId, errEncSchema ->
        val replierId = zid?.let { EntityGlobalId(ZenohId(it.bytes), eid.toUInt()) }
        f(
            if (isOk) {
                Reply(
                    replierId,
                    Result.success(
                        Sample.fromParts(keStr!!, payloadH!!, encId!!, encSchema, kindInt!!, ntp64, express!!, prioInt!!, ccInt!!, attachH, reliabilityInt!!, sourceZid, sourceEid!!, sourceSn!!)
                    )
                )
            } else {
                Reply(
                    replierId,
                    Result.failure(
                        ReplyError(
                            ZBytes.fromHandle(errPayloadH!!),
                            errEncId?.let { Encoding(it, schema = errEncSchema) } ?: Encoding.default()
                        )
                    )
                )
            }
        )
    }

internal fun helloCallbackOf(
    f: (Hello) -> Unit
): io.zenoh.jni.scouting.HelloCallback =
    io.zenoh.jni.scouting.HelloCallback { whatamiInt, zid, locators ->
        f(Hello(WhatAmI.fromInt(whatamiInt), ZenohId(zid.bytes), locators))
    }
