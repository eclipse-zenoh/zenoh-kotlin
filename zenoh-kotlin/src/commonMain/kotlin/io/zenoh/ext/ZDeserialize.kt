package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.jni.JNIZBytes.deserializeViaJNI
import kotlin.reflect.typeOf

/**
 * Deserialize the [ZBytes] instance into an element of type [T].
 *
 * @see ZBytes
 * @return a [Result] with the deserialization.
 */
inline fun <reified T: Any> zDeserialize(zbytes: ZBytes): Result<T> = runCatching {
    deserializeViaJNI(zbytes, typeOf<T>()) as T
}
