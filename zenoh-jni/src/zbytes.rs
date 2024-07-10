// let map = env.new_object("java/util/HashMap", "()V", &[]).unwrap();
// let jmap = JMap::from_env(&mut env, &map).unwrap();
// jmap.put(env, key, value)

use std::collections::HashMap;

use jni::{
    objects::{JByteArray, JClass, JList, JMap, JObject},
    sys::{jbyteArray, jobject},
    JNIEnv,
};
use zenoh::bytes::ZBytes;

use crate::{errors::Result, utils::bytes_to_java_array};
use crate::{throw_exception, utils::decode_byte_array};

///
/// Map serialization and deserialization
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_serializeIntoMapViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    map: JObject,
) -> jbyteArray {
    || -> Result<jobject> {
        let jmap = JMap::from_env(&mut env, &map).unwrap();
        let mut iterator = jmap.iter(&mut env).unwrap();
        let mut rust_map: HashMap<Vec<u8>, Vec<u8>> = HashMap::new();
        while let Some((key, value)) = iterator.next(&mut env).unwrap() {
            let key_bytes = env.auto_local(JByteArray::from(key));
            let value_bytes = env.auto_local(JByteArray::from(value));

            let key_bytes = env.convert_byte_array(key_bytes).unwrap();
            let value_bytes = env.convert_byte_array(value_bytes).unwrap();
            rust_map.insert(key_bytes, value_bytes);
        }
        let zmap = ZBytes::serialize(rust_map);
        let byte_array = bytes_to_java_array(&env, &zmap).unwrap();
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_deserializeIntoMapViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
) -> jobject {
    || -> Result<jobject> {
        let payload = decode_byte_array(&env, payload)?;
        let zbytes = ZBytes::new(payload);
        let deserialization: HashMap<Vec<u8>, Vec<u8>> =
            zbytes.deserialize::<HashMap<Vec<u8>, Vec<u8>>>().unwrap();

        let map = env.new_object("java/util/HashMap", "()V", &[]).unwrap();
        let jmap = JMap::from_env(&mut env, &map).unwrap();

        deserialization.into_iter().for_each(|(k, v)| {
            let key = env.byte_array_from_slice(k.as_slice()).unwrap();
            let value = env.byte_array_from_slice(v.as_slice()).unwrap();
            jmap.put(&mut env, &key, &value).unwrap();
        });

        Ok(map.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

///
/// List serialization and deserialization
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_serializeIntoListViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    list: JObject,
) -> jbyteArray {
    || -> Result<jobject> {
        let jmap = JList::from_env(&mut env, &list).unwrap();
        let mut iterator = jmap.iter(&mut env).unwrap();
        let mut rust_vec: Vec<Vec<u8>> = Vec::new();
        while let Some(value) = iterator.next(&mut env).unwrap() {
            let value_bytes = env.auto_local(JByteArray::from(value));
            let value_bytes = env.convert_byte_array(value_bytes).unwrap();
            rust_vec.push(value_bytes);
        }
        let zmap = ZBytes::from_iter(rust_vec);
        let byte_array = bytes_to_java_array(&env, &zmap).unwrap();
        Ok(byte_array.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZBytes_deserializeIntoListViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
) -> jobject {
    || -> Result<jobject> {
        let payload = decode_byte_array(&env, payload)?;
        let zbytes = ZBytes::new(payload);
        let array_list = env.new_object("java/util/ArrayList", "()V", &[]).unwrap();
        let jlist = JList::from_env(&mut env, &array_list).unwrap();

        for (_idx, value) in zbytes.iter::<Vec<u8>>().enumerate() {
            let value = &mut env
                .byte_array_from_slice(value.unwrap().as_slice())
                .unwrap();
            jlist.add(&mut env, value).unwrap();
        }

        Ok(array_list.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::null().as_raw()
    })
}
