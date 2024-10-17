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

use jni::{
    objects::{AutoLocal, JByteArray, JClass, JList, JMap, JObject, JString, JValue},
    sys::jobject,
    JNIEnv,
};
use zenoh::bytes::ZBytes;
use zenoh_ext::{VarInt, ZDeserializeError, ZDeserializer, ZSerializer};

use crate::{
    errors::ZResult,
    throw_exception,
    utils::{bytes_to_java_array, decode_byte_array},
    zerror,
};

enum KotlinType {
    Boolean,
    String,
    ByteArray,
    Byte,
    Short,
    Int,
    Long,
    Float,
    Double,
    UByte,
    UShort,
    UInt,
    ULong,
    List(Box<KotlinType>),
    Map(Box<KotlinType>, Box<KotlinType>),
    Pair(Box<KotlinType>, Box<KotlinType>),
    Triple(Box<KotlinType>, Box<KotlinType>, Box<KotlinType>),
}

fn decode_ktype(env: &mut JNIEnv, ktype: JObject) -> ZResult<KotlinType> {
    let classifier_obj = env
        .call_method(
            &ktype,
            "getClassifier",
            "()Lkotlin/reflect/KClassifier;",
            &[],
        )
        .map_err(|err| zerror!(err))?
        .l()
        .map_err(|err| zerror!(err))?;
    let classifier_obj = AutoLocal::new(classifier_obj, env);

    let kclass_class = env
        .find_class("kotlin/reflect/KClass")
        .map_err(|err| zerror!(err))?;
    let is_kclass = env
        .is_instance_of(&classifier_obj, kclass_class)
        .map_err(|err| zerror!(err))?;
    if is_kclass {
        let qualified_name_jobject = env
            .call_method(
                &classifier_obj,
                "getQualifiedName",
                "()Ljava/lang/String;",
                &[],
            )
            .map_err(|err| zerror!(err))?
            .l()
            .map_err(|err| zerror!(err))?;

        let qualified_name: String = env
            .get_string(&JString::from(qualified_name_jobject))
            .map_err(|err| zerror!(err))?
            .into();

        match qualified_name.as_str() {
            "kotlin.Boolean" => Ok(KotlinType::Boolean),
            "kotlin.String" => Ok(KotlinType::String),
            "kotlin.ByteArray" => Ok(KotlinType::ByteArray),
            "kotlin.Byte" => Ok(KotlinType::Byte),
            "kotlin.Short" => Ok(KotlinType::Short),
            "kotlin.Int" => Ok(KotlinType::Int),
            "kotlin.Long" => Ok(KotlinType::Long),
            "kotlin.Float" => Ok(KotlinType::Float),
            "kotlin.Double" => Ok(KotlinType::Double),
            "kotlin.UByte" => Ok(KotlinType::UByte),
            "kotlin.UShort" => Ok(KotlinType::UShort),
            "kotlin.UInt" => Ok(KotlinType::UInt),
            "kotlin.ULong" => Ok(KotlinType::ULong),
            "kotlin.collections.List" => Ok(KotlinType::List(Box::new(decode_ktype_arg(
                env, &ktype, 0,
            )?))),
            "kotlin.collections.Map" => Ok(KotlinType::Map(
                Box::new(decode_ktype_arg(env, &ktype, 0)?),
                Box::new(decode_ktype_arg(env, &ktype, 1)?),
            )),
            "kotlin.Pair" => Ok(KotlinType::Pair(
                Box::new(decode_ktype_arg(env, &ktype, 0)?),
                Box::new(decode_ktype_arg(env, &ktype, 1)?),
            )),
            "kotlin.Triple" => Ok(KotlinType::Triple(
                Box::new(decode_ktype_arg(env, &ktype, 0)?),
                Box::new(decode_ktype_arg(env, &ktype, 1)?),
                Box::new(decode_ktype_arg(env, &ktype, 2)?),
            )),
            _ => Err(zerror!("Unsupported type: {}", qualified_name)),
        }
    } else {
        Err(zerror!("Classifier is not a KClass"))
    }
}

