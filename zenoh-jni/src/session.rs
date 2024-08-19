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
use crate::{jni_error, utils::*};
use crate::{session_error, throw_exception};

use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;
use std::mem;
use std::ops::Deref;
use std::ptr::null;
use std::sync::Arc;
use std::time::Duration;
use zenoh::config::{Config, ZenohId};
use zenoh::key_expr::KeyExpr;
use zenoh::prelude::{EncodingBuilderTrait, Wait};
use zenoh::pubsub::{Publisher, Subscriber};
use zenoh::qos::QoSBuilderTrait;
use zenoh::query::{Query, Queryable, ReplyError, Selector};
use zenoh::sample::{Sample, SampleBuilderTrait};
use zenoh::session::{Session, SessionDeclarations};

/// Open a Zenoh session via JNI.
///
/// It returns an [Arc] raw pointer to the Zenoh Session, which should be stored as a private read-only attribute
/// of the session object in the Java/Kotlin code. Subsequent calls to other session functions will require
/// this raw pointer to retrieve the [Session] using `Arc::from_raw`.
///
/// If opening the session fails, an exception is thrown on the JVM, and a null pointer is returned.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class (parameter required by the JNI interface but unused).
/// - `config_path`: Nullable path to the Zenoh config file. If null, the default configuration will be loaded.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNISession_openSessionViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    config_path: /*nullable*/ JString,
) -> *const Session {
    let session = open_session(&mut env, config_path);
    match session {
        Ok(session) => Arc::into_raw(Arc::new(session)),
        Err(err) => {
            tracing::error!("Unable to open session: {}", err);
            throw_exception!(env, session_error!(err));
            null()
        }
    }
}

/// Open a Zenoh session with the configuration pointed out by `config_path`.
///
/// If the config path provided is null then the default configuration is loaded.
///
fn open_session(env: &mut JNIEnv, config_path: JString) -> Result<Session> {
    let config = if config_path.is_null() {
        Config::default()
    } else {
        let config_file_path = decode_string(env, &config_path)?;
        Config::from_file(config_file_path).map_err(|err| session_error!(err))?
    };
    zenoh::open(config)
        .wait()
        .map_err(|err| session_error!(err))
}

/// Open a Zenoh session with a JSON configuration.
///
/// It returns an [Arc] raw pointer to the Zenoh Session, which should be stored as a private read-only attribute
/// of the session object in the Java/Kotlin code. Subsequent calls to other session functions will require
/// this raw pointer to retrieve the [Session] using `Arc::from_raw`.
///
/// If opening the session fails, an exception is thrown on the JVM, and a null pointer is returned.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class (parameter required by the JNI interface but unused).
/// - `json_config`: Nullable configuration as a JSON string. If null, the default configuration will be loaded.
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
            throw_exception!(env, session_error!(err));
            null()
        }
    }
}

/// Open a Zenoh session with the provided json configuration.
///
/// If the provided json config is null, then the default config is loaded.
///
fn open_session_with_json_config(env: &mut JNIEnv, json_config: JString) -> Result<Session> {
    let config = if json_config.is_null() {
        Config::default()
    } else {
        let json_config = decode_string(env, &json_config)?;
        let mut deserializer =
            json5::Deserializer::from_str(&json_config).map_err(|err| session_error!(err))?;
        Config::from_deserializer(&mut deserializer).map_err(|err| match err {
            Ok(c) => session_error!("Invalid configuration: {}", c),
            Err(e) => session_error!("JSON error: {}", e),
        })?
    };
    zenoh::open(config)
        .wait()
        .map_err(|err| session_error!(err))
}

/// Closes a Zenoh session via JNI.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `session_ptr`: The raw pointer to the Zenoh session.
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
/// - After the session is closed, the provided pointer is no more valid.
///
#[no_mangle]
#[allow(non_snake_case, unused)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_closeSessionViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
) {
    let ptr = Arc::try_unwrap(Arc::from_raw(session_ptr));
    match ptr {
        Ok(session) => {
            // Do nothing, the pointer will be freed.
        }
        Err(arc_session) => {
            let ref_count = Arc::strong_count(&arc_session);
            throw_exception!(env, session_error!(
                "Attempted to close the session, but at least one strong reference to it is still alive
                (ref count: {}). All the declared publishers, subscribers, and queryables need to be
                dropped first.",
                ref_count
            ));
        }
    };
}

