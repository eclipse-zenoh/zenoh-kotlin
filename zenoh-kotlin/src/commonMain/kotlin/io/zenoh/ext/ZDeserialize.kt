package io.zenoh.ext

import io.zenoh.bytes.ZBytes
import io.zenoh.bytes.intoAny
import io.zenoh.jni.JNIZBytes
import kotlin.reflect.KClass
import kotlin.reflect.KFunction1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.jvm.jvmErasure
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
 * @see ZBytes
 * @return a [Result] with the deserialization.
 */
inline fun <reified T: Any> zDeserialize(zbytes: ZBytes): Result<T> {
    val type = typeOf<T>()
    when {
        typeOf<List<*>>().isSupertypeOf(type) -> {
            val itemsClass = type.arguments.firstOrNull()?.type?.jvmErasure
            return zDeserialize(zbytes, T::class, arg1clazz = itemsClass)
        }
        typeOf<Map<*, *>>().isSupertypeOf(type) -> {
            val keyClass = type.arguments.getOrNull(0)?.type?.jvmErasure
            val valueClass = type.arguments.getOrNull(1)?.type?.jvmErasure
            return zDeserialize(zbytes, T::class, arg1clazz = keyClass, arg2clazz = valueClass)
        }
        typeOf<Any>().isSupertypeOf(type) -> {
            return zDeserialize(zbytes, T::class)
        }
    }
    throw IllegalArgumentException("Unsupported type for deserialization: '$type'.")
}

/**
 * Deserialize the [ZBytes] into an element of class [clazz].
 *
 * It's generally preferable to use the [zDeserialize] function with reification, however
 * this function is exposed for cases when reification needs to be avoided.
 *
 * Example:
 * ```kotlin
 * val list = listOf("value1", "value2", "value3")
 * val zbytes = serialize(list).getOrThrow()
 * val deserializedList = deserialize(zbytes, clazz = List::class, arg1clazz = String::class).getOrThrow()
 * check(list == deserializedList)
 * ```
 *
 * Supported types:
 * - [Number]: Byte, Short, Int, Long, Float, Double
 * - [String]
 * - [ByteArray]
 * - Lists and Maps of the above-mentioned types.
 *
 * @see [zDeserialize]
 *
 *
 * @param clazz: the [KClass] of the type to be serialized.
 * @param arg1clazz Optional first nested parameter of the provided clazz, for instance when trying to deserialize
 *  into a `List<String>`, arg1clazz should be set to `String::class`, when trying to deserialize into a
 *  `Map<Int, String>`, arg1clazz should be set to `Int::class`. Can be null if providing a basic type.
 * @param arg2clazz Optional second nested parameter of the provided clazz, to be used for the cases of maps.
 *  For instance, when trying to deserialize into a `Map<Int, String>`, arg2clazz should be set to `String::class`.
 *  Can be null if providing a basic type.
 */
@Suppress("UNCHECKED_CAST")
fun <T: Any> zDeserialize(
    zbytes: ZBytes,
    clazz: KClass<T>,
    arg1clazz: KClass<*>? = null,
    arg2clazz: KClass<*>? = null,
): Result<T> {
    val type: KType = when (clazz) {
        List::class -> typeOf<List<*>>()
        Map::class -> typeOf<Map<*, *>>()
        else -> clazz.createType()
    }
    return when {
        typeOf<List<*>>().isSupertypeOf(type) -> {
            val typeElement = arg1clazz?.createType()
            if (typeElement != null) {
                Result.success(JNIZBytes.deserializeIntoList(zbytes).map { it.intoAny(typeElement) } as T)
            } else {
                Result.failure(IllegalArgumentException("Unsupported list type for deserialization: $type"))
            }
        }

        typeOf<Map<*, *>>().isSupertypeOf(type) -> {
            val keyType = arg1clazz?.createType()
            val valueType = arg2clazz?.createType()
            if (keyType != null && valueType != null) {
                Result.success(
                    JNIZBytes.deserializeIntoMap(zbytes)
                    .map { (k, v) -> k.intoAny(keyType) to v.intoAny(valueType) }.toMap() as T
                )
            } else {
                Result.failure(IllegalArgumentException("Unsupported map type for deserialization: $type"))
            }
        }

        typeOf<Any>().isSupertypeOf(type) -> {
            Result.success(zbytes.intoAny(type) as T)
        }

        else -> Result.failure(IllegalArgumentException("Unsupported type for deserialization: $type"))
    }
}
