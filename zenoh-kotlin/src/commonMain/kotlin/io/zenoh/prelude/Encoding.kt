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
 *
 */
class Encoding(val id: ID, val schema: String? = null) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Encoding

        if (id != other.id) return false
        return schema == other.schema
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + schema.hashCode()
        return result
    }

    /**
     * The ID of the encoding.
     *
     * @property id The id of the encoding.
     * @property encoding The encoding name.
     */
    enum class ID(val id: Int, val encoding: String) {
        ZENOH_BYTES(0, "zenoh/bytes"),
        ZENOH_INT(1, "zenoh/int"),
        ZENOH_UINT(2, "zenoh/uint"),
        ZENOH_FLOAT(3, "zenoh/float"),
        ZENOH_BOOL(4, "zenoh/bool"),
        ZENOH_STRING(5, "zenoh/string"),
        ZENOH_ERROR(6, "zenoh/error"),
        APPLICATION_OCTET_STREAM(7, "application/octet-stream"),
        TEXT_PLAIN(8, "text/plain"),
        APPLICATION_JSON(9, "application/json"),
        TEXT_JSON(10, "text/json"),
        APPLICATION_CDR(11, "application/cdr"),
        APPLICATION_CBOR(12, "application/cbor"),
        APPLICATION_YAML(13, "application/yaml"),
        TEXT_YAML(14, "text/yaml"),
        TEXT_JSON5(15, "text/json5"),
        APPLICATION_PYTHON_SERIALIZED_OBJECT(16, "application/python-serialized-object"),
        APPLICATION_PROTOBUF(17, "application/protobuf"),
        APPLICATION_JAVA_SERIALIZED_OBJECT(18, "application/java-serialized-object"),
        APPLICATION_OPENMETRICS_TEXT(19, "application/openmetrics-text"),
        IMAGE_PNG(20, "image/png"),
        IMAGE_JPEG(21, "image/jpeg"),
        IMAGE_GIF(22, "image/gif"),
        IMAGE_BMP(23, "image/bmp"),
        IMAGE_WEBP(24, "image/webp"),
        APPLICATION_XML(25, "application/xml"),
        APPLICATION_X_WWW_FORM_URLENCODED(26, "application/x-www-form-urlencoded"),
        TEXT_HTML(27, "text/html"),
        TEXT_XML(28, "text/xml"),
        TEXT_CSS(29, "text/css"),
        TEXT_JAVASCRIPT(30, "text/javascript"),
        TEXT_MARKDOWN(31, "text/markdown"),
        TEXT_CSV(32, "text/csv"),
        APPLICATION_SQL(33, "application/sql"),
        APPLICATION_COAP_PAYLOAD(34, "application/coap-payload"),
        APPLICATION_JSON_PATCH_JSON(35, "application/json-patch+json"),
        APPLICATION_JSON_SEQ(36, "application/json-seq"),
        APPLICATION_JSONPATH(37, "application/jsonpath"),
        APPLICATION_JWT(38, "application/jwt"),
        APPLICATION_MP4(39, "application/mp4"),
        APPLICATION_SOAP_XML(40, "application/soap+xml"),
        APPLICATION_YANG(41, "application/yang"),
        AUDIO_AAC(42, "audio/aac"),
        AUDIO_FLAC(43, "audio/flac"),
        AUDIO_MP4(44, "audio/mp4"),
        AUDIO_OGG(45, "audio/ogg"),
        AUDIO_VORBIS(46, "audio/vorbis"),
        VIDEO_H261(47, "video/h261"),
        VIDEO_H263(48, "video/h263"),
        VIDEO_H264(49, "video/h264"),
        VIDEO_H265(50, "video/h265"),
        VIDEO_H266(51, "video/h266"),
        VIDEO_MP4(52, "video/mp4"),
        VIDEO_OGG(53, "video/ogg"),
        VIDEO_RAW(54, "video/raw"),
        VIDEO_VP8(55, "video/vp8"),
        VIDEO_VP9(56, "video/vp9");

        companion object {
            private val idToEnum = entries.associateBy(ID::id)
            fun fromId(id: Int): ID? = idToEnum[id]
        }
    }
}


