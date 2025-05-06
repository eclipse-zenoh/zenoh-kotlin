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
    objects::{JClass, JObject, JString},
    sys::{jboolean, jlong},
    JNIEnv,
};

use zenoh::{
    internal::runtime::ZRuntime, key_expr::KeyExpr, liveliness::LivelinessToken,
    pubsub::Subscriber, Session, Wait,
};

use crate::{
    errors::ZResult,
    key_expr::process_kotlin_key_expr,
    owned_object::OwnedObject,
    sample_callback::SetJniSampleCallback,
    session::{on_reply_error, on_reply_success},
    throw_exception,
    utils::{get_callback_global_ref, get_java_vm, load_on_close},
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
    let session = unsafe { OwnedObject::from_raw(session_ptr) };
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
    let session = unsafe { OwnedObject::from_raw(session_ptr) };
    || -> ZResult<*const LivelinessToken> {
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
    })
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
    let session = unsafe { OwnedObject::from_raw(session_ptr) };
    || -> ZResult<*const Subscriber<()>> {
        let key_expr = unsafe { process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)? };
        tracing::debug!("Declaring liveliness subscriber on '{}'...", key_expr);

        let result = unsafe {
            session
                .liveliness()
                .declare_subscriber(key_expr.to_owned())
                .history(history != 0)
                .set_jni_sample_callback(&mut env, callback, on_close)?
                .wait()
        };

        let subscriber =
            result.map_err(|err| zerror!("Unable to declare liveliness subscriber: {}", err))?;

        tracing::debug!("Liveliness subscriber declared on '{}'.", key_expr);
        Ok(Arc::into_raw(Arc::new(subscriber)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}
