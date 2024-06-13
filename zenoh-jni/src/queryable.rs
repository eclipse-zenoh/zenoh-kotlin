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
    objects::{GlobalRef, JByteArray, JClass, JObject, JString, JValue},
    sys::{jboolean, jint, jlong},
    JNIEnv,
};
use zenoh::{
    key_expr::KeyExpr,
    prelude::Wait,
    session::{Session, SessionDeclarations},
};
use zenoh::{query::Query, queryable::Queryable};

use crate::{
    errors::{Error, Result},
    key_expr::process_kotlin_key_expr,
    utils::{
        bytes_to_java_array, get_callback_global_ref, get_java_vm, load_on_close,
        slice_to_java_string,
    },
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
/// - `key_expr_ptr`: Raw pointer to a declared [KeyExpr] to be used for the queryable. May be null in case
///     of using a non declared key expression, in which case the key_expr_str parameter will be used instead.
/// - `key_expr_str`: String representation of the key expression to be used to declare the queryable.
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
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    complete: jboolean,
) -> Result<Queryable<'static, ()>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let key_expr = process_kotlin_key_expr(env, &key_expr_str, key_expr_ptr)?;
    let complete = complete != 0;
    let on_close = load_on_close(&java_vm, on_close_global_ref);
    let session: Arc<Session> = Arc::from_raw(session_ptr);
    tracing::debug!("Declaring queryable through JNI on {}", key_expr);
    let queryable = session
        .declare_queryable(key_expr)
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
    queryable
        .wait()
        .map_err(|err| Error::Session(format!("Error declaring queryable: {}", err)))
}

/// Handles a Zenoh query callback via JNI.
///
/// This function is responsible for invoking the query callback function provided by the user from Java/Kotlin.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `query`: The Zenoh [Query] to be passed to the callback function.
/// - `callback_global_ref`: A global object reference of the callback function.
///
/// Returns:
/// - A [Result] indicating success or failure.
///
/// Note:
/// - The callback reference `callback_global_ref` should point to the Java/Kotlin implementation
///   of the `onQuery` function (which receives a `Query` as a parameter) from the `Callback` interface.
///
pub(crate) fn on_query(
    mut env: JNIEnv,
    query: Query,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let selector = query.selector();
    let value = query.value();

    let selector_params_jstr =
        env.new_string(selector.parameters().to_string())
            .map_err(|err| {
                Error::Jni(format!(
                    "Could not create a JString through JNI for the Query key expression. {}",
                    err
                ))
            })?;

    let (with_value, payload, encoding_id, encoding_schema) = if let Some(value) = value {
        let byte_array = bytes_to_java_array(&env, value.payload())?;
        let encoding_id = value.encoding().id() as jint;
        let encoding_schema = match value.encoding().schema() {
            Some(schema) => slice_to_java_string(&env, schema)?,
            None => JString::default(),
        };
        (true, byte_array, encoding_id, encoding_schema)
    } else {
        (false, JByteArray::default(), 0, JString::default())
    };

    let attachment_bytes = query
        .attachment()
        .map_or_else(
            || Ok(JByteArray::default()),
            |attachment| bytes_to_java_array(&env, attachment),
        )
        .map_err(|err| Error::Jni(format!("Error processing attachment of reply: '{}'.", err)))?;

    let key_expr_str = env
        .new_string(&query.key_expr().to_string())
        .map_err(|err| {
            Error::Jni(format!(
                "Could not create a JString through JNI for the Query key expression. {}",
                err
            ))
        })?;

    let query_ptr = Arc::into_raw(Arc::new(query));

    let result = env
        .call_method(
            callback_global_ref,
            "run",
            "(Ljava/lang/String;Ljava/lang/String;Z[BILjava/lang/String;[BJ)V",
            &[
                JValue::from(&key_expr_str),
                JValue::from(&selector_params_jstr),
                JValue::from(with_value),
                JValue::from(&payload),
                JValue::from(encoding_id),
                JValue::from(&encoding_schema),
                JValue::from(&attachment_bytes),
                JValue::from(query_ptr as jlong),
            ],
        )
        .map(|_| ())
        .map_err(|err| {
            // The callback could not be invoked, therefore the created kotlin query object won't be
            // used. Since `query_ptr` as well as `key_expr_ptr` was created within this function
            // and remains unaltered, it is safe to reclaim ownership of the memory by converting
            // the raw pointers back into an `Arc` and freeing the memory.
            unsafe {
                Arc::from_raw(query_ptr);
            };
            _ = env.exception_describe();
            Error::Jni(format!("{}", err))
        });

    _ = env
        .delete_local_ref(key_expr_str)
        .map_err(|err| tracing::error!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(selector_params_jstr)
        .map_err(|err| tracing::error!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(payload)
        .map_err(|err| tracing::error!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(attachment_bytes)
        .map_err(|err| tracing::error!("Error deleting local ref: {}", err));
    result
}
