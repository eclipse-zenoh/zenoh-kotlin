package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.jni.JNIZBytes.deserializeViaJNI
import kotlin.reflect.typeOf

/**
 * Deserialize the [ZBytes] instance into an element of type [T].
 *
 * Supported types:
 * - [Number]: Byte, Short, Int, Long, Float, Double
 * - [String]
 * - [ByteArray]
 * - Lists and Maps of the above-mentioned types.
 *
 * // TODO: update doc
 *
 * @see ZBytes
 * @return a [Result] with the deserialization.
 */
inline fun <reified T: Any> zDeserialize(zbytes: ZBytes): Result<T> = runCatching {
    deserializeViaJNI(zbytes, typeOf<T>()) as T
}
