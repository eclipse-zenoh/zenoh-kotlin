package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.jni.JNIZBytes.serializeViaJNI
import kotlin.reflect.typeOf

/**
 * Serialize an element of type [T] into a [ZBytes].
 *
 * @see ZBytes
 * @return a [Result] with the serialized [ZBytes].
 */
inline fun <reified T: Any> zSerialize(t: T): Result<ZBytes> = runCatching {
    serializeViaJNI(t, typeOf<T>())
}
