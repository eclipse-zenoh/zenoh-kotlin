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

use std::{ops::Deref, sync::Arc};

use jni::{
    objects::{JClass, JObject, JValue},
    sys::{jint, jlong},
    JNIEnv,
};
use zenoh::prelude::r#sync::*;
use zenoh::subscriber::Subscriber;

use crate::utils::{get_callback_global_ref, get_java_vm, load_on_close};
use crate::{
    errors::{Error, Result},
    utils::attachment_to_vec,
};

/// Frees the memory associated with a Zenoh subscriber raw pointer via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh subscriber ([Subscriber]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided subscriber pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the subscriber pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNISubscriber_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const zenoh::subscriber::Subscriber<()>,
) {
    Arc::from_raw(ptr);
}

/// Declares a Zenoh subscriber via JNI.
///
/// Parameters:
/// - `env`: A mutable reference to the JNI environment.
/// - `key_expr_ptr`: Raw pointer to the key expression to be used for the subscriber.
/// - `session_ptr`: Raw pointer to the session to be used for the declaration..
/// - `callback`: The callback function as an instance of the `Callback` interface in Java/Kotlin.
/// - `onClose`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called when the subscriber is undeclared.
/// - `reliability`: The [Reliability] configuration as an ordinal.
///
/// Returns:
/// - A [Result] containing a raw pointer to the declared Zenoh subscriber ([Subscriber]) in case of success,
///   or an [Error] variant in case of failure.
///
/// Safety:
/// - The returned raw pointer should be stored appropriately and later freed using [Java_io_zenoh_jni_JNISubscriber_freePtrViaJNI].
///
pub(crate) unsafe fn declare_subscriber(
    env: &mut JNIEnv,
    key_expr_ptr: *const KeyExpr<'static>,
    session_ptr: *const zenoh::Session,
    callback: JObject,
    on_close: JObject,
    reliability: jint,
) -> Result<*const Subscriber<'static, ()>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let reliability = decode_reliability(reliability)?;
    let on_close = load_on_close(&java_vm, on_close_global_ref);

    let session = Arc::from_raw(session_ptr);
    let key_expr = Arc::from_raw(key_expr_ptr);
    let key_expr_clone = key_expr.deref().clone();
    tracing::debug!("Declaring subscriber on '{}'...", key_expr);
    let result = session
        .declare_subscriber(key_expr_clone.to_owned())
        .callback(move |sample| {
            on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
            let mut env = match java_vm.attach_current_thread_as_daemon() {
                Ok(env) => env,
                Err(err) => {
                    tracing::error!("Unable to attach thread for subscriber: {}", err);
                    return;
                }
            };

            let byte_array =
                match env.byte_array_from_slice(sample.value.payload.contiguous().as_ref()) {
                    Ok(byte_array) => byte_array,
                    Err(err) => {
                        tracing::error!("On subscriber callback error: {}", err.to_string());
                        return;
                    }
                };

            let encoding: jint = sample.value.encoding.prefix().to_owned() as jint;
            let kind = sample.kind.to_owned() as jint;
            let (timestamp, is_valid) = sample.timestamp.map_or_else(
                || (0, false),
                |timestamp| (timestamp.get_time().as_u64(), true),
            );

            let attachment_bytes = match sample.attachment.map_or_else(
                || env.byte_array_from_slice(&[]),
                |attachment| env.byte_array_from_slice(attachment_to_vec(attachment).as_slice()),
            ) {
                Ok(byte_array) => byte_array,
                Err(err) => {
                    tracing::error!(
                        "On subscriber callback error. Error processing attachment: {}.",
                        err.to_string()
                    );
                    return;
                }
            };

            let key_expr_ptr = Arc::into_raw(Arc::new(sample.key_expr));
            match env.call_method(
                &callback_global_ref,
                "run",
                "(J[BIIJZ[B)V",
                &[
                    JValue::from(key_expr_ptr as jlong),
                    JValue::from(&byte_array),
                    JValue::from(encoding),
                    JValue::from(kind),
                    JValue::from(timestamp as i64),
                    JValue::from(is_valid),
                    JValue::from(&attachment_bytes),
                ],
            ) {
                Ok(_) => {}
                Err(err) => {
                    tracing::error!("On subscriber callback error: {}", err.to_string());
                    Arc::from_raw(key_expr_ptr); // Free key expr pointer
                }
            };
            _ = env
                .delete_local_ref(byte_array)
                .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
            _ = env
                .delete_local_ref(attachment_bytes)
                .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
        })
        .reliability(reliability)
        .res();
    std::mem::forget(session);
    std::mem::forget(key_expr);

    let subscriber =
        result.map_err(|err| Error::Session(format!("Unable to declare subscriber: {}", err)))?;

    tracing::debug!(
        "Subscriber declared on '{}' with reliability '{:?}'.",
        key_expr_clone,
        reliability,
    );
    Ok(Arc::into_raw(Arc::new(subscriber)))
}

fn decode_reliability(reliability: jint) -> Result<Reliability> {
    match reliability {
        0 => Ok(Reliability::BestEffort),
        1 => Ok(Reliability::Reliable),
        value => Err(Error::Session(format!(
            "Unable to decode reliability '{value}'"
        ))),
    }
}
