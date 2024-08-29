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
 * Default encoding values used by Zenoh.
 *
 * An encoding has a similar role to Content-type in HTTP: it indicates, when present, how data should be interpreted by the application.
 *
 * Please note the Zenoh protocol does not impose any encoding value, nor it operates on it.
 * It can be seen as some optional metadata that is carried over by Zenoh in such a way the application may perform different operations depending on the encoding value.
 *
 * A set of associated constants are provided to cover the most common encodings for user convenience.
 * This is particularly useful in helping Zenoh to perform additional network optimizations.
 */
class Encoding private constructor(val id: Int, val schema: String? = null, private val description: String? = null) {

    constructor(id: Int, schema: String? = null) : this(id, schema, null)

    companion object {
        val ZENOH_BYTES = Encoding(0, description = "zenoh/bytes")
        val ZENOH_INT = Encoding(1, description = "zenoh/int")
        val ZENOH_UINT = Encoding(2, description = "zenoh/uint")
        val ZENOH_FLOAT = Encoding(3, description = "zenoh/float")
        val ZENOH_BOOL = Encoding(4, description = "zenoh/bool")
        val ZENOH_STRING = Encoding(5, description = "zenoh/string")
        val ZENOH_ERROR = Encoding(6, description = "zenoh/error")
        val APPLICATION_OCTET_STREAM = Encoding(7, description = "application/octet-stream")
        val TEXT_PLAIN = Encoding(8, description = "text/plain")
        val APPLICATION_JSON = Encoding(9, description = "application/json")
        val TEXT_JSON = Encoding(10, description = "text/json")
        val APPLICATION_CDR = Encoding(11, description = "application/cdr")
        val APPLICATION_CBOR = Encoding(12, description = "application/cbor")
        val APPLICATION_YAML = Encoding(13, description = "application/yaml")
        val TEXT_YAML = Encoding(14, description = "text/yaml")
        val TEXT_JSON5 = Encoding(15, description = "text/json5")
        val APPLICATION_PYTHON_SERIALIZED_OBJECT = Encoding(16, description = "application/python-serialized-object")
        val APPLICATION_PROTOBUF = Encoding(17, description = "application/protobuf")
        val APPLICATION_JAVA_SERIALIZED_OBJECT = Encoding(18, description = "application/java-serialized-object")
        val APPLICATION_OPENMETRICS_TEXT = Encoding(19, description = "application/openmetrics-text")
        val IMAGE_PNG = Encoding(20, description = "image/png")
        val IMAGE_JPEG = Encoding(21, description = "image/jpeg")
        val IMAGE_GIF = Encoding(22, description = "image/gif")
        val IMAGE_BMP = Encoding(23, description = "image/bmp")
        val IMAGE_WEBP = Encoding(24, description = "image/webp")
        val APPLICATION_XML = Encoding(25, description = "application/xml")
        val APPLICATION_X_WWW_FORM_URLENCODED = Encoding(26, description = "application/x-www-form-urlencoded")
        val TEXT_HTML = Encoding(27, description = "text/html")
        val TEXT_XML = Encoding(28, description = "text/xml")
        val TEXT_CSS = Encoding(29, description = "text/css")
        val TEXT_JAVASCRIPT = Encoding(30, description = "text/javascript")
        val TEXT_MARKDOWN = Encoding(31, description = "text/markdown")
        val TEXT_CSV = Encoding(32, description = "text/csv")
        val APPLICATION_SQL = Encoding(33, description = "application/sql")
        val APPLICATION_COAP_PAYLOAD = Encoding(34, description = "application/coap-payload")
        val APPLICATION_JSON_PATCH_JSON = Encoding(35, description = "application/json-patch+json")
        val APPLICATION_JSON_SEQ = Encoding(36, description = "application/json-seq")
        val APPLICATION_JSONPATH = Encoding(37, description = "application/jsonpath")
        val APPLICATION_JWT = Encoding(38, description = "application/jwt")
        val APPLICATION_MP4 = Encoding(39, description = "application/mp4")
        val APPLICATION_SOAP_XML = Encoding(40, description = "application/soap+xml")
        val APPLICATION_YANG = Encoding(41, description = "application/yang")
        val AUDIO_AAC = Encoding(42, description = "audio/aac")
        val AUDIO_FLAC = Encoding(43, description = "audio/flac")
        val AUDIO_MP4 = Encoding(44, description = "audio/mp4")
        val AUDIO_OGG = Encoding(45, description = "audio/ogg")
        val AUDIO_VORBIS = Encoding(46, description = "audio/vorbis")
        val VIDEO_H261 = Encoding(47, description = "video/h261")
        val VIDEO_H263 = Encoding(48, description = "video/h263")
        val VIDEO_H264 = Encoding(49, description = "video/h264")
        val VIDEO_H265 = Encoding(50, description = "video/h265")
        val VIDEO_H266 = Encoding(51, description = "video/h266")
        val VIDEO_MP4 = Encoding(52, description = "video/mp4")
        val VIDEO_OGG = Encoding(53, description = "video/ogg")
        val VIDEO_RAW = Encoding(54, description = "video/raw")
        val VIDEO_VP8 = Encoding(55, description = "video/vp8")
        val VIDEO_VP9 = Encoding(56, description = "video/vp9")
        internal fun default() = ZENOH_BYTES
    }

    fun withSchema(schema: String): Encoding {
        return Encoding(this.id, schema, this.description)
    }

    override fun toString(): String {
        val base = description ?: "unknown(${this.id})"
        val schemaInfo = schema?.let { ";$it" } ?: ""
        return "$base$schemaInfo"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Encoding

        return id == other.id && schema == other.schema
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
