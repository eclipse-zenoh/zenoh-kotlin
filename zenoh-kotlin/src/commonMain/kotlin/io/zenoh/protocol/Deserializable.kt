package io.zenoh.protocol

/**
 * Deserializable interface.
 *
 * Classes implementing these two nested interfaces can be deserialized into a ZBytes object.
 *
 * The class must be declared as [Deserializable], but it's also necessary to make the companion
 * object of the class implement the [Deserializable.From], as shown in the example below:
 *
 * ```kotlin
 * class Foo(val content: String) : Deserializable {
 *
 *   companion object: Deserializable.From {
 *      override fun from(zbytes: ZBytes): Foo {
 *          return Foo(zbytes.toString())
 *      }
 *   }
 * }
 * ```
 */
interface Deserializable {
    interface From {
        fun from(zbytes: ZBytes): IntoZBytes
    }
}
