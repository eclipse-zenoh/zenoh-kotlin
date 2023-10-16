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

use std::sync::Arc;

use jni::{
    objects::{JObject, JString},
    JNIEnv, JavaVM,
};

use crate::errors::{Error, Result};

/// Converts a JString into a rust String.
pub(crate) fn decode_string(env: &mut JNIEnv, string: JString) -> Result<String> {
    let binding = env
        .get_string(&string)
        .map_err(|err| Error::Jni(format!("Error while retrieving JString: {}", err)))?;
    let value = binding
        .to_str()
        .map_err(|err| Error::Jni(format!("Error decoding JString: {}", err)))?;
    Ok(value.to_string())
}

pub(crate) fn get_java_vm(env: &mut JNIEnv) -> Result<JavaVM> {
    env.get_java_vm()
        .map_err(|err| Error::Jni(format!("Unable to retrieve JVM reference: {:?}", err)))
}

pub(crate) fn get_callback_global_ref(
    env: &mut JNIEnv,
    callback: JObject,
) -> crate::errors::Result<jni::objects::GlobalRef> {
    env.new_global_ref(callback).map_err(|err| {
        Error::Jni(format!(
            "Unable to get reference to the provided callback: {}",
            err
        ))
    })
}

/// A type that calls a function when dropped
pub(crate) struct CallOnDrop<F: FnOnce()>(core::mem::MaybeUninit<F>);
impl<F: FnOnce()> CallOnDrop<F> {
    /// Constructs a value that calls `f` when dropped.
    pub fn new(f: F) -> Self {
        Self(core::mem::MaybeUninit::new(f))
    }
    /// Does nothing, but tricks closures into moving the value inside,
    /// so that the closure's destructor will call `drop(self)`.
    pub fn noop(&self) {}
}
impl<F: FnOnce()> Drop for CallOnDrop<F> {
    fn drop(&mut self) {
        // Take ownership of the closure that is always initialized,
        // since the only constructor uses `MaybeUninit::new`
        let f = unsafe { self.0.assume_init_read() };
        // Call the now owned function
        f();
    }
}

pub(crate) fn load_on_close(
    java_vm: &Arc<jni::JavaVM>,
    on_close_global_ref: jni::objects::GlobalRef,
) -> CallOnDrop<impl FnOnce()> {
    CallOnDrop::new({
        let java_vm = java_vm.clone();
        move || {
            let mut env = match java_vm.attach_current_thread_as_daemon() {
                Ok(env) => env,
                Err(err) => {
                    log::error!("Unable to attach thread for 'onClose' callback: {}", err);
                    return;
                }
            };
            match env.call_method(on_close_global_ref, "run", "()V", &[]) {
                Ok(_) => (),
                Err(err) => {
                    _ = env.exception_describe();
                    _ = Error::Jni(format!("Error while running 'onClose' callback: {}", err))
                        .throw_on_jvm(&mut env)
                        .map_err(|err| {
                            log::error!("Unable to throw exception upon 'onClose' failure: {}", err)
                        });
                }
            }
        }
    })
}
