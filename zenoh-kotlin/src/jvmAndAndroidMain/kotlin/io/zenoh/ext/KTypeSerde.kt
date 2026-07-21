package io.zenoh.ext

import io.zenoh.jni.bytes.SerializationCodec
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Build a [SerializationCodec.SerdeType] from a Kotlin [KType] — the reflection adapter for
 * the pure-Kotlin serializer. Inspects the type's classifier (a [KClass] with a
 * qualified name) so it recognizes the Kotlin-only unsigned value classes and
 * `Pair`/`Triple` that erase away in `java.lang.reflect.Type`. Mirrors the
 * former native `decode_ktype`, in Kotlin. Lives in `jvmAndAndroidMain` (the
 * source set carrying kotlin-reflect).
 */
internal fun serdeTypeOf(type: KType): SerializationCodec.SerdeType {
    val classifier = type.classifier as? KClass<*>
        ?: throw SerializationCodec.SerdeException("KType classifier is not a class: $type")
    val name = classifier.qualifiedName
        ?: throw SerializationCodec.SerdeException("KType classifier has no qualified name: $type")
    return when (name) {
        "kotlin.Boolean" -> SerializationCodec.SerdeType.Bool
        "kotlin.Byte" -> SerializationCodec.SerdeType.I8
        "kotlin.Short" -> SerializationCodec.SerdeType.I16
        "kotlin.Int" -> SerializationCodec.SerdeType.I32
        "kotlin.Long" -> SerializationCodec.SerdeType.I64
        "kotlin.UByte" -> SerializationCodec.SerdeType.U8
        "kotlin.UShort" -> SerializationCodec.SerdeType.U16
        "kotlin.UInt" -> SerializationCodec.SerdeType.U32
        "kotlin.ULong" -> SerializationCodec.SerdeType.U64
        "kotlin.Float" -> SerializationCodec.SerdeType.F32
        "kotlin.Double" -> SerializationCodec.SerdeType.F64
        "kotlin.String" -> SerializationCodec.SerdeType.Str
        "kotlin.ByteArray" -> SerializationCodec.SerdeType.Bytes
        "kotlin.collections.List" -> SerializationCodec.SerdeType.ZList(arg(type, 0))
        "kotlin.collections.Map" -> SerializationCodec.SerdeType.ZMap(arg(type, 0), arg(type, 1))
        "kotlin.Pair" -> SerializationCodec.SerdeType.ZPair(arg(type, 0), arg(type, 1))
        "kotlin.Triple" -> SerializationCodec.SerdeType.ZTriple(arg(type, 0), arg(type, 1), arg(type, 2))
        else -> throw SerializationCodec.SerdeException("Unsupported type: $name")
    }
}

private fun arg(type: KType, idx: Int): SerializationCodec.SerdeType {
    val argType = type.arguments[idx].type
        ?: throw SerializationCodec.SerdeException("star projection unsupported at argument $idx of $type")
    return serdeTypeOf(argType)
}
