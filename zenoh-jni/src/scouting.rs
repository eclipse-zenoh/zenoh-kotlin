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
use zenoh::prelude::Wait;
use zenoh::{config::WhatAmI, scouting::Scout, Config};

use crate::{errors::Result, throw_exception};
use crate::{
    session_error,
    utils::{get_callback_global_ref, get_java_vm},
};

#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIScout_00024Companion_scoutViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    _whatami: JString,
    callback: JObject,
    _config: JString,
) -> *const Scout<()> {
    || -> Result<*const Scout<()>> {
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        zenoh::scout(WhatAmI::Peer, Config::default())
            .callback(move |hello| {
                let _ = || -> jni::errors::Result<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon()?;
                    let whatami = hello.whatami() as jint;
                    let zenohid = env.new_string(hello.zid().to_string())?;
                    let locators = env
                        .new_object("java/util/ArrayList", "()V", &[])
                        .map(|it| env.auto_local(it))?;
                    let jlist = JList::from_env(&mut env, &locators)?;
                    for value in hello.locators() {
                        let locator = env.new_string(value.as_str()).unwrap();
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