/// Declare a Zenoh publisher via JNI.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the publisher, may be null.
/// - `key_expr_str`: String representation of the [KeyExpr] to be used for the publisher.
///     It is only considered when the key_expr_ptr parameter is null, meaning the function is
///     receiving a key expression that was not declared.
/// - `session_ptr`: Raw pointer to the Zenoh [Session] to be used for the publisher.
/// - `congestion_control`: The [zenoh::publisher::CongestionControl] configuration as an ordinal.
/// - `priority`: The [zenoh::core::Priority] configuration as an ordinal.
/// - `is_express`: The express config of the publisher (see [zenoh::prelude::QoSBuilderTrait]).
///
/// # Returns:
/// - A raw pointer to the declared Zenoh publisher or null in case of failure.
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session pointer is valid and has not been modified or freed.
/// - The ownership of the session is not transferred, and the session pointer remains valid
///   after this function call so it is safe to use it after this call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declarePublisherViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    congestion_control: jint,
    priority: jint,
    is_express: jboolean,
) -> *const Publisher<'static> {
    let session = Arc::from_raw(session_ptr);
    let publisher_ptr = || -> Result<*const Publisher<'static>> {
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let congestion_control = decode_congestion_control(congestion_control)?;
        let priority = decode_priority(priority)?;
        let result = session
            .declare_publisher(key_expr)
            .congestion_control(congestion_control)
            .priority(priority)
            .express(is_express != 0)
            .wait();
        match result {
            Ok(publisher) => Ok(Arc::into_raw(Arc::new(publisher))),
            Err(err) => Err(session_error!(err)),
        }
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    });
    std::mem::forget(session);
    publisher_ptr
}

/// Performs a `put` operation in the Zenoh session via JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the operation, may be null.
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
/// - The function may throw an exception in case of failure, which should be handled by the Java/Kotlin caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_putViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
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
            .map_err(|err| session_error!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

/// Performs a `delete` operation in the Zenoh session via JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the operation, may be null.
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
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
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
            .map_err(|err| session_error!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

/// Declare a Zenoh subscriber via JNI.
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
/// - `reliability`: The reliability value as an ordinal.
///
/// Returns:
/// - A raw pointer to the declared Zenoh subscriber. In case of failure, an exception is thrown and null is returned.
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
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    reliability: jint,
) -> *const Subscriber<'static, ()> {
    let session = Arc::from_raw(session_ptr);
    || -> Result<*const Subscriber<'static, ()>> {
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let reliability = decode_reliability(reliability)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);

        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        tracing::debug!("Declaring subscriber on '{}'...", key_expr);

        let result = session
            .declare_subscriber(key_expr.to_owned())
            .callback(move |sample| {
                on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
                let _ = || -> Result<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        jni_error!("Unable to attach thread for subscriber: {}", err)
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
                        .map_err(|err| jni_error!("Error processing attachment: {}", err))?;

                    let key_expr_str =
                        env.auto_local(env.new_string(sample.key_expr().to_string()).map_err(
                            |err| jni_error!("Error processing sample key expr: {}", err),
                        )?);

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
                    .map_err(|err| jni_error!(err))?;
                    Ok(())
                }()
                .map_err(|err| tracing::error!("On subscriber callback error: {err}"));
            })
            .reliability(reliability)
            .wait();

        let subscriber =
            result.map_err(|err| session_error!("Unable to declare subscriber: {}", err))?;

        tracing::debug!(
            "Subscriber declared on '{}' with reliability '{:?}'.",
            key_expr,
            reliability
        );
        std::mem::forget(session);
        Ok(Arc::into_raw(Arc::new(subscriber)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
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
/// - A raw pointer to the declared Zenoh queryable. In case of failure, an exception is thrown and null is returned.
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
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    complete: jboolean,
) -> *const Queryable<'static, ()> {
    let session = Arc::from_raw(session_ptr);
    let query_ptr = || -> Result<*const Queryable<'static, ()>> {
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let complete = complete != 0;
        let on_close = load_on_close(&java_vm, on_close_global_ref);
        tracing::debug!("Declaring queryable through JNI on {}", key_expr);
        let builder = session
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

        let queryable = builder
            .wait()
            .map_err(|err| session_error!("Error declaring queryable: {}", err))?;
        Ok(Arc::into_raw(Arc::new(queryable)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    });
    std::mem::forget(session);
    query_ptr
}

