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

package io.zenoh.time

import io.zenoh.config.ZenohId
import io.zenoh.jni.time.Timestamp as JniTimestamp

/**
 * A Zenoh timestamp: an NTP64 instant together with the identifier of the node
 * whose clock produced it.
 *
 * Both halves are significant. Zenoh orders and de-duplicates on the whole
 * `(ntp64, id)` pair, so a timestamp is only meaningful when its [id] is the
 * real identifier of the originating node — obtain it from the session that
 * will publish the value, via `session.info().zid()`.
 *
 * @property ntp64 The NTP64 instant. This is an *unsigned* 64-bit value whose
 *   high bit marks the current era; [getNtp64] returns the same 64 bits in the
 *   form `org.apache.commons.net.ntp.TimeStamp(long)` expects.
 * @property id Identifier of the node whose clock produced [ntp64].
 */
data class Timestamp(
    // Rename default getter which is not accessible on Java due to unsigned type
    @get:JvmName("getNtp64ULong") val ntp64: ULong,
    val id: ZenohId,
) {

    @Suppress("unused")
    // Manually defined getter for Java: the raw 64 bits, not a magnitude, so
    // this reinterprets rather than clamping.
    fun getNtp64(): Long = ntp64.toLong()

    internal fun toJni(): JniTimestamp = JniTimestamp(ntp64, id.bytes)

    companion object {
        /**
         * Build a timestamp from the raw 64 NTP bits — the constructor Java
         * callers want, since the primary one takes an unsigned type Java
         * cannot express. These are the same bits
         * `org.apache.commons.net.ntp.TimeStamp(long)` takes, so
         * `TimeStamp.getCurrentTime().ntpValue()` feeds straight in.
         */
        @JvmStatic
        fun ofNtp64(ntp64: Long, id: ZenohId): Timestamp = Timestamp(ntp64.toULong(), id)

        /** The native `(ntp64, id)` pair as delivered by a decomposed value. */
        internal fun fromJni(t: JniTimestamp): Timestamp = Timestamp(t.ntp64, ZenohId(t.id))
    }
}
