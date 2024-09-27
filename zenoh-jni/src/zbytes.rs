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
    objects::{JByteArray, JClass, JList, JMap, JObject},
    sys::{jbyteArray, jobject},
    JNIEnv,
};
use zenoh::bytes::ZBytes;
use zenoh_ext::{z_deserialize, z_serialize};

use crate::{errors::ZResult, utils::bytes_to_java_array, zerror};
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
) -> jbyteArray {
    || -> ZResult<jobject> {
        let zbytes = java_map_to_zbytes(&mut env, &map).map_err(|err| zerror!(err))?;
        let byte_array = bytes_to_java_array(&env, &zbytes)?;
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn java_map_to_zbytes(env: &mut JNIEnv, map: &JObject) -> jni::errors::Result<ZBytes> {
    let jmap = JMap::from_env(env, map)?;
    let mut iterator = jmap.iter(env)?;
    let mut rust_map: HashMap<Vec<u8>, Vec<u8>> = HashMap::new();
    while let Some((key, value)) = iterator.next(env)? {
        let key_bytes = env.convert_byte_array(env.auto_local(JByteArray::from(key)))?;
        let value_bytes = env.convert_byte_array(env.auto_local(JByteArray::from(value)))?;
        rust_map.insert(key_bytes, value_bytes);
    }
    Ok(z_serialize(&rust_map))
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
) -> jbyteArray {
    || -> ZResult<jobject> {
        let zbytes = java_list_to_zbytes(&mut env, &list).map_err(|err| zerror!(err))?;
        let byte_array = bytes_to_java_array(&env, &zbytes)?;
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

fn java_list_to_zbytes(env: &mut JNIEnv, list: &JObject) -> jni::errors::Result<ZBytes> {
    let jmap = JList::from_env(env, list)?;
    let mut iterator = jmap.iter(env)?;
    let mut rust_vec: Vec<Vec<u8>> = Vec::new();
    while let Some(value) = iterator.next(env)? {
        let value_bytes = env.auto_local(JByteArray::from(value));
        let value_bytes = env.convert_byte_array(value_bytes)?;
        rust_vec.push(value_bytes);
    }
    let zbytes = z_serialize(&rust_vec);
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