fn on_query(mut env: JNIEnv, query: Query, callback_global_ref: &GlobalRef) -> Result<()> {
    let selector_params_jstr = env
        .new_string(query.parameters().to_string())
        .map(|value| env.auto_local(value))
        .map_err(|err| {
            jni_error!(
                "Could not create a JString through JNI for the Query key expression. {}",
                err
            )
        })?;

    let (with_value, payload, encoding_id, encoding_schema) = if let Some(payload) = query.payload()
    {
        let encoding = query.encoding().unwrap(); //If there is payload, there is encoding.
        let encoding_id = encoding.id() as jint;
        let encoding_schema = encoding
            .schema()
            .map_or_else(
                || Ok(JString::default()),
                |schema| slice_to_java_string(&env, schema),
            )
            .map(|value| env.auto_local(value))?;
        let byte_array = bytes_to_java_array(&env, payload).map(|value| env.auto_local(value))?;
        (true, byte_array, encoding_id, encoding_schema)
    } else {
        (
            false,
            env.auto_local(JByteArray::default()),
            0,
            env.auto_local(JString::default()),
        )
    };

    let attachment_bytes = query
        .attachment()
        .map_or_else(
            || Ok(JByteArray::default()),
            |attachment| bytes_to_java_array(&env, attachment),
        )
        .map(|value| env.auto_local(value))
        .map_err(|err| jni_error!("Error processing attachment of reply: {}.", err))?;

    let key_expr_str = env
        .new_string(&query.key_expr().to_string())
        .map(|key_expr| env.auto_local(key_expr))
        .map_err(|err| {
            jni_error!(
                "Could not create a JString through JNI for the Query key expression: {}.",
                err
            )
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
            jni_error!(err)
        });
    result
}

/// Declare a [KeyExpr] through a [Session] via JNI.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `session_ptr`: A raw pointer to the Zenoh [Session] from which to declare the key expression.
/// - `key_expr_str`: A Java String with the intended key expression.
///
/// # Returns:
/// - A raw pointer to the declared key expression. In case of failure, an exception is thrown and null is returned.
///
/// # Safety:
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
    let session: Arc<Session> = Arc::from_raw(session_ptr);
    let key_expr_ptr = || -> Result<*const KeyExpr<'static>> {
        let key_expr_str = decode_string(&mut env, &key_expr_str)?;
        let key_expr = session
            .declare_keyexpr(key_expr_str.to_owned())
            .wait()
            .map_err(|err| {
                session_error!(
                    "Unable to declare key expression '{}': {}",
                    key_expr_str,
                    err
                )
            })?;
        Ok(Arc::into_raw(Arc::new(key_expr)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    });
    mem::forget(session);
    key_expr_ptr
}

/// Undeclare a [KeyExpr] through a [Session] via JNI.
///
/// The key expression must have been previously declared on the specified session, otherwise an
/// exception is thrown.
///
/// This functions frees the key expression pointer provided.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `session_ptr`: A raw pointer to the Zenoh [Session] from which to undeclare the key expression.
/// - `key_expr_ptr`: A raw pointer to the [KeyExpr] to undeclare.
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided session and keyexpr pointers are valid and have not been modified or freed.
/// - The session pointer remains valid after this function call.
/// - The key expression pointer is voided after this function call.
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
            throw_exception!(
                env,
                session_error!("Unable to declare key expression '{}': {}", key_expr, err)
            );
        }
    }
    std::mem::forget(session);
    // `key_expr` is intentionally left to be freed by Rust
}

/// Performs a `get` operation in the Zenoh session via JNI with Value.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `key_expr_ptr`: Raw pointer to a declared [KeyExpr] to be used for the query. May be null in case
///     of using a non declared key expression, in which case the `key_expr_str` parameter will be used instead.
/// - `key_expr_str`: String representation of the key expression to be used to declare the query. It is not
///     considered if a `key_expr_ptr` is provided.
/// - `selector_params`: Parameters of the selector.
/// - `session_ptr`: A raw pointer to the Zenoh [Session].
/// - `callback`: A Java/Kotlin callback to be called upon receiving a reply.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called when no more replies will be received.
/// - `timeout_ms`: The timeout in milliseconds.
/// - `target`: The query target as the ordinal of the enum.
/// - `consolidation`: The consolidation mode as the ordinal of the enum.
/// - `attachment`: An optional attachment encoded into a byte array.
/// - `with_value`: Boolean value to tell if a value must be included in the get operation. If true,
///     then the next params are valid.
/// - `payload`: The payload of the value.
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
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    selector_params: JString,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    timeout_ms: jlong,
    target: jint,
    consolidation: jint,
    attachment: /*nullable*/ JByteArray,
    with_value: jboolean,
    payload: /*nullable*/ JByteArray,
    encoding_id: jint,
    encoding_schema: /*nullable*/ JString,
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
        let selector = Selector::owned(&key_expr, &*selector_params);
        let mut get_builder = session
            .get(selector)
            .callback(move |reply| {
                || -> Result<()> {
                    on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
                    tracing::debug!("Receiving reply through JNI: {:?}", reply);
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        jni_error!("Unable to attach thread for GET query callback: {}.", err)
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
                .unwrap_or_else(|err| tracing::error!("Error on get callback: {err}"));
            })
            .target(query_target)
            .timeout(timeout)
            .consolidation(consolidation);

        if with_value != 0 {
            let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
            get_builder = get_builder.encoding(encoding);
            get_builder = get_builder.payload(decode_byte_array(&env, payload)?);
        }

        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            get_builder = get_builder.attachment::<Vec<u8>>(attachment);
        }

        get_builder
            .wait()
            .map(|_| tracing::trace!("Performing get on '{key_expr}'.",))
            .map_err(|err| session_error!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(session);
}

