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

use std::{mem, sync::Arc};

use jni::{
    objects::{GlobalRef, JByteArray, JClass, JPrimitiveArray, JString, JValue},
    sys::{jboolean, jbyte, jint, jlong},
    JNIEnv,
};
use zenoh::{
    prelude::{sync::SyncResolve, KeyExpr, SplitBuffer},
    query::{ConsolidationMode, QueryTarget},
    queryable::Query,
    sample::{Attachment, Sample},
    value::Value,
};

use crate::{
    errors::{Error, Result},
    key_expr::process_kotlin_key_expr,
    utils::attachment_to_vec,
    value::decode_value,
};
use crate::{
    sample::decode_sample,
    utils::{decode_byte_array, vec_to_attachment},
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
/// - `encoding`: The encoding of the payload.
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
    query_ptr: *const zenoh::queryable::Query,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    payload: JByteArray,
    encoding: jint,
    sample_kind: jint,
    timestamp_enabled: jboolean,
    timestamp_ntp_64: jlong,
    qos: jbyte,
    attachment: JByteArray,
) {
    let key_expr = match process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            if let Err(err) = err.throw_on_jvm(&mut env) {
                tracing::error!("{}", err);
            }
            return;
        }
    };

    let sample = match decode_sample(
        &mut env,
        key_expr,
        payload,
        encoding,
        sample_kind,
        timestamp_enabled,
        timestamp_ntp_64,
        qos,
    ) {
        Ok(sample) => sample,
        Err(err) => {
            if let Err(err) = err.throw_on_jvm(&mut env) {
                tracing::error!("Unable to throw exception on query reply failure: {}", err);
            }
            return;
        }
    };

    let attachment = if !attachment.is_null() {
        match decode_byte_array(&env, attachment) {
            Ok(attachment_bytes) => Some(vec_to_attachment(attachment_bytes)),
            Err(err) => {
                if let Err(err) = err.throw_on_jvm(&mut env) {
                    tracing::error!("Unable to throw exception on query reply failure: {}", err);
                }
                return;
            }
        }
    } else {
        None
    };

    let query = Arc::from_raw(query_ptr);
    query_reply(env, &query, Ok(sample), attachment);
    mem::forget(query);
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
/// - `key_expr`: The key expression associated with the query result.
/// - `payload`: The payload as a `JByteArray`.
/// - `encoding`: The encoding of the payload as a jint.
/// - `attachment`: The user attachment bytes.
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
    ptr: *const zenoh::queryable::Query,
    payload: JByteArray,
    encoding: jint,
    attachment: JByteArray,
) {
    let errorValue = match decode_value(&env, payload, encoding) {
        Ok(value) => value,
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!("Unable to throw exception on query reply failure. {}", err)
            });
            return;
        }
    };
    let attachment: Option<Attachment> = if !attachment.is_null() {
        match decode_byte_array(&env, attachment) {
            Ok(attachment_bytes) => Some(vec_to_attachment(attachment_bytes)),
            Err(err) => {
                _ = err.throw_on_jvm(&mut env).map_err(|err| {
                    tracing::error!("Unable to throw exception on query reply failure. {}", err)
                });
                return;
            }
        }
    } else {
        None
    };

    let query = Arc::from_raw(ptr);
    query_reply(env, &query, Err(errorValue), attachment);
    mem::forget(query)
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
    ptr: *const zenoh::queryable::Query,
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

    let (with_value, payload, encoding) = if let Some(value) = value {
        let byte_array = env
            .byte_array_from_slice(value.payload.contiguous().as_ref())
            .map_err(|err| Error::Jni(err.to_string()))?;
        let encoding: i32 = value.encoding.prefix().to_owned() as i32;
        (true, byte_array, encoding)
    } else {
        (false, JPrimitiveArray::default(), 0)
    };

    let attachment_bytes = query
        .attachment()
        .map_or_else(
            || env.byte_array_from_slice(&[]),
            |attachment| {
                env.byte_array_from_slice(attachment_to_vec(attachment.clone()).as_slice())
            },
        )
        .map_err(|err| Error::Jni(format!("Error processing attachment of reply: {}.", err)))?;

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
            "(Ljava/lang/String;Ljava/lang/String;Z[BI[BJ)V",
            &[
                JValue::from(&key_expr_str),
                JValue::from(&selector_params_jstr),
                JValue::from(with_value),
                JValue::from(&payload),
                JValue::from(encoding),
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

/// Helper function to perform a reply to a query.
fn query_reply(
    mut env: JNIEnv,
    query: &Arc<Query>,
    reply: core::result::Result<Sample, Value>,
    attachment: Option<Attachment>,
) {
    let result = if let Some(attachment) = attachment {
        query
            .reply(reply)
            .with_attachment(attachment)
            .unwrap_or_else(|(builder, _)| {
                tracing::warn!("Unable to append attachment to query reply");
                builder
            })
            .res()
            .map_err(|err| Error::Session(err.to_string()))
    } else {
        query
            .reply(reply)
            .res()
            .map_err(|err| Error::Session(err.to_string()))
    };
    match result {
        Ok(_) => {}
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!("Unable to throw exception on query reply failure. {}", err)
            });
        }
    }
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