fn decode_ktype_arg(env: &mut JNIEnv, ktype: &JObject, idx: i32) -> ZResult<KotlinType> {
    let arguments = env
        .call_method(ktype, "getArguments", "()Ljava/util/List;", &[])
        .map_err(|err| zerror!(err))?
        .l()
        .map_err(|err| zerror!(err))?;
    let arg = env
        .call_method(
            &arguments,
            "get",
            "(I)Ljava/lang/Object;",
            &[JValue::Int(idx)],
        )
        .map_err(|err| zerror!(err))?
        .l()
        .map_err(|err| zerror!(err))?;
    let ktype = env
        .call_method(arg, "getType", "()Lkotlin/reflect/KType;", &[])
        .map_err(|err| zerror!(err))?
        .l()
        .map_err(|err| zerror!(err))?;
    decode_ktype(env, ktype)
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_serializeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    any: JObject,
    ktype: JObject,
) -> jobject {
    || -> ZResult<jobject> {
        let mut serializer = ZSerializer::new();
        let ktype = decode_ktype(&mut env, ktype)?;
        serialize(&mut env, &mut serializer, any, &ktype)?;
        let zbytes = serializer.finish();

        let byte_array = bytes_to_java_array(&env, &zbytes).map_err(|err| zerror!(err))?;
        let zbytes_obj = env
            .new_object(
                "io/zenoh/bytes/ZBytes",
                "([B)V",
                &[JValue::Object(&JObject::from(byte_array))],
            )
            .map_err(|err| zerror!(err))?;

        Ok(zbytes_obj.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::default().as_raw()
    })
}

fn serialize(
    env: &mut JNIEnv,
    serializer: &mut ZSerializer,
    any: JObject,
    ktype: &KotlinType,
) -> ZResult<()> {
    match ktype {
        KotlinType::Byte => {
            let byte_value = env
                .call_method(any, "byteValue", "()B", &[])
                .map_err(|err| zerror!(err))?
                .b()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(byte_value);
        }
        KotlinType::Short => {
            let short_value = env
                .call_method(any, "shortValue", "()S", &[])
                .map_err(|err| zerror!(err))?
                .s()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(short_value);
        }
        KotlinType::Int => {
            let int_value = env
                .call_method(any, "intValue", "()I", &[])
                .map_err(|err| zerror!(err))?
                .i()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(int_value);
        }
        KotlinType::Long => {
            let long_value = env
                .call_method(any, "longValue", "()J", &[])
                .map_err(|err| zerror!(err))?
                .j()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(long_value);
        }
        KotlinType::Float => {
            let float_value = env
                .call_method(any, "floatValue", "()F", &[])
                .map_err(|err| zerror!(err))?
                .f()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(float_value);
        }
        KotlinType::Double => {
            let double_value = env
                .call_method(any, "doubleValue", "()D", &[])
                .map_err(|err| zerror!(err))?
                .d()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(double_value);
        }
        KotlinType::Boolean => {
            let boolean_value = env
                .call_method(any, "booleanValue", "()Z", &[])
                .map_err(|err| zerror!(err))?
                .z()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(boolean_value);
        }
        KotlinType::String => {
            let jstring = JString::from(any);
            let string_value: String = env.get_string(&jstring).map_err(|err| zerror!(err))?.into();
            serializer.serialize(string_value);
        }
        KotlinType::ByteArray => {
            let jbyte_array = JByteArray::from(any);
            let bytes = decode_byte_array(env, jbyte_array).map_err(|err| zerror!(err))?;
            serializer.serialize(bytes);
        }
        KotlinType::UByte => {
            let byte = env
                .get_field(any, "data", "B")
                .map_err(|err| zerror!(err))?
                .b()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(byte as u8);
        }
        KotlinType::UShort => {
            let short = env
                .get_field(any, "data", "S")
                .map_err(|err| zerror!(err))?
                .s()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(short as u16);
        }
        KotlinType::UInt => {
            let int = env
                .get_field(any, "data", "I")
                .map_err(|err| zerror!(err))?
                .i()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(int as u32);
        }
        KotlinType::ULong => {
            let long = env
                .get_field(any, "data", "J")
                .map_err(|err| zerror!(err))?
                .j()
                .map_err(|err| zerror!(err))?;
            serializer.serialize(long as u64);
        }
        KotlinType::List(kotlin_type) => {
            let jlist: JList<'_, '_, '_> =
                JList::from_env(env, &any).map_err(|err| zerror!(err))?;
            let mut iterator = jlist.iter(env).map_err(|err| zerror!(err))?;
            let list_size = jlist.size(env).unwrap();
            serializer.serialize(zenoh_ext::VarInt(list_size as usize));
            while let Some(value) = iterator.next(env).map_err(|err| zerror!(err))? {
                serialize(env, serializer, value, kotlin_type)?;
            }
        }
        KotlinType::Map(key_type, value_type) => {
            let jmap = JMap::from_env(env, &any).map_err(|err| zerror!(err))?;

            let map_size = env
                .call_method(&jmap, "size", "()I", &[])
                .map_err(|err| zerror!(err))?
                .i()
                .map_err(|err| zerror!(err))?;

            serializer.serialize(zenoh_ext::VarInt(map_size as usize));

            let mut iterator = jmap.iter(env).map_err(|err| zerror!(err))?;
            while let Some((key, value)) = iterator.next(env).map_err(|err| zerror!(err))? {
                serialize(env, serializer, key, key_type)?;
                serialize(env, serializer, value, value_type)?;
            }
        }
        KotlinType::Pair(first_type, second_type) => {
            let first = env
                .call_method(&any, "getFirst", "()Ljava/lang/Object;", &[])
                .map_err(|err| zerror!(err))?
                .l()
                .map_err(|err| zerror!(err))?;
            let second = env
                .call_method(&any, "getSecond", "()Ljava/lang/Object;", &[])
                .map_err(|err| zerror!(err))?
                .l()
                .map_err(|err| zerror!(err))?;
            serialize(env, serializer, first, first_type)?;
            serialize(env, serializer, second, second_type)?;
        }
        KotlinType::Triple(first_type, second_type, third_type) => {
            let first = env
                .call_method(&any, "getFirst", "()Ljava/lang/Object;", &[])
                .map_err(|err| zerror!(err))?
                .l()
                .map_err(|err| zerror!(err))?;
            let second = env
                .call_method(&any, "getSecond", "()Ljava/lang/Object;", &[])
                .map_err(|err| zerror!(err))?
                .l()
                .map_err(|err| zerror!(err))?;
            let third = env
                .call_method(&any, "getThird", "()Ljava/lang/Object;", &[])
                .map_err(|err| zerror!(err))?
                .l()
                .map_err(|err| zerror!(err))?;
            serialize(env, serializer, first, first_type)?;
            serialize(env, serializer, second, second_type)?;
            serialize(env, serializer, third, third_type)?;
        }
    }
    Ok(())
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_deserializeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    zbytes: JObject,
    ktype: JObject,
) -> jobject {
    || -> ZResult<jobject> {
        let payload = env
            .get_field(zbytes, "bytes", "[B")
            .map_err(|err| zerror!(err))?;
        let decoded_bytes: Vec<u8> =
            decode_byte_array(&env, JByteArray::from(payload.l().unwrap()))?;
        let zbytes = ZBytes::from(decoded_bytes);
        let mut deserializer = ZDeserializer::new(&zbytes);
        let ktype = decode_ktype(&mut env, ktype)?;
        let obj = deserialize(&mut env, &mut deserializer, &ktype)?;
        if !deserializer.done() {
            return Err(zerror!(ZDeserializeError));
        }
        Ok(obj)
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::default().as_raw()
    })
}

