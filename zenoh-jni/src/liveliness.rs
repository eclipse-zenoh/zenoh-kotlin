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

use std::{ptr::null, sync::Arc, time::Duration};

use jni::{
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::{jboolean, jint, jlong},
    JNIEnv,
};

use zenoh::{
    internal::runtime::ZRuntime, key_expr::KeyExpr, liveliness::LivelinessToken,
    pubsub::Subscriber, sample::Sample, Session, Wait,
};

use crate::{
    errors::ZResult,
    key_expr::process_kotlin_key_expr,
    session::{on_reply_error, on_reply_success},
    throw_exception,
    utils::{
        bytes_to_java_array, get_callback_global_ref, get_java_vm, load_on_close,
        slice_to_java_string,
    },
    zerror,
};

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNILiveliness_getViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    callback: JObject,
    timeout_ms: jlong,
    on_close: JObject,
) {
    let session = unsafe { Arc::from_raw(session_ptr) };
    let _ = || -> ZResult<()> {
        let key_expr = unsafe { process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr) }?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);
        let timeout = Duration::from_millis(timeout_ms as u64);
        let replies = session
            .liveliness()
            .get(key_expr.to_owned())
            .timeout(timeout)
            .wait()
            .map_err(|err| zerror!(err))?;

        ZRuntime::Application.spawn(async move {
            on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
            while let Ok(reply) = replies.recv_async().await {
                || -> ZResult<()> {
                    tracing::debug!("Receiving liveliness reply through JNI: {:?}", reply);
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        zerror!(
                            "Unable to attach thread for GET liveliness query callback: {}.",
                            err
                        )
                    })?;
                    match reply.result() {
                        Ok(sample) => on_reply_success(
                            &mut env,
                            reply.replier_id(),
                            sample,
                            &callback_global_ref,
                        ),
                        Err(error) => on_reply_error(
                            &mut env,
                            reply.replier_id(),
                            error,
                            &callback_global_ref,
                        ),
                    }
                }()
                .unwrap_or_else(|err| tracing::error!("Error on get liveliness callback: {err}."));
            }
        });
        Ok(())
    }()
    .map_err(|err| {
        throw_exception!(env, err);
    });
    std::mem::forget(session);
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNILiveliness_declareTokenViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
) -> *const LivelinessToken {
    let session = unsafe { Arc::from_raw(session_ptr) };
    let ptr = || -> ZResult<*const LivelinessToken> {
        let key_expr = unsafe { process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr) }?;
        tracing::trace!("Declaring liveliness token on '{key_expr}'.");
        let token = session
            .liveliness()
            .declare_token(key_expr)
            .wait()
            .map_err(|err| zerror!(err))?;
        Ok(Arc::into_raw(Arc::new(token)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    });
    std::mem::forget(session);
    ptr
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNILivelinessToken_00024Companion_undeclareViaJNI(
    _env: JNIEnv,
    _: JClass,
    token_ptr: *const LivelinessToken,
) {
    unsafe { Arc::from_raw(token_ptr) };
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNILiveliness_declareSubscriberViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    callback: JObject,
    history: jboolean,
    on_close: JObject,
) -> *const Subscriber<()> {
    let session = unsafe { Arc::from_raw(session_ptr) };
    || -> ZResult<*const Subscriber<()>> {
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);

        let key_expr = unsafe { process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr) }?;
        tracing::debug!("Declaring liveliness subscriber on '{}'...", key_expr);

        let result = session
            .liveliness()
            .declare_subscriber(key_expr.to_owned())
            .history(history != 0)
            .callback(move |sample: Sample| {
                let _ = || -> ZResult<()> {
                    on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        zerror!("Unable to attach thread for liveliness subscriber: {}", err)
                    })?;
                    let byte_array = bytes_to_java_array(&env, sample.payload())
                        .map(|array| env.auto_local(array))?;

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
                        .map(|array| env.auto_local(array))
                        .map_err(|err| zerror!("Error processing attachment: {}", err))?;

                    let key_expr_str = env.auto_local(
                        env.new_string(sample.key_expr().to_string())
                            .map_err(|err| zerror!("Error processing sample key expr: {}", err))?,
                    );

                    let express = sample.express();
                    let priority = sample.priority() as jint;
                    let cc = sample.congestion_control() as jint;

                    env.call_method(
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
                    )
                    .map_err(|err| zerror!(err))?;
                    Ok(())
                }()
                .map_err(|err| tracing::error!("On liveliness subscriber callback error: {err}"));
            })
            .wait();

        let subscriber =
            result.map_err(|err| zerror!("Unable to declare liveliness subscriber: {}", err))?;

        tracing::debug!("Subscriber declared on '{}'.", key_expr);
        std::mem::forget(session);
        Ok(Arc::into_raw(Arc::new(subscriber)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}
