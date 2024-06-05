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

use jni::{
    objects::{GlobalRef, JByteArray, JObject, JString, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{
    config::ZenohId, internal::EncodingInternals, query::Reply, sample::Sample, value::Value,
};

use crate::errors::Error;
use crate::errors::Result;

pub(crate) fn on_reply(
    mut env: JNIEnv,
    reply: &Reply,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    match reply.result() {
        Ok(sample) => on_reply_success(&mut env, reply.replier_id(), sample, callback_global_ref),
        Err(value) => on_reply_error(&mut env, reply.replier_id(), value, callback_global_ref),
    }
}

fn on_reply_success(
    env: &mut JNIEnv,
    replier_id: ZenohId,
    sample: &Sample,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = env
        .new_string(replier_id.to_string())
        .map_err(|err| Error::Jni(err.to_string()))?;

    let byte_array = env
        .byte_array_from_slice(sample.payload().deserialize::<Vec<u8>>().unwrap().as_ref()) // TODO: remove unwrap
        .map_err(|err| Error::Jni(err.to_string()))?;

    let encoding: jint = sample.encoding().id() as jint;
    let encoding_schema = match sample.encoding().schema() {
        Some(schema) => env
            .new_string(String::from_utf8(schema.to_vec()).unwrap())
            .unwrap(), // TODO: remove unwraps
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
            || env.byte_array_from_slice(&[]),
            |attachment| {
                env.byte_array_from_slice(attachment.deserialize::<Vec<u8>>().unwrap().as_ref())
            }, // TODO: provide JByteArray::default() instead of empty byte array.
        )
        .map_err(|err| Error::Jni(format!("Error processing attachment of reply: {}.", err)))?;

    let key_expr_str = env
        .new_string(sample.key_expr().to_string())
        .map_err(|err| {
            Error::Jni(format!(
                "Could not create a JString through JNI for the Sample key expression. {}",
                err
            ))
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
            Err(Error::Jni(format!("On GET callback error: {}", err)))
        }
    };

    _ = env
        .delete_local_ref(key_expr_str)
        .map_err(|err| tracing::error!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(zenoh_id)
        .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(byte_array)
        .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(attachment_bytes)
        .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
    result
}

fn on_reply_error(
    env: &mut JNIEnv,
    replier_id: ZenohId,
    value: &Value,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = env
        .new_string(replier_id.to_string())
        .map_err(|err| Error::Jni(err.to_string()))?;

    let byte_array = env
        .byte_array_from_slice(value.payload().deserialize::<Vec<u8>>().unwrap().as_ref()) // TODO: remove unwrap
        .map_err(|err| Error::Jni(err.to_string()))?;
    let encoding: jint = value.encoding().id() as jint;
    let encoding_schema = match value.encoding().schema() {
        Some(schema) => env
            .new_string(String::from_utf8(schema.to_vec()).unwrap())
            .unwrap(), // TODO: remove unwraps
        None => JString::default(),
    };
    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZLjava/lang/String;[BILjava/lang/String;IJZ[BZII)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(false),
            JValue::from(&JObject::null()),
            JValue::from(&byte_array),
            JValue::from(encoding),
            JValue::from(&encoding_schema),
            JValue::from(0),
            JValue::from(0),
            JValue::from(false),
            JValue::from(&JByteArray::default()),
            JValue::from(false),
            JValue::from(0),
            JValue::from(0)
        ],
    ) {
        Ok(_) => Ok(()),
        Err(err) => {
            _ = env.exception_describe();
            Err(Error::Jni(format!("On GET callback error: {}", err)))
        }
    };

    _ = env
        .delete_local_ref(zenoh_id)
        .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(byte_array)
        .map_err(|err| tracing::debug!("Error deleting local ref: {}", err));
    result
}