fn deserialize(
    env: &mut JNIEnv,
    deserializer: &mut ZDeserializer,
    ktype: &KotlinType,
) -> ZResult<jobject> {
    match ktype {
        KotlinType::Byte => {
            let byte = deserializer
                .deserialize::<i8>()
                .map_err(|err| zerror!(err))?;
            let byte_obj = env
                .new_object("java/lang/Byte", "(B)V", &[JValue::Byte(byte)])
                .map_err(|err| zerror!(err))?;
            Ok(byte_obj.as_raw())
        }
        KotlinType::Short => {
            let short = deserializer
                .deserialize::<i16>()
                .map_err(|err| zerror!(err))?;
            let short_obj = env
                .new_object("java/lang/Short", "(S)V", &[JValue::Short(short)])
                .map_err(|err| zerror!(err))?;
            Ok(short_obj.as_raw())
        }
        KotlinType::Int => {
            let integer = deserializer
                .deserialize::<i32>()
                .map_err(|err| zerror!(err))?;
            let integer_obj = env
                .new_object("java/lang/Integer", "(I)V", &[JValue::Int(integer)])
                .map_err(|err| zerror!(err))?;
            Ok(integer_obj.as_raw())
        }
        KotlinType::Long => {
            let long = deserializer
                .deserialize::<i64>()
                .map_err(|err| zerror!(err))?;
            let long_obj = env
                .new_object("java/lang/Long", "(J)V", &[JValue::Long(long)])
                .map_err(|err| zerror!(err))?;
            Ok(long_obj.as_raw())
        }
        KotlinType::Float => {
            let float = deserializer
                .deserialize::<f32>()
                .map_err(|err| zerror!(err))?;
            let float_obj = env
                .new_object("java/lang/Float", "(F)V", &[JValue::Float(float)])
                .map_err(|err| zerror!(err))?;
            Ok(float_obj.as_raw())
        }
        KotlinType::Double => {
            let double = deserializer
                .deserialize::<f64>()
                .map_err(|err| zerror!(err))?;
            let double_obj = env
                .new_object("java/lang/Double", "(D)V", &[JValue::Double(double)])
                .map_err(|err| zerror!(err))?;
            Ok(double_obj.as_raw())
        }
        KotlinType::Boolean => {
            let boolean_value = deserializer
                .deserialize::<bool>()
                .map_err(|err| zerror!(err))?;
            let jboolean = if boolean_value { 1u8 } else { 0u8 };
            let boolean_obj = env
                .new_object("java/lang/Boolean", "(Z)V", &[JValue::Bool(jboolean)])
                .map_err(|err| zerror!(err))?;
            Ok(boolean_obj.as_raw())
        }
        KotlinType::String => {
            let deserialized_string = deserializer
                .deserialize::<String>()
                .map_err(|err| zerror!(err))?;
            let jstring = env
                .new_string(&deserialized_string)
                .map_err(|err| zerror!(err))?;
            Ok(jstring.into_raw())
        }
        KotlinType::ByteArray => {
            let deserialized_bytes = deserializer
                .deserialize::<Vec<u8>>()
                .map_err(|err| zerror!(err))?;
            let jbytes = env
                .byte_array_from_slice(deserialized_bytes.as_slice())
                .map_err(|err| zerror!(err))?;
            Ok(jbytes.into_raw())
        }
        KotlinType::UByte => {
            let ubyte = deserializer
                .deserialize::<u8>()
                .map_err(|err| zerror!(err))?;
            let ubyte_obj = env
                .new_object("kotlin/UByte", "(B)V", &[JValue::Byte(ubyte as i8)])
                .map_err(|err| zerror!(err))?;
            Ok(ubyte_obj.as_raw())
        }
        KotlinType::UShort => {
            let ushort = deserializer
                .deserialize::<u16>()
                .map_err(|err| zerror!(err))?;
            let ushort_obj = env
                .new_object("kotlin/UShort", "(S)V", &[JValue::Short(ushort as i16)])
                .map_err(|err| zerror!(err))?;
            Ok(ushort_obj.as_raw())
        }
        KotlinType::UInt => {
            let uint = deserializer
                .deserialize::<u32>()
                .map_err(|err| zerror!(err))?;
            let uint_obj = env
                .new_object("kotlin/UInt", "(I)V", &[JValue::Int(uint as i32)])
                .map_err(|err| zerror!(err))?;
            Ok(uint_obj.as_raw())
        }
        KotlinType::ULong => {
            let ulong = deserializer
                .deserialize::<u64>()
                .map_err(|err| zerror!(err))?;
            let ulong_obj = env
                .new_object("kotlin/ULong", "(J)V", &[JValue::Long(ulong as i64)])
                .map_err(|err| zerror!(err))?;
            Ok(ulong_obj.as_raw())
        }
        KotlinType::List(kotlin_type) => {
            let list_size = deserializer
                .deserialize::<VarInt<usize>>()
                .map_err(|err| zerror!(err))?
                .0;
            let array_list = env
                .new_object("java/util/ArrayList", "()V", &[])
                .map_err(|err| zerror!(err))?;
            let jlist = JList::from_env(env, &array_list).map_err(|err| zerror!(err))?;

            for _ in 0..list_size {
                let item = deserialize(env, deserializer, kotlin_type)?;
                let item_obj = unsafe { JObject::from_raw(item) };
                jlist.add(env, &item_obj).map_err(|err| zerror!(err))?;
            }
            Ok(array_list.as_raw())
        }
        KotlinType::Map(key_type, value_type) => {
            let map_size = deserializer
                .deserialize::<VarInt<usize>>()
                .map_err(|err| zerror!(err))?
                .0;
            let map = env
                .new_object("java/util/HashMap", "()V", &[])
                .map_err(|err| zerror!(err))?;
            let jmap = JMap::from_env(env, &map).map_err(|err| zerror!(err))?;

            for _ in 0..map_size {
                let key = deserialize(env, deserializer, key_type)?;
                let key_obj = unsafe { JObject::from_raw(key) };
                let value = deserialize(env, deserializer, value_type)?;
                let value_obj = unsafe { JObject::from_raw(value) };
                jmap.put(env, &key_obj, &value_obj)
                    .map_err(|err| zerror!(err))?;
            }
            Ok(map.as_raw())
        }
        KotlinType::Pair(first_type, second_type) => {
            let first = deserialize(env, deserializer, first_type)?;
            let second = deserialize(env, deserializer, second_type)?;
            let pair = env
                .new_object(
                    "kotlin/Pair",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    &[
                        JValue::Object(&unsafe { JObject::from_raw(first) }),
                        JValue::Object(&unsafe { JObject::from_raw(second) }),
                    ],
                )
                .map_err(|err| zerror!(err))?;
            Ok(pair.as_raw())
        }
        KotlinType::Triple(first_type, second_type, third_type) => {
            let first = deserialize(env, deserializer, first_type)?;
            let second = deserialize(env, deserializer, second_type)?;
            let third = deserialize(env, deserializer, third_type)?;
            let triple = env
                .new_object(
                    "kotlin/Triple",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                    &[
                        JValue::Object(&unsafe { JObject::from_raw(first) }),
                        JValue::Object(&unsafe { JObject::from_raw(second) }),
                        JValue::Object(&unsafe { JObject::from_raw(third) }),
                    ],
                )
                .map_err(|err| zerror!(err))?;
            Ok(triple.as_raw())
        }
    }
}
