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
    objects::{JClass, JObject},
    sys::jboolean,
    JNIEnv,
};
use zenoh::prelude::r#sync::*;
use zenoh::{queryable::Queryable, Session};

use crate::{
    errors::Error,
    errors::Result,
    query::on_query,
    utils::{get_callback_global_ref, get_java_vm, load_on_close},
};

/// Frees the memory associated with a Zenoh queryable raw pointer via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh queryable ([Queryable]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided queryable pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the queryable pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQueryable_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const zenoh::queryable::Queryable<'_, ()>,
) {
    Arc::from_raw(ptr);
}

/// Declares a Zenoh queryable via JNI.
///
/// Parameters:
/// - `env`: A mutable reference to the JNI environment.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the queryable.
/// - `session_ptr`: Raw pointer to the [Session] from which to declare the queryable..
/// - `callback`: The callback function as an instance of the `Callback` interface in Java/Kotlin.
/// - `on_close`: The `on_close` callback function as an instance of the `JNIOnCloseCallback` interface in
///     Java/Kotlin, to be called when Zenoh notfies that no more queries will be received.
/// - `complete`: The completeness of the queryable.
///
/// Returns:
/// - A [Result] containing a raw pointer to the declared Zenoh queryable ([Queryable]) in case of success,
///   or an [Error] variant in case of failure.
///
/// Safety:
/// - The returned raw pointer should be stored appropriately and later freed using [Java_io_zenoh_jni_JNIQueryable_freePtrViaJNI].
///
pub(crate) unsafe fn declare_queryable(
    env: &mut JNIEnv,
    key_expr_ptr: *const KeyExpr<'static>,
    session_ptr: *const zenoh::Session,
    callback: JObject,
    on_close: JObject,
    complete: jboolean,
) -> Result<Queryable<'static, ()>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let complete = complete != 0;
    let on_close = load_on_close(&java_vm, on_close_global_ref);

    let session: Arc<Session> = Arc::from_raw(session_ptr);
    let key_expr = Arc::from_raw(key_expr_ptr);
    let key_expr_clone = key_expr.deref().clone();
    tracing::debug!("Declaring queryable through JNI on {}", key_expr);
    let queryable = session
        .declare_queryable(key_expr_clone)
        .callback(move |query| {
            on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
            let env = match java_vm.attach_current_thread_as_daemon() {
                Ok(env) => env,
                Err(err) => {
                    tracing::error!("Unable to attach thread for queryable callback: {}", err);
                    return;
                }
            };

            tracing::debug!("Receiving query through JNI: {}", query.to_string());
            match on_query(env, query, &callback_global_ref) {
                Ok(_) => tracing::debug!("Queryable callback called successfully."),
                Err(err) => tracing::error!("Error calling queryable callback: {}", err),
            }
        })
        .complete(complete);

    std::mem::forget(session);
    std::mem::forget(key_expr);
    queryable
        .res()
        .map_err(|err| Error::Session(format!("Error declaring queryable: {}", err)))
}
