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

use crate::errors::{Error, Result};
use crate::key_expr::process_kotlin_key_expr;
use crate::query::{decode_consolidation, decode_query_target};
use crate::queryable::declare_queryable;
use crate::reply::on_reply;
use crate::subscriber::declare_subscriber;
use crate::throw_exception;
use crate::utils::{
    decode_byte_array, decode_congestion_control, decode_encoding, decode_priority, decode_string,
    get_callback_global_ref, get_java_vm, load_on_close,
};

use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;
use std::ops::Deref;
use std::ptr::null;
use std::sync::Arc;
use std::time::Duration;
use zenoh::config::Config;
use zenoh::key_expr::KeyExpr;
use zenoh::prelude::Wait;
use zenoh::publisher::Publisher;
use zenoh::sample::{QoSBuilderTrait, SampleBuilderTrait, ValueBuilderTrait};
use zenoh::selector::Selector;
use zenoh::session::{Session, SessionDeclarations};
use zenoh::value::Value;

/// Open a Zenoh session via JNI.
///
/// This function is meant to be called from Java/Kotlin through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class (parameter required by the JNI interface but unused).
/// - `config`: The path to the Zenoh config file.
///
/// Returns:
/// - An [Arc] raw pointer to the Zenoh Session, which should be stored as a private read-only attribute
///   of the session object in the Java/Kotlin code. Subsequent calls to other session functions will require
///   this raw pointer to retrieve the [Session] using `Arc::from_raw`.
///
/// If opening the session fails, a `SessionException` is thrown on the JVM, and a null pointer is returned.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNISession_openSessionViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    config_path: JString,
) -> *const Session {
    let session = open_session(&mut env, config_path);
    match session {
        Ok(session) => Arc::into_raw(Arc::new(session)),
        Err(err) => {
            tracing::error!("Unable to open session: {}", err);
            _ = Error::Session(err.to_string())
                .throw_on_jvm(&mut env)
                .map_err(|err| {
                    tracing::error!("Unable to throw exception on session failure: {}", err)
                });
            null()
        }
    }
}

/// Open a Zenoh session via JNI.
///
/// This function is meant to be called from Java/Kotlin through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class (parameter required by the JNI interface but unused).
/// - `config`: The path to the Zenoh config file.
///
/// Returns:
/// - An [Arc] raw pointer to the Zenoh Session, which should be stored as a private read-only attribute
///   of the session object in the Java/Kotlin code. Subsequent calls to other session functions will require
///   this raw pointer to retrieve the [Session] using `Arc::from_raw`.
///
/// If opening the session fails, a `SessionException` is thrown on the JVM, and a null pointer is returned.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNISession_openSessionWithJsonConfigViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    json_config: JString,
) -> *const Session {
    let session = open_session_with_json_config(&mut env, json_config);
    match session {
        Ok(session) => Arc::into_raw(Arc::new(session)),
        Err(err) => {
            tracing::error!("Unable to open session: {}", err);
            _ = Error::Session(err.to_string())
                .throw_on_jvm(&mut env)
                .map_err(|err| {
                    tracing::error!("Unable to throw exception on session failure: {}", err)
                });
            null()
        }
    }
}

/// Open a Zenoh session.
///
/// Loads the session with the provided by [config_path]. If the config path provided is empty then
/// the default configuration is loaded.
///
/// Returns:
/// - A [Result] with a [zenoh::Session] in case of success or an [Error::Session] in case of failure.
///
fn open_session(env: &mut JNIEnv, config_path: JString) -> Result<Session> {
    let config_file_path = decode_string(env, &config_path)?;
    let config = if config_file_path.is_empty() {
        Config::default()
    } else {
        Config::from_file(config_file_path).map_err(|err| Error::Session(err.to_string()))?
    };
    zenoh::open(config)
        .wait()
        .map_err(|err| Error::Session(err.to_string()))
}

/// Open a Zenoh session.
///
/// Loads the session with the provided by [config_path]. If the config path provided is empty then
/// the default configuration is loaded.
///
/// Returns:
/// - A [Result] with a [zenoh::Session] in case of success or an [Error::Session] in case of failure.
///
fn open_session_with_json_config(env: &mut JNIEnv, json_config: JString) -> Result<Session> {
    let json_config = decode_string(env, &json_config)?;
    let config = if json_config.is_empty() {
        Config::default()
    } else {
        let mut deserializer = match json5::Deserializer::from_str(&json_config) {
            Ok(deserializer) => Ok(deserializer),
            Err(err) => Err(Error::Session(err.to_string())),
        }?;
        Config::from_deserializer(&mut deserializer).map_err(|err| match err {
            Ok(c) => Error::Session(format!("Invalid configuration: {}", c)),
            Err(e) => Error::Session(format!("JSON error: {}", e)),
        })?
    };
    zenoh::open(config)
        .wait()
        .map_err(|err| Error::Session(err.to_string()))
}

