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
    objects::{GlobalRef, JByteArray, JClass, JPrimitiveArray, JString, JValue},
    sys::{jboolean, jint, jlong},
    JNIEnv,
};
use uhlc::{Timestamp, ID, NTP64};
use zenoh::{
    core::Priority,
    key_expr::KeyExpr,
    prelude::Wait,
    publisher::CongestionControl,
    query::{ConsolidationMode, Query, QueryTarget},
    sample::{QoSBuilderTrait, SampleBuilderTrait, TimestampBuilderTrait, ValueBuilderTrait},
};

use crate::utils::{bytes_to_java_array, decode_byte_array, decode_encoding, slice_to_java_string};
use crate::{
    errors::{Error, Result},
    key_expr::process_kotlin_key_expr,
    throw_exception,
};

/// Replies with success to a Zenoh query via JNI.
///
/// This function is meant to be called from Java/Kotlin through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh query.
/// - `key_expr_ptr`: The key expression pointer associated with the query result. This parameter
///    is meant to be used with declared key expressions, which have a pointer associated to them.
///    In case of it being null, then the `key_expr_string` will be used to perform the reply.
/// - `key_expr_string`: The string representation of the key expression associated with the query result.
/// - `payload`: The payload as a `JByteArray`.
/// - `encoding_id`: The encoding id of the payload.
/// - `encoding_schema`: Optional encoding schema, may be null.
/// - `sample_kind`: The kind of sample.
/// - `timestamp_enabled`: A boolean indicating whether the timestamp is enabled.
/// - `timestamp_ntp_64`: The NTP64 timestamp value.
/// - `attachment`: Optional user attachment encoded as a byte array. May be null.
///
/// Safety:
/// - This function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided raw pointer to the Zenoh query is valid and has not been modified or freed.
/// - The ownership of the Zenoh query is not transferred, and it remains valid after this call.
/// - May throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQuery_replySuccessViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    query_ptr: *const Query,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    payload: JByteArray,
    encoding_id: jint,
    encoding_schema: JString,
    timestamp_enabled: jboolean,
    timestamp_ntp_64: jlong,
    attachment: JByteArray,
    qos_express: jboolean,
    qos_priority: jint,
    qos_congestion_control: jint,
) {
    let _ = || -> Result<()> {
        let query = Arc::from_raw(query_ptr);
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let payload = decode_byte_array(&env, payload)?;
        let mut reply_builder = query.reply(key_expr, payload);
        let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
        reply_builder = reply_builder.encoding(encoding);
        if timestamp_enabled != 0 {
            let ts = Timestamp::new(NTP64(timestamp_ntp_64 as u64), ID::rand());
            reply_builder = reply_builder.timestamp(ts)
        }
        if !attachment.is_null() {
            reply_builder = reply_builder.attachment(decode_byte_array(&env, attachment)?);
        }
        reply_builder = reply_builder.express(qos_express != 0);
        reply_builder = reply_builder.priority(Priority::try_from(qos_priority as u8).unwrap()); // The numeric value is always within range.
        reply_builder = if qos_congestion_control != 0 {
            reply_builder.congestion_control(CongestionControl::Block)
        } else {
            reply_builder.congestion_control(CongestionControl::Drop)
        };
        reply_builder
            .wait()
            .map_err(|err| Error::Session(format!("{err}")))
    }()
    .map_err(|err| throw_exception!(env, err));
}

/// Replies with error to a Zenoh query via JNI.
///
/// This function is meant to be called from Java/Kotlin through JNI.
///
/// Support:
/// - Replying with error is a feature that is not yet supported by Zenoh. This implementation is
/// meant to prepare the API to these expected changes. Calling this function now would cause an
/// exception.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh query.
/// - `payload`: The payload as a `JByteArray`.
/// - `encoding_id`: The encoding id of the payload.
/// - `encoding_schema`: Optional encoding schema, may be null.
///
/// Safety:
/// - This function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided raw pointer to the Zenoh query is valid and has not been modified or freed.
/// - The ownership of the Zenoh query is not transferred, and it remains valid after this call.
/// - May throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQuery_replyErrorViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    ptr: *const Query,
    payload: JByteArray,
    encoding_id: jint,
    encoding_schema: JString,
) {
    let _ = || -> Result<()> {
        let query = Arc::from_raw(ptr);
        let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
        query
            .reply_err(decode_byte_array(&env, payload)?)
            .encoding(encoding)
            .wait()
            .map_err(|err| Error::Session(format!("{err}")))
    }()
    .map_err(|err| throw_exception!(env, err));
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQuery_replyDeleteViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    ptr: *const Query,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    timestamp_enabled: jboolean,
    timestamp_ntp_64: jlong,
    attachment: JByteArray,
    qos_express: jboolean,
    qos_priority: jint,
    qos_congestion_control: jint,
) {
    let _ = || -> Result<()> {
        let query = Arc::from_raw(ptr);
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let mut reply_builder = query.reply_del(key_expr);
        if timestamp_enabled != 0 {
            let ts = Timestamp::new(NTP64(timestamp_ntp_64 as u64), ID::rand());
            reply_builder = reply_builder.timestamp(ts)
        }
        if !attachment.is_null() {
            reply_builder = reply_builder.attachment(decode_byte_array(&env, attachment)?);
        }
        reply_builder = reply_builder.express(qos_express != 0);
        reply_builder = reply_builder.priority(Priority::try_from(qos_priority as u8).unwrap()); // The numeric value is always within range.
        reply_builder = if qos_congestion_control != 0 {
            reply_builder.congestion_control(CongestionControl::Block)
        } else {
            reply_builder.congestion_control(CongestionControl::Drop)
        };
        reply_builder
            .wait()
            .map_err(|err| Error::Session(format!("{err}")))
    }()
    .map_err(|err| throw_exception!(env, err));
}

/// Frees the memory associated with a Zenoh query raw pointer via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh query ([Query]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided query pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the query pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQuery_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const Query,
) {
    Arc::from_raw(ptr);
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
        (false, JPrimitiveArray::default(), 0, JString::default())
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

pub(crate) fn decode_query_target(target: jint) -> Result<QueryTarget> {
    match target {
        0 => Ok(QueryTarget::BestMatching),
        1 => Ok(QueryTarget::All),
        2 => Ok(QueryTarget::AllComplete),
        value => Err(Error::Session(format!(
            "Unable to decode QueryTarget {value}"
        ))),
    }
}

pub(crate) fn decode_consolidation(consolidation: jint) -> Result<ConsolidationMode> {
    match consolidation {
        0 => Ok(ConsolidationMode::None),
        1 => Ok(ConsolidationMode::Monotonic),
        2 => Ok(ConsolidationMode::Latest),
        value => Err(Error::Session(format!(
            "Unable to decode consolidation {value}"
        ))),
    }
}
