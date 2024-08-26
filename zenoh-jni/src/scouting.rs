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
    objects::{JClass, JList, JObject, JString, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{config::WhatAmIMatcher, prelude::Wait};
use zenoh::{scouting::Scout, Config};

use crate::{errors::Result, throw_exception, utils::decode_string};
use crate::{
    session_error,
    utils::{get_callback_global_ref, get_java_vm},
};

/// Start a scout.
///
/// # Params
/// - `whatAmI`: Ordinal value of the WhatAmI enum.
/// - `callback`: Callback to be executed whenever a hello message is received.
/// - `config_string`: Optional embedded configuration as a string.
/// - `format`: format of the `config_string` param.
/// - `config_path`: Optional path to a config file.
///
/// Note: Either the config_string or the config_path or None can be provided.
/// If none is provided, then the default configuration is loaded. Otherwise
/// it's the config_string or the config_path that are loaded. This consistency
/// logic is granted by the kotlin layer.
///
/// Returns a pointer to the scout, which must be freed afterwards.
/// If starting the scout fails, an exception is thrown on the JVM, and a null pointer is returned.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIScout_00024Companion_scoutViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    whatAmI: jint,
    callback: JObject,
    config_string: /*nullable=*/ JString,
    format: jint,
    config_path: /*nullable=*/ JString,
) -> *const Scout<()> {
    || -> Result<*const Scout<()>> {
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let whatAmIMatcher: WhatAmIMatcher = (whatAmI as u8).try_into().unwrap(); // The validity of the operation is guaranteed on the kotlin layer.
        let config = if config_string.is_null() && config_path.is_null() {
            Config::default()
        } else if !config_string.is_null() {
            let string_config = decode_string(&mut env, &config_string)?;
            match format {
                0 /*YAML*/ => {
                    let deserializer = serde_yaml::Deserializer::from_str(&string_config);
                    Config::from_deserializer(deserializer).map_err(|err| match err {
                        Ok(c) => session_error!("Invalid configuration: {}", c),
                        Err(e) => session_error!("YAML error: {}", e),
                    })?
                }
                1 | 2 /*JSON | JSON5*/ => {
                    let mut deserializer =
                    json5::Deserializer::from_str(&string_config).map_err(|err| session_error!(err))?;
                    Config::from_deserializer(&mut deserializer).map_err(|err| match err {
                        Ok(c) => session_error!("Invalid configuration: {}", c),
                        Err(e) => session_error!("JSON error: {}", e),
                    })?
                }
                _ => {
                    // This can never happen unless the Config.Format enum on Kotlin is wrongly modified!
                    Err(session_error!("Unexpected error: attempting to decode a config with a format other than Json, 
                        Json5 or Yaml. Check Config.Format for eventual modifications..."))?
                }
            }
        } else {
            let config_file_path = decode_string(&mut env, &config_path)?;
            Config::from_file(config_file_path).map_err(|err| session_error!(err))?
        };
        zenoh::scout(whatAmIMatcher, config)
            .callback(move |hello| {
                tracing::debug!("Received hello: {hello}");
                let _ = || -> jni::errors::Result<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon()?;
                    let whatami = hello.whatami() as jint;
                    let zenohid = env.new_string(hello.zid().to_string())?;
                    let locators = env
                        .new_object("java/util/ArrayList", "()V", &[])
                        .map(|it| env.auto_local(it))?;
                    let jlist = JList::from_env(&mut env, &locators)?;
                    for value in hello.locators() {
                        let locator = env.new_string(value.as_str())?;
                        jlist.add(&mut env, &locator)?;
                    }
                    env.call_method(
                        &callback_global_ref,
                        "run",
                        "(ILjava/lang/String;Ljava/util/List;)V",
                        &[
                            JValue::from(whatami),
                            JValue::from(&zenohid),
                            JValue::from(&locators),
                        ],
                    )?;
                    Ok(())
                }()
                .map_err(|err| tracing::error!("Error while scouting: ${err}"));
            })
            .wait()
            .map(|scout| Arc::into_raw(Arc::new(scout)))
            .map_err(|err| session_error!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}
