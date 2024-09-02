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
    JNIEnv,
};
use zenoh::Config;

use crate::errors::Result;
use crate::{session_error, throw_exception, utils::decode_string};

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
    || -> Result<*const Config> {
        let config_file_path = decode_string(&mut env, &config_path)?;
        let config = Config::from_file(config_file_path).map_err(|err| session_error!(err))?;
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
    || -> Result<*const Config> {
        let json_config = decode_string(&mut env, &json_config)?;
        let mut deserializer =
            json5::Deserializer::from_str(&json_config).map_err(|err| session_error!(err))?;
        let config = Config::from_deserializer(&mut deserializer).map_err(|err| match err {
            Ok(c) => session_error!("Invalid configuration: {}", c),
            Err(e) => session_error!("JSON error: {}", e),
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
    || -> Result<*const Config> {
        let yaml_config = decode_string(&mut env, &yaml_config)?;
        let deserializer = serde_yaml::Deserializer::from_str(&yaml_config);
        let config = Config::from_deserializer(deserializer).map_err(|err| match err {
            Ok(c) => session_error!("Invalid configuration: {}", c),
            Err(e) => session_error!("YAML error: {}", e),
        })?;
        Ok(Arc::into_raw(Arc::new(config)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
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