/// Closes a Zenoh session via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh session.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The ownership of the session is not transferred, and the session pointer remains valid
///   after this function call, so it is safe to continue using the session.
/// - It is the responsibility of the caller to ensure that the session is not used after it has
///   been freed or dropped.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case, unused)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_closeSessionViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    ptr: *const Session,
) {
    let ptr = Arc::try_unwrap(Arc::from_raw(ptr));
    match ptr {
        Ok(session) => {
            // Do nothing, the pointer will be freed.
        }
        Err(arc_session) => {
            let ref_count = Arc::strong_count(&arc_session);
            tracing::error!("Unable to close the session.");
            _ = Error::Session(format!(
                "Attempted to close the session, but at least one strong reference to it is still alive
                (ref count: {}). All the declared publishers, subscribers, and queryables need to be
                dropped first.",
                ref_count
            ))
            .throw_on_jvm(&mut env)
            .map_err(|err| tracing::error!("Unable to throw exception on session failure: {}", err));
        }
    };
}

/// Declare a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the publisher.
/// - `key_expr_str`: String representation of the [KeyExpr] to be used for the publisher.
///     It is only considered when the key_expr_ptr parameter is null, meaning the function is
///     receiving a key expression that was not declared.
/// - `session_ptr`: Raw pointer to the Zenoh [Session] to be used for the publisher.
/// - `congestion_control`: The [zenoh::publisher::CongestionControl] configuration as an ordinal.
/// - `priority`: The [zenoh::core::Priority] configuration as an ordinal.
/// - `is_express`: The express config of the publisher (see [zenoh::prelude::QoSBuilderTrait]).
///
/// Returns:
/// - A raw pointer to the declared Zenoh publisher or null in case of failure.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The ownership of the session is not transferred, and the session pointer remains valid
///   after this function call so it is safe to use it after this call.
/// - The returned raw pointer should be stored appropriately and later freed using `Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI`.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declarePublisherViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    congestion_control: jint,
    priority: jint,
    is_express: jboolean,
) -> *const Publisher<'static> {
    || -> Result<*const Publisher<'static>> {
        let session = Arc::from_raw(session_ptr);
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let congestion_control = decode_congestion_control(congestion_control)?;
        let priority = decode_priority(priority)?;
        let result = session
            .declare_publisher(key_expr)
            .congestion_control(congestion_control)
            .priority(priority)
            .express(is_express != 0)
            .wait();
        std::mem::forget(session);
        match result {
            Ok(publisher) => Ok(Arc::into_raw(Arc::new(publisher))),
            Err(err) => Err(Error::Session(err.to_string())),
        }
    }()
    .unwrap_or_else(|err| {
        let _ = throw_exception!(env, err);
        null()
    })
}

