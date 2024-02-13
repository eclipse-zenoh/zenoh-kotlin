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

package io.zenoh.jni

import io.zenoh.sample.Attachment

internal fun encodeAttachment(attachment: Attachment): ByteArray {
    return attachment.values.joinToString("&") { (key, value) ->
        "$key=${value.decodeToString()}"
    }.encodeToByteArray()
}

internal fun decodeAttachment(attachment: ByteArray): Attachment {
    val pairs = attachment.decodeToString().split("&").map { it.split("=").let { (k, v) -> k to v.toByteArray() } }
    return Attachment(pairs)
}
