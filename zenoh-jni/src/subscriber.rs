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
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{
    key_expr::KeyExpr,
    prelude::Wait,
    session::{Session, SessionDeclarations},
    subscriber::{Reliability, Subscriber},
};

use crate::{
    errors::{Error, Result},
    utils::{bytes_to_java_array, slice_to_java_string},
};
use crate::{
    key_expr::process_kotlin_key_expr,
    utils::{get_callback_global_ref, get_java_vm, load_on_close},
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
/// - `key_expr_ptr`: Raw pointer to the key expression to be used for the subscriber. May be null, in
///     which case the key_expr_str parameter will be used.
/// - `key_expr_str`: String representation of the key expression to be used to declare the subscriber.
///     Not considered if the key_expr_ptr parameter is provided.
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
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    reliability: jint,
) -> Result<*const Subscriber<'static, ()>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let reliability = decode_reliability(reliability)?;
    let on_close = load_on_close(&java_vm, on_close_global_ref);

    let key_expr = process_kotlin_key_expr(env, &key_expr_str, key_expr_ptr)?;
    tracing::debug!("Declaring subscriber on '{}'...", key_expr);
    let session = Arc::from_raw(session_ptr);

    let result = session
        .declare_subscriber(key_expr.to_owned())
        .callback(move |sample| {
            on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
            let _ = || -> Result<()> {
                let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                    Error::Jni(format!("Unable to attach thread for subscriber: {}", err))
                })?;
                let byte_array = bytes_to_java_array(&env, sample.payload())?;

                let encoding_id: jint = sample.encoding().id() as jint;
                let encoding_schema = match sample.encoding().schema() {
                    Some(schema) => slice_to_java_string(&env, schema)?,
                    None => JString::default(),
                };
                let kind = sample.kind() as jint;
                let (timestamp, is_valid) = sample
                    .timestamp()
                    .map(|timestamp| (timestamp.get_time().as_u64(), true))
                    .unwrap_or((0, false));

                let attachment_bytes = sample
                    .attachment()
                    .map_or_else(
                        || Ok(JByteArray::default()),
                        |attachment| bytes_to_java_array(&env, attachment),
                    )
                    .map_err(|err| Error::Jni(format!("Error processing attachment: {err}")))?;

                let key_expr_str =
                    env.new_string(sample.key_expr().to_string())
                        .map_err(|err| {
                            Error::Jni(format!("Error processing sample key expr: {err}"))
                        })?;

                let express = sample.express();
                let priority = sample.priority() as jint;
                let cc = sample.congestion_control() as jint;

                if let Err(err) = env.call_method(
                    &callback_global_ref,
                    "run",
                    "(Ljava/lang/String;[BILjava/lang/String;IJZ[BZII)V",
                    &[
                        JValue::from(&key_expr_str),
                        JValue::from(&byte_array),
                        JValue::from(encoding_id),
                        JValue::from(&encoding_schema),
                        JValue::from(kind),
                        JValue::from(timestamp as i64),
                        JValue::from(is_valid),
                        JValue::from(&attachment_bytes),
                        JValue::from(express),
                        JValue::from(priority),
                        JValue::from(cc),
                    ],
                ) {
                    tracing::error!("On subscriber callback error: {}", err);
                }
                _ = env
                    .delete_local_ref(key_expr_str)
                    .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
                _ = env
                    .delete_local_ref(byte_array)
                    .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
                _ = env
                    .delete_local_ref(attachment_bytes)
                    .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
                Ok(())
            }()
            .map_err(|err| tracing::error!("On subscriber callback error: {err}"));
        })
        .reliability(reliability)
        .wait();

    std::mem::forget(session);

    let subscriber =
        result.map_err(|err| Error::Session(format!("Unable to declare subscriber: {}", err)))?;

    tracing::debug!(
        "Subscriber declared on '{}' with reliability '{:?}'.",
        key_expr,
        reliability
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
