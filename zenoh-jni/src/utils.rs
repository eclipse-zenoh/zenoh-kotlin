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
    objects::{JByteArray, JObject, JString},
    JNIEnv, JavaVM,
};
use zenoh::sample::{Attachment, AttachmentBuilder};

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

/// Helper function to convert a JByteArray into a Vec<u8>.
pub(crate) fn decode_byte_array(env: &JNIEnv<'_>, payload: JByteArray) -> Result<Vec<u8>> {
    let payload_len = env
        .get_array_length(&payload)
        .map(|length| length as usize)
        .map_err(|err| Error::Jni(err.to_string()))?;
    let mut buff = vec![0; payload_len];
    env.get_byte_array_region(payload, 0, &mut buff[..])
        .map_err(|err| Error::Jni(err.to_string()))?;
    let buff: Vec<u8> = buff.iter().map(|&x| x as u8).collect();
    Ok(buff)
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

pub(crate) fn attachment_to_vec(attachment: Attachment) -> Vec<u8> {
    let mut buffer: Vec<u8> = Vec::new();
    for (key, value) in attachment.iter() {
        buffer.extend(&i32::to_le_bytes(key.len().try_into().unwrap()));
        buffer.extend(&key[..]);
        buffer.extend(&i32::to_le_bytes(value.len().try_into().unwrap()));
        buffer.extend(&value[..]);
    }
    buffer
}

pub(crate) fn vec_to_attachment(bytes: Vec<u8>) -> Attachment {
    let mut builder = AttachmentBuilder::new();
    let mut idx = 0;
    let i32_size = std::mem::size_of::<i32>();
    let mut slice_size;

    while idx < bytes.len() {
        slice_size = i32::from_le_bytes(
            bytes[idx..idx + i32_size]
                .try_into()
                .expect("Error decoding i32."),
        );
        idx += i32_size;

        let key = &bytes[idx..idx + slice_size as usize];
        idx += slice_size as usize;

        slice_size = i32::from_le_bytes(
            bytes[idx..idx + i32_size]
                .try_into()
                .expect("Error decoding i32."),
        );
        idx += i32_size;

        let value = &bytes[idx..idx + slice_size as usize];
        idx += slice_size as usize;

        builder.insert(key, value);
    }

    builder.build()
}
