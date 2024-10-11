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

package io.zenoh.bytes

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
class Encoding private constructor(
    internal val id: Int,
    internal val schema: String? = null,
    private val description: String? = null
) {
    internal constructor(id: Int, schema: String? = null) : this(id, schema, null)

    companion object {
        /**
         * Just some bytes.
         *
         * Constant alias for string: `"zenoh/bytes"`.
         *
         * Usually used for types: `ByteArray`, `List<Byte>`.
         */
        val ZENOH_BYTES = Encoding(0, description = "zenoh/bytes")

        /**
         * A UTF-8 string.
         *
         * Constant alias for string: `"zenoh/string"`.
         *
         * Usually used for type: `String`.
         */
        val ZENOH_STRING = Encoding(1, description = "zenoh/string")

        /**
         * Zenoh serialized data.
         *
         * Constant alias for string: `"zenoh/serialized"`.
         */
        val ZENOH_SERIALIZED = Encoding(2, description = "zenoh/serialized")

        /**
         * An application-specific stream of bytes.
         *
         * Constant alias for string: `"application/octet-stream"`.
         */
        val APPLICATION_OCTET_STREAM = Encoding(3, description = "application/octet-stream")

        /**
         * A textual file.
         *
         * Constant alias for string: `"text/plain"`.
         */
        val TEXT_PLAIN = Encoding(4, description = "text/plain")

        /**
         * JSON data intended to be consumed by an application.
         *
         * Constant alias for string: `"application/json"`.
         */
        val APPLICATION_JSON = Encoding(5, description = "application/json")

        /**
         * JSON data intended to be human readable.
         *
         * Constant alias for string: `"text/json"`.
         */
        val TEXT_JSON = Encoding(6, description = "text/json")

        /**
         * A Common Data Representation (CDR)-encoded data.
         *
         * Constant alias for string: `"application/cdr"`.
         */
        val APPLICATION_CDR = Encoding(7, description = "application/cdr")

        /**
         * A Concise Binary Object Representation (CBOR)-encoded data.
         *
         * Constant alias for string: `"application/cbor"`.
         */
        val APPLICATION_CBOR = Encoding(8, description = "application/cbor")

        /**
         * YAML data intended to be consumed by an application.
         *
         * Constant alias for string: `"application/yaml"`.
         */
        val APPLICATION_YAML = Encoding(9, description = "application/yaml")

        /**
         * YAML data intended to be human readable.
         *
         * Constant alias for string: `"text/yaml"`.
         */
        val TEXT_YAML = Encoding(10, description = "text/yaml")

        /**
         * JSON5 encoded data that are human readable.
         *
         * Constant alias for string: `"text/json5"`.
         */
        val TEXT_JSON5 = Encoding(11, description = "text/json5")

        /**
         * A Python object serialized using [pickle](https://docs.python.org/3/library/pickle.html).
         *
         * Constant alias for string: `"application/python-serialized-object"`.
         */
        val APPLICATION_PYTHON_SERIALIZED_OBJECT =
            Encoding(12, description = "application/python-serialized-object")

        /**
         * An application-specific protobuf-encoded data.
         *
         * Constant alias for string: `"application/protobuf"`.
         */
        val APPLICATION_PROTOBUF = Encoding(13, description = "application/protobuf")

        /**
         * A Java serialized object.
         *
         * Constant alias for string: `"application/java-serialized-object"`.
         */
        val APPLICATION_JAVA_SERIALIZED_OBJECT =
            Encoding(14, description = "application/java-serialized-object")

        /**
         * OpenMetrics data, commonly used by [Prometheus](https://prometheus.io/).
         *
         * Constant alias for string: `"application/openmetrics-text"`.
         */
        val APPLICATION_OPENMETRICS_TEXT =
            Encoding(15, description = "application/openmetrics-text")

        /**
         * A Portable Network Graphics (PNG) image.
         *
         * Constant alias for string: `"image/png"`.
         */
        val IMAGE_PNG = Encoding(16, description = "image/png")

        /**
         * A Joint Photographic Experts Group (JPEG) image.
         *
         * Constant alias for string: `"image/jpeg"`.
         */
        val IMAGE_JPEG = Encoding(17, description = "image/jpeg")

        /**
         * A Graphics Interchange Format (GIF) image.
         *
         * Constant alias for string: `"image/gif"`.
         */
        val IMAGE_GIF = Encoding(18, description = "image/gif")

        /**
         * A BitMap (BMP) image.
         *
         * Constant alias for string: `"image/bmp"`.
         */
        val IMAGE_BMP = Encoding(19, description = "image/bmp")

        /**
         * A WebP image.
         *
         * Constant alias for string: `"image/webp"`.
         */
        val IMAGE_WEBP = Encoding(20, description = "image/webp")

        /**
         * An XML file intended to be consumed by an application.
         *
         * Constant alias for string: `"application/xml"`.
         */
        val APPLICATION_XML = Encoding(21, description = "application/xml")

        /**
         * A list of tuples, each consisting of a name and a value.
         *
         * Constant alias for string: `"application/x-www-form-urlencoded"`.
         */
        val APPLICATION_X_WWW_FORM_URLENCODED =
            Encoding(22, description = "application/x-www-form-urlencoded")

        /**
         * An HTML file.
         *
         * Constant alias for string: `"text/html"`.
         */
        val TEXT_HTML = Encoding(23, description = "text/html")

        /**
         * An XML file that is human readable.
         *
         * Constant alias for string: `"text/xml"`.
         */
        val TEXT_XML = Encoding(24, description = "text/xml")

        /**
         * A CSS file.
         *
         * Constant alias for string: `"text/css"`.
         */
        val TEXT_CSS = Encoding(25, description = "text/css")

        /**
         * A JavaScript file.
         *
         * Constant alias for string: `"text/javascript"`.
         */
        val TEXT_JAVASCRIPT = Encoding(26, description = "text/javascript")

        /**
         * A Markdown file.
         *
         * Constant alias for string: `"text/markdown"`.
         */
        val TEXT_MARKDOWN = Encoding(27, description = "text/markdown")

        /**
         * A CSV file.
         *
         * Constant alias for string: `"text/csv"`.
         */
        val TEXT_CSV = Encoding(28, description = "text/csv")

        /**
         * An application-specific SQL query.
         *
         * Constant alias for string: `"application/sql"`.
         */
        val APPLICATION_SQL = Encoding(29, description = "application/sql")

        /**
         * Constrained Application Protocol (CoAP) data intended for CoAP-to-HTTP and HTTP-to-CoAP proxies.
         *
         * Constant alias for string: `"application/coap-payload"`.
         */
        val APPLICATION_COAP_PAYLOAD = Encoding(30, description = "application/coap-payload")

        /**
         * Defines a JSON document structure for expressing a sequence of operations to apply to a JSON document.
         *
         * Constant alias for string: `"application/json-patch+json"`.
         */
        val APPLICATION_JSON_PATCH_JSON = Encoding(31, description = "application/json-patch+json")

        /**
         * A JSON text sequence consists of any number of JSON texts, all encoded in UTF-8.
         *
         * Constant alias for string: `"application/json-seq"`.
         */
        val APPLICATION_JSON_SEQ = Encoding(32, description = "application/json-seq")

        /**
         * A JSONPath defines a string syntax for selecting and extracting JSON values from within a given JSON value.
         *
         * Constant alias for string: `"application/jsonpath"`.
         */
        val APPLICATION_JSONPATH = Encoding(33, description = "application/jsonpath")

        /**
         * A JSON Web Token (JWT).
         *
         * Constant alias for string: `"application/jwt"`.
         */
        val APPLICATION_JWT = Encoding(34, description = "application/jwt")

        /**
         * An application-specific MPEG-4 encoded data, either audio or video.
         *
         * Constant alias for string: `"application/mp4"`.
         */
        val APPLICATION_MP4 = Encoding(35, description = "application/mp4")

        /**
         * A SOAP 1.2 message serialized as XML 1.0.
         *
         * Constant alias for string: `"application/soap+xml"`.
         */
        val APPLICATION_SOAP_XML = Encoding(36, description = "application/soap+xml")

        /**
         * A YANG-encoded data commonly used by the Network Configuration Protocol (NETCONF).
         *
         * Constant alias for string: `"application/yang"`.
         */
        val APPLICATION_YANG = Encoding(37, description = "application/yang")

        /**
         * A MPEG-4 Advanced Audio Coding (AAC) media.
         *
         * Constant alias for string: `"audio/aac"`.
         */
        val AUDIO_AAC = Encoding(38, description = "audio/aac")

        /**
         * A Free Lossless Audio Codec (FLAC) media.
         *
         * Constant alias for string: `"audio/flac"`.
         */
        val AUDIO_FLAC = Encoding(39, description = "audio/flac")

        /**
         * An audio codec defined in MPEG-1, MPEG-2, MPEG-4, or registered at the MP4 registration authority.
         *
         * Constant alias for string: `"audio/mp4"`.
         */
        val AUDIO_MP4 = Encoding(40, description = "audio/mp4")

        /**
         * An Ogg-encapsulated audio stream.
         *
         * Constant alias for string: `"audio/ogg"`.
         */
        val AUDIO_OGG = Encoding(41, description = "audio/ogg")

        /**
         * A Vorbis-encoded audio stream.
         *
         * Constant alias for string: `"audio/vorbis"`.
         */
        val AUDIO_VORBIS = Encoding(42, description = "audio/vorbis")

        /**
         * A h261-encoded video stream.
         *
         * Constant alias for string: `"video/h261"`.
         */
        val VIDEO_H261 = Encoding(43, description = "video/h261")

        /**
         * A h263-encoded video stream.
         *
         * Constant alias for string: `"video/h263"`.
         */
        val VIDEO_H263 = Encoding(44, description = "video/h263")

        /**
         * A h264-encoded video stream.
         *
         * Constant alias for string: `"video/h264"`.
         */
        val VIDEO_H264 = Encoding(45, description = "video/h264")

        /**
         * A h265-encoded video stream.
         *
         * Constant alias for string: `"video/h265"`.
         */
        val VIDEO_H265 = Encoding(46, description = "video/h265")

        /**
         * A h266-encoded video stream.
         *
         * Constant alias for string: `"video/h266"`.
         */
        val VIDEO_H266 = Encoding(47, description = "video/h266")

        /**
         * A video codec defined in MPEG-1, MPEG-2, MPEG-4, or registered at the MP4 registration authority.
         *
         * Constant alias for string: `"video/mp4"`.
         */
        val VIDEO_MP4 = Encoding(48, description = "video/mp4")

        /**
         * An Ogg-encapsulated video stream.
         *
         * Constant alias for string: `"video/ogg"`.
         */
        val VIDEO_OGG = Encoding(49, description = "video/ogg")

        /**
         * An uncompressed, studio-quality video stream.
         *
         * Constant alias for string: `"video/raw"`.
         */
        val VIDEO_RAW = Encoding(50, description = "video/raw")

        /**
         * A VP8-encoded video stream.
         *
         * Constant alias for string: `"video/vp8"`.
         */
        val VIDEO_VP8 = Encoding(51, description = "video/vp8")

        /**
         * A VP9-encoded video stream.
         *
         * Constant alias for string: `"video/vp9"`.
         */
        val VIDEO_VP9 = Encoding(52, description = "video/vp9")

        /**
         * The default [Encoding] is [ZENOH_BYTES].
         */
        fun default() = ZENOH_BYTES
    }

    /**
     * Set a schema to this encoding. Zenoh does not define what a schema is and its semantics is left to the implementer.
     * E.g. a common schema for `text/plain` encoding is `utf-8`.
     */
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