fn on_reply_success(
    env: &mut JNIEnv,
    replier_id: Option<ZenohId>,
    sample: &Sample,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = replier_id
        .map_or_else(
            || Ok(JString::default()),
            |replier_id| {
                env.new_string(replier_id.to_string())
                    .map_err(|err| jni_error!(err))
            },
        )
        .map(|value| env.auto_local(value))?;

    let byte_array =
        bytes_to_java_array(env, sample.payload()).map(|value| env.auto_local(value))?;
    let encoding: jint = sample.encoding().id() as jint;
    let encoding_schema = sample
        .encoding()
        .schema()
        .map_or_else(
            || Ok(JString::default()),
            |schema| slice_to_java_string(env, schema),
        )
        .map(|value| env.auto_local(value))?;
    let kind = sample.kind() as jint;

    let (timestamp, is_valid) = sample
        .timestamp()
        .map(|timestamp| (timestamp.get_time().as_u64(), true))
        .unwrap_or((0, false));

    let attachment_bytes = sample
        .attachment()
        .map_or_else(
            || Ok(JByteArray::default()),
            |attachment| bytes_to_java_array(env, attachment),
        )
        .map(|value| env.auto_local(value))
        .map_err(|err| jni_error!("Error processing attachment of reply: {}.", err))?;

    let key_expr_str = env
        .new_string(sample.key_expr().to_string())
        .map(|value| env.auto_local(value))
        .map_err(|err| {
            jni_error!(
                "Could not create a JString through JNI for the Sample key expression. {}",
                err
            )
        })?;

    let express = sample.express();
    let priority = sample.priority() as jint;
    let cc = sample.congestion_control() as jint;

    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZLjava/lang/String;[BILjava/lang/String;IJZ[BZII)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(true),
            JValue::from(&key_expr_str),
            JValue::from(&byte_array),
            JValue::from(encoding),
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
        Ok(_) => Ok(()),
        Err(err) => {
            _ = env.exception_describe();
            Err(jni_error!("On GET callback error: {}", err))
        }
    };
    result
}

fn on_reply_error(
    env: &mut JNIEnv,
    replier_id: Option<ZenohId>,
    reply_error: &ReplyError,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = replier_id
        .map_or_else(
            || Ok(JString::default()),
            |replier_id| {
                env.new_string(replier_id.to_string())
                    .map_err(|err| jni_error!(err))
            },
        )
        .map(|value| env.auto_local(value))?;

    let payload =
        bytes_to_java_array(env, reply_error.payload()).map(|value| env.auto_local(value))?;
    let encoding_id: jint = reply_error.encoding().id() as jint;
    let encoding_schema = reply_error
        .encoding()
        .schema()
        .map_or_else(
            || Ok(JString::default()),
            |schema| slice_to_java_string(env, schema),
        )
        .map(|value| env.auto_local(value))?;
    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZLjava/lang/String;[BILjava/lang/String;IJZ[BZII)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(false),
            JValue::from(&JString::default()),
            JValue::from(&payload),
            JValue::from(encoding_id),
            JValue::from(&encoding_schema),
            // The remaining parameters aren't used in case of replying error, so we set them to default.
            JValue::from(0 as jint),
            JValue::from(0_i64),
            JValue::from(false),
            JValue::from(&JByteArray::default()),
            JValue::from(false),
            JValue::from(0 as jint),
            JValue::from(0 as jint),
        ],
    ) {
        Ok(_) => Ok(()),
        Err(err) => {
            _ = env.exception_describe();
            Err(jni_error!("On GET callback error: {}", err))
        }
    };
    result
}
