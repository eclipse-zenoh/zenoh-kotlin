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
    objects::{GlobalRef, JObject, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{
    prelude::{SplitBuffer, ZenohId},
    query::Reply,
    sample::Sample,
    value::Value,
};

use crate::{errors::Error, utils::attachment_to_vec};
use crate::{errors::Result, sample::qos_into_jbyte};

pub(crate) fn on_reply(
    mut env: JNIEnv,
    reply: Reply,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    match reply.sample {
        Ok(sample) => on_reply_success(&mut env, reply.replier_id, sample, callback_global_ref),
        Err(value) => on_reply_error(&mut env, reply.replier_id, value, callback_global_ref),
    }
}

fn on_reply_success(
    env: &mut JNIEnv,
    replier_id: ZenohId,
    sample: Sample,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = env
        .new_string(replier_id.to_string())
        .map_err(|err| Error::Jni(err.to_string()))?;

    let byte_array = env
        .byte_array_from_slice(sample.value.payload.contiguous().as_ref())
        .map_err(|err| Error::Jni(err.to_string()))?;

    let encoding: jint = sample.value.encoding.prefix().to_owned() as jint;
    let kind = sample.kind as jint;

    let (timestamp, is_valid) = sample
        .timestamp
        .map(|timestamp| (timestamp.get_time().as_u64(), true))
        .unwrap_or((0, false));

    let attachment_bytes = sample
        .attachment
        .map_or_else(
            || env.byte_array_from_slice(&[]),
            |attachment| env.byte_array_from_slice(attachment_to_vec(attachment).as_slice()),
        )
        .map_err(|err| Error::Jni(format!("Error processing attachment of reply: {}.", err)))?;

    let key_expr_str = env.new_string(sample.key_expr.to_string()).map_err(|err| {
        Error::Jni(format!(
            "Could not create a JString through JNI for the Sample key expression. {}",
            err
        ))
    })?;

    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZLjava/lang/String;[BIIJZB[B)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(true),
            JValue::from(&key_expr_str),
            JValue::from(&byte_array),
            JValue::from(encoding),
            JValue::from(kind),
            JValue::from(timestamp as i64),
            JValue::from(is_valid),
            JValue::from(qos_into_jbyte(sample.qos)),
            JValue::from(&attachment_bytes),
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
    value: Value,
    callback_global_ref: &GlobalRef,
) -> Result<()> {
    let zenoh_id = env
        .new_string(replier_id.to_string())
        .map_err(|err| Error::Jni(err.to_string()))?;

    let byte_array = env
        .byte_array_from_slice(value.payload.contiguous().as_ref())
        .map_err(|err| Error::Jni(err.to_string()))?;
    let encoding: jint = value.encoding.prefix().to_owned() as jint;

    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZJ[BIIJZ)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(false),
            JValue::from(&JObject::null()),
            JValue::from(&byte_array),
            JValue::from(encoding),
            JValue::from(0),
            JValue::from(0),
            JValue::from(false),
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
