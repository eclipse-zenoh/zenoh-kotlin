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

use std::collections::HashMap;

use jni::{
    objects::{JByteArray, JClass, JList, JMap, JObject, JString},
    sys::{jbyteArray, jobject},
    JNIEnv,
};
use zenoh::bytes::ZBytes;
use zenoh_ext::{z_deserialize, z_serialize, Serialize};

use crate::{
    errors::ZResult,
    utils::{bytes_to_java_array, decode_string},
    zerror,
};
use crate::{throw_exception, utils::decode_byte_array};

///
/// Map serialization and deserialization
///

/// Serializes a Map<ByteArray, ByteArray>, returning the resulting ByteArray.
///
/// # Parameters
/// - `env``: the JNI environment.
/// - `_class`: The java class.
/// - `map`: A Java bytearray map.
///
/// # Returns:
/// - Returns the serialized map as a byte array.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_serializeIntoMapViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    map: JObject,
    keyType: JString,
    valueType: JString,
) -> jbyteArray {
    || -> ZResult<jobject> {
        let key_type = decode_string(&mut env, &keyType)?;
        let value_type = decode_string(&mut env, &valueType)?;
        let zbytes = java_map_to_zbytes(&mut env, &map, key_type, value_type)?;
        let byte_array = bytes_to_java_array(&env, &zbytes)?;
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn java_map_to_zbytes(
    env: &mut JNIEnv,
    map: &JObject,
    key_type: String,
    value_type: String,
) -> ZResult<ZBytes> {
    let jmap = JMap::from_env(env, map).map_err(|err| zerror!(err))?;
    let mut iterator = jmap.iter(env).map_err(|err| zerror!(err))?;
    let mut rust_map: HashMap<Vec<u8>, Vec<u8>> = HashMap::new();

    while let Some((key, value)) = iterator.next(env).map_err(|err| zerror!(err))? {
        let key_bytes = env
            .convert_byte_array(env.auto_local(JByteArray::from(key)))
            .map_err(|err| zerror!(err))?;
        let value_bytes = env
            .convert_byte_array(env.auto_local(JByteArray::from(value)))
            .map_err(|err| zerror!(err))?;
        rust_map.insert(key_bytes, value_bytes);
    }

    let mut serializable_map: Vec<(KotlinTypes, KotlinTypes)> = Vec::new();

    for (key_bytes, value_bytes) in rust_map {
        let key_converted = decode_type(key_bytes, &key_type)?;
        let value_converted = decode_type(value_bytes, &value_type)?;
        serializable_map.push((key_converted, value_converted));
    }

    Ok(z_serialize(&serializable_map))
}

/// Deserializes a serialized bytearray map, returning the original map.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Java class.
/// - `serialized_map`: The byte array resulting of the serialization of a bytearray map.
///
/// # Returns
/// - The original byte array map before serialization.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_deserializeIntoMapViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    serialized_map: JByteArray,
) -> jobject {
    || -> ZResult<jobject> {
        let payload = decode_byte_array(&env, serialized_map)?;
        let zbytes = ZBytes::from(payload);
        let deserialization: HashMap<Vec<u8>, Vec<u8>> =
            z_deserialize(&zbytes).map_err(|err| zerror!(err))?;
        hashmap_to_java_map(&mut env, &deserialization).map_err(|err| zerror!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn hashmap_to_java_map(
    env: &mut JNIEnv,
    hashmap: &HashMap<Vec<u8>, Vec<u8>>,
) -> jni::errors::Result<jobject> {
    let map = env.new_object("java/util/HashMap", "()V", &[])?;
    let jmap = JMap::from_env(env, &map)?;

    for (k, v) in hashmap.iter() {
        let key = env.byte_array_from_slice(k.as_slice())?;
        let value = env.byte_array_from_slice(v.as_slice())?;
        jmap.put(env, &key, &value)?;
    }
    Ok(map.as_raw())
}

///
/// List serialization and deserialization
///

/// Serializes a list of byte arrays, returning a byte array.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Java class.
/// - `list`: The Java list of byte arrays to serialize.
///
/// # Returns:
/// - The serialized list as a ByteArray.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_serializeIntoListViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    list: JObject,
    list_type: JString,
) -> jbyteArray {
    || -> ZResult<jobject> {
        let list_type = decode_string(&mut env, &list_type)?;
        let zbytes = java_list_to_zbytes(&mut env, &list, list_type).map_err(|err| zerror!(err))?;
        let byte_array = bytes_to_java_array(&env, &zbytes)?;
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn java_list_to_zbytes(env: &mut JNIEnv, list: &JObject, list_type: String) -> ZResult<ZBytes> {
    let jmap = JList::from_env(env, list).map_err(|err| zerror!(err))?;
    let mut iterator = jmap.iter(env).map_err(|err| zerror!(err))?;
    let mut rust_vec: Vec<Vec<u8>> = Vec::new();
    while let Some(value) = iterator.next(env).map_err(|err| zerror!(err))? {
        let value_bytes = env.auto_local(JByteArray::from(value));
        let value_bytes = env
            .convert_byte_array(value_bytes)
            .map_err(|err| zerror!(err))?;
        rust_vec.push(value_bytes);
    }

    let mut serializable_vec: Vec<KotlinTypes> = Vec::new();
    for bytes in rust_vec {
        let converted_value = decode_type(bytes, &list_type)?;
        serializable_vec.push(converted_value);
    }
    let zbytes = z_serialize(&serializable_vec);
    Ok(zbytes)
}

/// Deserializes a serialized list of byte arrrays, returning the original list.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Java class.
/// - `serialized_list`: The byte array resulting of the serialization of a bytearray list.
///
/// # Returns:
/// - The original list of byte arrays prior to serialization.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_deserializeIntoListViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    serialized_list: JByteArray,
) -> jobject {
    || -> ZResult<jobject> {
        let payload = decode_byte_array(&env, serialized_list)?;
        let zbytes = ZBytes::from(payload);
        zbytes_to_java_list(&mut env, &zbytes).map_err(|err| zerror!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn zbytes_to_java_list(env: &mut JNIEnv, zbytes: &ZBytes) -> ZResult<jobject> {
    let array_list = env
        .new_object("java/util/ArrayList", "()V", &[])
        .map_err(|err| zerror!(err))?;
    let jlist = JList::from_env(env, &array_list).map_err(|err| zerror!(err))?;
    let values: Vec<Vec<u8>> = z_deserialize(zbytes).map_err(|err| zerror!(err))?;
    for value in values {
        let value = &mut env
            .byte_array_from_slice(&value)
            .map_err(|err| zerror!(err))?;
        jlist.add(env, value).map_err(|err| zerror!(err))?;
    }
    Ok(array_list.as_raw())
}

fn decode_type(bytes: Vec<u8>, ty: &str) -> ZResult<KotlinTypes> {
    match ty {
        "String" => Ok(KotlinTypes::String(String::from_utf8(bytes).unwrap())),
        "ByteArray" => Ok(KotlinTypes::ByteArray(bytes)),
        "Byte" => {
            if bytes.len() != 1 {
                return Err(zerror!("Invalid byte length for Byte"));
            }
            Ok(KotlinTypes::Byte(bytes[0] as i8))
        }
        "UByte" => {
            if bytes.len() != 1 {
                return Err(zerror!("Invalid byte length for UByte"));
            }
            Ok(KotlinTypes::UByte(bytes[0]))
        }
        "Short" => {
            if bytes.len() != 2 {
                return Err(zerror!("Invalid byte length for Short"));
            }
            let arr: [u8; 2] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to Short: {:?}", bytes))?;
            Ok(KotlinTypes::Short(i16::from_le_bytes(arr)))
        }
        "UShort" => {
            if bytes.len() != 2 {
                return Err(zerror!("Invalid byte length for UShort"));
            }
            let arr: [u8; 2] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to UShort: {:?}", bytes))?;
            Ok(KotlinTypes::UShort(u16::from_le_bytes(arr)))
        }
        "Int" => {
            if bytes.len() != 4 {
                return Err(zerror!("Invalid byte length for Int"));
            }
            let arr: [u8; 4] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to Int: {:?}", bytes))?;
            Ok(KotlinTypes::Int(i32::from_le_bytes(arr)))
        }
        "UInt" => {
            if bytes.len() != 4 {
                return Err(zerror!("Invalid byte length for UInt"));
            }
            let arr: [u8; 4] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to UInt: {:?}", bytes))?;
            Ok(KotlinTypes::UInt(u32::from_le_bytes(arr)))
        }
        "Long" => {
            if bytes.len() != 8 {
                return Err(zerror!("Invalid byte length for Long"));
            }
            let arr: [u8; 8] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to Long: {:?}", bytes))?;
            Ok(KotlinTypes::Long(i64::from_le_bytes(arr)))
        }
        "ULong" => {
            if bytes.len() != 8 {
                return Err(zerror!("Invalid byte length for ULong"));
            }
            let arr: [u8; 8] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to ULong: {:?}", bytes))?;
            Ok(KotlinTypes::ULong(u64::from_le_bytes(arr)))
        }
        "Float" => {
            if bytes.len() != 4 {
                return Err(zerror!("Invalid byte length for Float"));
            }
            let arr: [u8; 4] = bytes
                .try_into()
                .map_err(|bytes| zerror!("Failed to convert to Float: {:?}", bytes))?;
            Ok(KotlinTypes::Float(f32::from_le_bytes(arr)))
        }
        "Double" => {
            if bytes.len() != 8 {
                return Err(zerror!("Invalid byte length for Double"));
            }
            let arr: [u8; 8] = bytes
                .try_into()
                .map_err(|_| zerror!("Failed to convert to Double"))?;
            Ok(KotlinTypes::Double(f64::from_le_bytes(arr)))
        }
        _ => Err(zerror!("Unsupported type: {}", ty)),
    }
}

enum KotlinTypes {
    Byte(i8),
    UByte(u8),
    Short(i16),
    UShort(u16),
    Int(i32),
    UInt(u32),
    Long(i64),
    ULong(u64),
    Float(f32),
    Double(f64),
    String(String),
    ByteArray(Vec<u8>),
}

impl Serialize for KotlinTypes {
    fn serialize(&self, serializer: &mut zenoh_ext::ZSerializer) {
        match self {
            KotlinTypes::Byte(value) => value.serialize(serializer),
            KotlinTypes::UByte(value) => value.serialize(serializer),
            KotlinTypes::Short(value) => value.serialize(serializer),
            KotlinTypes::UShort(value) => value.serialize(serializer),
            KotlinTypes::Int(value) => value.serialize(serializer),
            KotlinTypes::UInt(value) => value.serialize(serializer),
            KotlinTypes::Long(value) => value.serialize(serializer),
            KotlinTypes::ULong(value) => value.serialize(serializer),
            KotlinTypes::Float(value) => value.serialize(serializer),
            KotlinTypes::Double(value) => value.serialize(serializer),
            KotlinTypes::String(value) => value.serialize(serializer),
            KotlinTypes::ByteArray(vec) => vec.serialize(serializer),
        }
    }
}
