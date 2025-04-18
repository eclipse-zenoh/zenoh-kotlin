package io.zenoh.bytes

/**
 * IntoZBytes interface.
 *
 * Classes implementing this interface can be serialized into a ZBytes object.
 *
 * Example:
 * ```kotlin
 * class Foo(val content: String) : IntoZBytes {
 *
 *   override fun into(): ZBytes = ZBytes.from(content)
 * }
 * ```
 */
interface IntoZBytes {
    fun into(): ZBytes
}
