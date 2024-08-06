package io.zenoh.protocol

/**
 * Serializable interface.
 *
 * Classes implementing this interface can be serialized into a ZBytes object.
 *
 * Example:
 * ```kotlin
 * class Foo(val content: String) : Serializable {
 *
 *   override fun into(): ZBytes = content.into()
 * }
 * ```
 */
interface Serializable {
    fun into(): ZBytes
}