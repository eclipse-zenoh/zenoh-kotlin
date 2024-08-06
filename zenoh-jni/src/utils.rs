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

use crate::{
    errors::{Error, Result},
    jni_error, session_error, throw_exception,
};
use jni::{
    objects::{JByteArray, JObject, JString},
    sys::jint,
    JNIEnv, JavaVM,
};
use zenoh::{
    bytes::{Encoding, ZBytes},
    internal::buffers::ZSlice,
    pubsub::Reliability,
    qos::{CongestionControl, Priority},
    query::{ConsolidationMode, QueryTarget},
};

/// Converts a JString into a rust String.
pub(crate) fn decode_string(env: &mut JNIEnv, string: &JString) -> Result<String> {
    let binding = env
        .get_string(string)
        .map_err(|err| jni_error!("Error while retrieving JString: {}", err))?;
    let value = binding
        .to_str()
        .map_err(|err| jni_error!("Error decoding JString: {}", err))?;
    Ok(value.to_string())
}

pub(crate) fn decode_encoding(
    env: &mut JNIEnv,
    encoding: jint,
    schema: &JString,
) -> Result<Encoding> {
    let schema: Option<ZSlice> = if schema.is_null() {
        None
    } else {
        Some(decode_string(env, schema)?.into_bytes().into())
    };
    let encoding_id =
        u16::try_from(encoding).map_err(|err| jni_error!("Failed to decode encoding: {}", err))?;
    Ok(Encoding::new(encoding_id, schema))
}

pub(crate) fn get_java_vm(env: &mut JNIEnv) -> Result<JavaVM> {
    env.get_java_vm()
        .map_err(|err| jni_error!("Unable to retrieve JVM reference: {}", err))
}

pub(crate) fn get_callback_global_ref(
    env: &mut JNIEnv,
    callback: JObject,
) -> crate::errors::Result<jni::objects::GlobalRef> {
    env.new_global_ref(callback)
        .map_err(|err| jni_error!("Unable to get reference to the provided callback: {}", err))
}

/// Helper function to convert a JByteArray into a Vec<u8>.
pub(crate) fn decode_byte_array(env: &JNIEnv<'_>, payload: JByteArray) -> Result<Vec<u8>> {
    let payload_len = env
        .get_array_length(&payload)
        .map(|length| length as usize)
        .map_err(|err| jni_error!(err))?;
    let mut buff = vec![0; payload_len];
    env.get_byte_array_region(payload, 0, &mut buff[..])
        .map_err(|err| jni_error!(err))?;
    let buff: Vec<u8> = unsafe { std::mem::transmute::<Vec<i8>, Vec<u8>>(buff) };
    Ok(buff)
}

pub(crate) fn decode_priority(priority: jint) -> Result<Priority> {
    Priority::try_from(priority as u8)
        .map_err(|err| session_error!("Error retrieving priority: {}.", err))
}

pub(crate) fn decode_congestion_control(congestion_control: jint) -> Result<CongestionControl> {
    match congestion_control {
        1 => Ok(CongestionControl::Block),
        0 => Ok(CongestionControl::Drop),
        value => Err(session_error!("Unknown congestion control '{}'.", value)),
    }
}

pub(crate) fn decode_query_target(target: jint) -> Result<QueryTarget> {
    match target {
        0 => Ok(QueryTarget::BestMatching),
        1 => Ok(QueryTarget::All),
        2 => Ok(QueryTarget::AllComplete),
        value => Err(session_error!("Unable to decode QueryTarget '{}'.", value)),
    }
}

pub(crate) fn decode_consolidation(consolidation: jint) -> Result<ConsolidationMode> {
    match consolidation {
        0 => Ok(ConsolidationMode::Auto),
        1 => Ok(ConsolidationMode::None),
        2 => Ok(ConsolidationMode::Monotonic),
        3 => Ok(ConsolidationMode::Latest),
        value => Err(session_error!("Unable to decode consolidation '{}'", value)),
    }
}

pub(crate) fn decode_reliability(reliability: jint) -> Result<Reliability> {
    match reliability {
        0 => Ok(Reliability::BestEffort),
        1 => Ok(Reliability::Reliable),
        value => Err(session_error!("Unable to decode reliability '{}'", value)),
    }
}

pub(crate) fn bytes_to_java_array<'a>(env: &JNIEnv<'a>, slice: &ZBytes) -> Result<JByteArray<'a>> {
    env.byte_array_from_slice(
        slice
            .deserialize::<Vec<u8>>()
            .map_err(|err| session_error!("Unable to deserialize slice: {}", err))?
            .as_ref(),
    )
    .map_err(|err| jni_error!(err))
}

pub(crate) fn slice_to_java_string<'a>(env: &JNIEnv<'a>, slice: &ZSlice) -> Result<JString<'a>> {
    env.new_string(
        String::from_utf8(slice.to_vec())
            .map_err(|err| session_error!("Unable to decode string: {}", err))?,
    )
    .map_err(|err| jni_error!(err))
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
                    tracing::error!("Unable to attach thread for 'onClose' callback: {}", err);
                    return;
                }
            };
            match env.call_method(on_close_global_ref, "run", "()V", &[]) {
                Ok(_) => (),
                Err(err) => {
                    _ = env.exception_describe();
                    throw_exception!(
                        env,
                        jni_error!("Error while running 'onClose' callback: {}", err)
                    );
                }
            }
        }
    })
}