/// Performs a `put` operation in the Zenoh session via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the operation.
/// - `key_expr_str`: String representation of the [KeyExpr] to be used for the operation.
///     It is only considered when the key_expr_ptr parameter is null, meaning the function is
///     receiving a key expression that was not declared.
/// - `session_ptr`: Raw pointer to the [Session] to be used for the operation.
/// - `payload`: The payload to send through the network.
/// - `encoding_id`: The encoding id of the payload.
/// - `encoding_schema`: Optional encoding schema, may be null.
/// - `congestion_control`: The [CongestionControl] mechanism specified.
/// - `priority`: The [Priority] mechanism specified.
/// - `is_express`: The express flag.
/// - `attachment`: Optional attachment encoded into a byte array. May be null.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The function may throw a JNI exception or a Session exception in case of failure, which
///   should be handled by the Java/Kotlin caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_putViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    payload: JByteArray,
    encoding_id: jint,
    encoding_schema: JString,
    congestion_control: jint,
    priority: jint,
    is_express: jboolean,
    attachment: JByteArray,
) {
    let session = Arc::from_raw(session_ptr);
    let _ = || -> Result<()> {
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let payload = decode_byte_array(&env, payload)?;
        let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
        let congestion_control = decode_congestion_control(congestion_control)?;
        let priority = decode_priority(priority)?;

        let mut put_builder = session
            .put(&key_expr, payload)
            .congestion_control(congestion_control)
            .encoding(encoding)
            .express(is_express != 0)
            .priority(priority);

        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            put_builder = put_builder.attachment(attachment)
        }

        put_builder
            .wait()
            .map(|_| tracing::trace!("Put on '{key_expr}'"))
            .map_err(|err| Error::Session(format!("{err}")))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

/// Performs a `delete` operation in the Zenoh session via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the operation.
/// - `key_expr_str`: String representation of the [KeyExpr] to be used for the operation.
///     It is only considered when the key_expr_ptr parameter is null, meaning the function is
///     receiving a key expression that was not declared.
/// - `session_ptr`: Raw pointer to the [Session] to be used for the operation.
/// - `congestion_control`: The [CongestionControl] mechanism specified.
/// - `priority`: The [Priority] mechanism specified.
/// - `is_express`: The express flag.
/// - `attachment`: Optional attachment encoded into a byte array. May be null.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The function may throw a JNI exception or a Session exception in case of failure, which
///   should be handled by the Java/Kotlin caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_deleteViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    congestion_control: jint,
    priority: jint,
    is_express: jboolean,
    attachment: JByteArray,
) {
    let session = Arc::from_raw(session_ptr);
    let _ = || -> Result<()> {
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let congestion_control = decode_congestion_control(congestion_control)?;
        let priority = decode_priority(priority)?;

        let mut delete_builder = session
            .delete(&key_expr)
            .congestion_control(congestion_control)
            .express(is_express != 0)
            .priority(priority);

        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            delete_builder = delete_builder.attachment(attachment)
        }

        delete_builder
            .wait()
            .map(|_| tracing::trace!("Delete on '{key_expr}'"))
            .map_err(|err| Error::Session(format!("{err}")))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

/// Declare a Zenoh subscriber via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: The key expression pointer for the subscriber. May be null in case of using an
///     undeclared key expression.
/// - `key_expr_str`: String representation of the key expression to be used to declare the subscriber.
///     It won't be considered in case a key_expr_ptr to a declared key expression is provided.
/// - `session_ptr`: The raw pointer to the Zenoh session.
/// - `callback`: The callback function as an instance of the `JNISubscriberCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon closing the subscriber.
/// - `reliability`: The [Reliability] value as an ordinal.
///
/// Returns:
/// - A raw pointer to the declared Zenoh subscriber or null in case of failure.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNISubscriberCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareSubscriberViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    reliability: jint,
) -> *const zenoh::subscriber::Subscriber<'static, ()> {
    match declare_subscriber(
        &mut env,
        key_expr_ptr,
        key_expr_str,
        session_ptr,
        callback,
        on_close,
        reliability,
    ) {
        Ok(subscriber_ptr) => subscriber_ptr,
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!(
                    "Unable to throw exception on subscriber declaration failure: {}",
                    err
                )
            });
            null()
        }
    }
}

/// Declare a Zenoh queryable via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: A raw pointer to the [KeyExpr] to be used for the queryable. May be null in case of using an
///     undeclared key expression.
/// - `key_expr_str`: String representation of the key expression to be used to declare the queryable.
///     It won't be considered in case a key_expr_ptr to a declared key expression is provided.
/// - `session_ptr`: A raw pointer to the Zenoh [Session] to be used to declare the queryable.
/// - `callback`: The callback function as an instance of the `JNIQueryableCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon closing the queryable.
/// - `complete`: The completeness of the queryable.
///
/// Returns:
/// - A raw pointer to the declared Zenoh queryable or null in case of failure.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNIQueryableCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareQueryableViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    complete: jboolean,
) -> *const zenoh::queryable::Queryable<'static, ()> {
    match declare_queryable(
        &mut env,
        key_expr_ptr,
        key_expr_str,
        session_ptr,
        callback,
        on_close,
        complete,
    ) {
        Ok(queryable) => Arc::into_raw(Arc::new(queryable)),
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!(
                    "Unable to throw exception on query declaration failure. {}",
                    err
                )
            });
            null()
        }
    }
}

/// Declare a [KeyExpr] through a [Session] via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `session_ptr`: A raw pointer to the Zenoh [Session] from which to declare the key expression.
/// - `key_expr_str`: A Java String with the intended key expression.
///
/// Returns:
/// - A raw pointer to the declared key expression or null in case of failure.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareKeyExprViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    key_expr_str: JString,
) -> *const KeyExpr<'static> {
    match declare_keyexpr(&mut env, session_ptr, key_expr_str) {
        Ok(key_expr) => Arc::into_raw(Arc::new(key_expr)),
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!(
                    "Unable to throw exception on key expr declaration failure. {}",
                    err
                )
            });
            null()
        }
    }
}

