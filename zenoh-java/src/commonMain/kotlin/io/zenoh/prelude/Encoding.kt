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

package io.zenoh.prelude

/**
 * The encoding of a [io.zenoh.value.Value].
 *
 * A zenoh encoding is an HTTP Mime type and a string suffix.
 *
 * **Suffixes are not yet supported by zenoh-jni and are currently ignored.**
 *
 * @property knownEncoding A [KnownEncoding].
 * @property suffix Suffix of the encoding. This parameter is not yet supported by Zenoh-JNI and is currently ignored.
 */
class Encoding(val knownEncoding: KnownEncoding, val suffix: String = "") {

    constructor(knownEncoding: KnownEncoding) : this(knownEncoding, "")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Encoding

        if (knownEncoding != other.knownEncoding) return false
        return suffix == other.suffix
    }

    override fun hashCode(): Int {
        var result = knownEncoding.hashCode()
        result = 31 * result + suffix.hashCode()
        return result
    }
}

/**
 * Known encoding. An HTTP Mime type.
 */
enum class KnownEncoding {
    EMPTY,
    APP_OCTET_STREAM,
    APP_CUSTOM,
    TEXT_PLAIN,
    APP_PROPERTIES,
    APP_JSON,
    APP_SQL,
    APP_INTEGER,
    APP_FLOAT,
    APP_XML,
    APP_XHTML_XML,
    APP_X_WWW_FORM_URLENCODED,
    TEXT_JSON,
    TEXT_HTML,
    TEXT_XML,
    TEXT_CSS,
    TEXT_CSV,
    TEXT_JAVASCRIPT,
    IMAGE_JPEG,
    IMAGE_PNG,
    IMAGE_GIF;

    companion object {
        fun fromInt(value: Int) = entries.first { it.ordinal == value }

        fun default() = EMPTY
    }
}
