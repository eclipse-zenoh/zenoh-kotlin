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
    objects::{JByteArray, JClass, JString},
    sys::{jboolean, jint, jlong},
    JNIEnv,
};
use uhlc::{Timestamp, ID, NTP64};
use zenoh::{
    core::Priority,
    key_expr::KeyExpr,
    prelude::Wait,
    publisher::CongestionControl,
    query::Query,
    sample::{QoSBuilderTrait, SampleBuilderTrait, TimestampBuilderTrait, ValueBuilderTrait},
};

use crate::utils::{decode_byte_array, decode_encoding};
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
