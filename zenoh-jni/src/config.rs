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

use std::{ptr::null, sync::Arc};

use jni::{
    objects::{JClass, JString},
    sys::jstring,
    JNIEnv,
};
use zenoh::Config;

use crate::{errors::ZResult, zerror};
use crate::{throw_exception, utils::decode_string};

/// Loads the default configuration, returning a raw pointer to it.
///
/// The pointer to the config is expected to be freed later on upon the destruction of the
/// Kotlin Config instance.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_loadDefaultConfigViaJNI(
    _env: JNIEnv,
    _class: JClass,
) -> *const Config {
    let config = Config::default();
    Arc::into_raw(Arc::new(config))
}

/// Loads the config from a file, returning a pointer to the loaded config in case of success.
/// In case of failure, an exception is thrown via JNI.
///
/// The pointer to the config is expected to be freed later on upon the destruction of the
/// Kotlin Config instance.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_loadConfigFileViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    config_path: JString,
) -> *const Config {
    || -> ZResult<*const Config> {
        let config_file_path = decode_string(&mut env, &config_path)?;
        let config = Config::from_file(config_file_path).map_err(|err| zerror!(err))?;
        Ok(Arc::into_raw(Arc::new(config)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Loads the config from a json/json5 formatted string, returning a pointer to the loaded config
/// in case of success. In case of failure, an exception is thrown via JNI.
///
/// The pointer to the config is expected to be freed later on upon the destruction of the
/// Kotlin Config instance.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_loadJsonConfigViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    json_config: JString,
) -> *const Config {
    || -> ZResult<*const Config> {
        let json_config = decode_string(&mut env, &json_config)?;
        let mut deserializer =
            json5::Deserializer::from_str(&json_config).map_err(|err| zerror!(err))?;
        let config = Config::from_deserializer(&mut deserializer).map_err(|err| match err {
            Ok(c) => zerror!("Invalid configuration: {}", c),
            Err(e) => zerror!("JSON error: {}", e),
        })?;
        Ok(Arc::into_raw(Arc::new(config)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Loads the config from a yaml-formatted string, returning a pointer to the loaded config
/// in case of success. In case of failure, an exception is thrown via JNI.
///
/// The pointer to the config is expected to be freed later on upon the destruction of the
/// Kotlin Config instance.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_loadYamlConfigViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    yaml_config: JString,
) -> *const Config {
    || -> ZResult<*const Config> {
        let yaml_config = decode_string(&mut env, &yaml_config)?;
        let deserializer = serde_yaml::Deserializer::from_str(&yaml_config);
        let config = Config::from_deserializer(deserializer).map_err(|err| match err {
            Ok(c) => zerror!("Invalid configuration: {}", c),
            Err(e) => zerror!("YAML error: {}", e),
        })?;
        Ok(Arc::into_raw(Arc::new(config)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Returns the json value associated to the provided [key]. May throw an exception in case of failure, which must be handled
/// on the kotlin layer.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_getJsonViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    cfg_ptr: *const Config,
    key: JString,
) -> jstring {
    let arc_cfg: Arc<Config> = Arc::from_raw(cfg_ptr);
    let result = || -> ZResult<jstring> {
        let key = decode_string(&mut env, &key)?;
        let json = arc_cfg.get_json(&key).map_err(|err| zerror!(err))?;
        let java_json = env.new_string(json).map_err(|err| zerror!(err))?;
        Ok(java_json.as_raw())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JString::default().as_raw()
    });
    std::mem::forget(arc_cfg);
    result
}

/// Inserts a json5 value associated to the provided [key]. May throw an exception in case of failure, which must be handled
/// on the kotlin layer.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_insertJson5ViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    cfg_ptr: *const Config,
    key: JString,
    value: JString,
) {
    || -> ZResult<()> {
        let key = decode_string(&mut env, &key)?;
        let value = decode_string(&mut env, &value)?;
        let mut config = core::ptr::read(cfg_ptr);
        let insert_result = config
            .insert_json5(&key, &value)
            .map_err(|err| zerror!(err));
        core::ptr::write(cfg_ptr as *mut _, config);
        insert_result
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
    })
}

/// Frees the pointer to the config. The pointer should be valid and should have been obtained through
/// one of the preceding `load` functions. This function should be called upon destruction of the kotlin
/// Config instance.
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIConfig_00024Companion_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    config_ptr: *const Config,
) {
    Arc::from_raw(config_ptr);
}
