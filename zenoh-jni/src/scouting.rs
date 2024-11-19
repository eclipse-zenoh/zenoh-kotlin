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
    objects::{GlobalRef, JClass, JList, JObject, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{config::WhatAmIMatcher, Wait};
use zenoh::{scouting::Scout, Config};

use crate::utils::{get_callback_global_ref, get_java_vm, load_on_close};
use crate::{errors::ZResult, throw_exception, zerror};

/// Start a scout.
///
/// # Params
/// - `whatAmI`: Ordinal value of the WhatAmI enum.
/// - `callback`: Callback to be executed whenever a hello message is received.
/// - `config_ptr`: Optional config pointer.
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
    on_close: JObject,
    config_ptr: /*nullable=*/ *const Config,
) -> *const Scout<()> {
    || -> ZResult<*const Scout<()>> {
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let on_close_global_ref: GlobalRef = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);
        let whatAmIMatcher: WhatAmIMatcher = (whatAmI as u8).try_into().unwrap(); // The validity of the operation is guaranteed on the kotlin layer.
        let config = if config_ptr.is_null() {
            Config::default()
        } else {
            let arc_cfg = Arc::from_raw(config_ptr);
            let config_clone = arc_cfg.as_ref().clone();
            std::mem::forget(arc_cfg);
            config_clone
        };
        zenoh::scout(whatAmIMatcher, config)
            .callback(move |hello| {
                on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
                tracing::debug!("Received hello: {hello}");
                let _ = || -> jni::errors::Result<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon()?;
                    let whatami = hello.whatami() as jint;
                    let zenoh_id = env
                        .byte_array_from_slice(&hello.zid().to_le_bytes())
                        .map(|it| env.auto_local(it))?;
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
                        "(I[BLjava/util/List;)V",
                        &[
                            JValue::from(whatami),
                            JValue::from(&zenoh_id),
                            JValue::from(&locators),
                        ],
                    )?;
                    Ok(())
                }()
                .map_err(|err| tracing::error!("Error while scouting: ${err}"));
            })
            .wait()
            .map(|scout| Arc::into_raw(Arc::new(scout)))
            .map_err(|err| zerror!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Frees the scout.
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIScout_00024Companion_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    scout_ptr: *const Scout<()>,
) {
    Arc::from_raw(scout_ptr);
}