/// Undeclare a [KeyExpr] through a [Session] via JNI.
///
/// The key expression must have been previously declared on the specified session, otherwise an
/// error is thrown and propagated to the caller.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `session_ptr`: A raw pointer to the Zenoh [Session] from which to undeclare the key expression.
/// - `key_expr_ptr`: A raw pointer to the [KeyExpr] to undeclare.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session and keyexpr pointers are valid and have not been modified or freed.
/// - Both session pointer and key expression pointers will remain valid.
///   Their ownership is not transferred, allowing safe usage of the session and the key expression after this function call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_undeclareKeyExprViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    key_expr_ptr: *const KeyExpr<'static>,
) {
    let session = Arc::from_raw(session_ptr);
    let key_expr = Arc::from_raw(key_expr_ptr);
    let key_expr_clone = key_expr.deref().clone();
    match session.undeclare(key_expr_clone).wait() {
        Ok(_) => {}
        Err(err) => {
            _ = Error::Session(format!(
                "Unable to declare key expression {key_expr}: {}",
                err
            ))
            .throw_on_jvm(&mut env)
        }
    }
    std::mem::forget(session);
    std::mem::forget(key_expr);
}

/// Performs a `get` operation in the Zenoh session via JNI with Value.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to a declared [KeyExpr] to be used for the query. May be null in case
///     of using a non declared key expression, in which case the key_expr_str parameter will be used instead.
/// - `key_expr_str`: String representation of the key expression to be used to declare the query. It is not
///     considered if a key_expr_ptr is provided.
/// - `selector_params`: Parameters of the selector.
/// - `session_ptr`: A raw pointer to the Zenoh [Session].
/// - `callback`: A Java/Kotlin callback to be called upon receiving a reply.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called when no more replies will be received.
/// - `timeout_ms`: The timeout in milliseconds.
/// - `target`: The [QueryTarget] as the ordinal of the enum.
/// - `consolidation`: The [ConsolidationMode] as the ordinal of the enum.
/// - `payload`: The payload of the [Value]
/// - `encoding`: The [Encoding] as the ordinal of the enum.
/// - `attachment`: An optional attachment encoded into a byte array.
/// - `with_value`: Boolean value to tell if a value must be included in the get operation. If true,
///     then the next params are valid.
/// - `value_payload`: The payload of the value (if present, otherwise it'll be null).
/// - `encoding_id`: The encoding of the value payload.
/// - `encoding_schema`: The encoding schema of the value payload, may be null.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The session pointer remains valid and the ownership of the session is not transferred,
///   allowing safe usage of the session after this function call.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
/// Throws:
/// - An exception in case of failure handling the query.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_getViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    selector_params: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    timeout_ms: jlong,
    target: jint,
    consolidation: jint,
    attachment: JByteArray,
    with_value: jboolean,
    value_payload: JByteArray,
    encoding_id: jint,
    encoding_schema: JString,
) {
    let session = Arc::from_raw(session_ptr);
    let _ = || -> Result<()> {
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let query_target = decode_query_target(target)?;
        let consolidation = decode_consolidation(consolidation)?;
        let selector_params = decode_string(&mut env, &selector_params)?;
        let timeout = Duration::from_millis(timeout_ms as u64);
        let on_close = load_on_close(&java_vm, on_close_global_ref);
        let selector = Selector::new(&key_expr, &*selector_params);
        let mut get_builder = session
            .get(selector)
            .callback(move |reply| {
                on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
                tracing::debug!("Receiving reply through JNI: {:?}", reply);
                let env = match java_vm.attach_current_thread_as_daemon() {
                    Ok(env) => env,
                    Err(err) => {
                        tracing::error!("Unable to attach thread for GET query callback: {}", err);
                        return;
                    }
                };
                match on_reply(env, &reply, &callback_global_ref) {
                    Ok(_) => {}
                    Err(err) => tracing::error!("{}", err),
                }
            })
            .target(query_target)
            .timeout(timeout)
            .consolidation(consolidation);

        if with_value != 0 {
            let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
            let value = Value::new(decode_byte_array(&env, value_payload)?, encoding);
            get_builder = get_builder.value(value);
        }

        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            get_builder = get_builder.attachment::<Vec<u8>>(attachment);
        }

        get_builder
            .wait()
            .map(|_| tracing::trace!("Performing get on '{key_expr}'.",))
            .map_err(|err| Error::Session(err.to_string()))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

pub(crate) unsafe fn declare_keyexpr(
    env: &mut JNIEnv,
    session_ptr: *const Session,
    key_expr: JString,
) -> Result<KeyExpr<'static>> {
    let key_expr = decode_string(env, &key_expr)?;
    let session: Arc<Session> = Arc::from_raw(session_ptr);
    let result = session.declare_keyexpr(key_expr.to_owned()).wait();
    std::mem::forget(session);

    match result {
        Ok(key_expr) => Ok(key_expr),
        Err(err) => Err(Error::Session(format!(
            "Unable to declare key expression {key_expr}: {}",
            err
        ))),
    }
}
