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
    objects::{GlobalRef, JObject, JValue},
    sys::{jint, jlong},
    JNIEnv,
};
use zenoh::{
    prelude::{SplitBuffer, ZenohId},
    query::Reply,
    sample::Sample,
    value::Value,
};

use crate::errors::Result;
use crate::{errors::Error, utils::attachment_to_vec};

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
    let kind = sample.kind.to_owned() as jint;
    let (timestamp, is_valid) = sample.timestamp.map_or_else(
        || (0, false),
        |timestamp| (timestamp.get_time().as_u64(), true),
    );

    let attachment_bytes = match sample.attachment.map_or_else(
        || env.byte_array_from_slice(&[]),
        |attachment| env.byte_array_from_slice(attachment_to_vec(attachment).as_slice()),
    ) {
        Ok(byte_array) => Ok(byte_array),
        Err(err) => Err(Error::Jni(format!(
            "Error processing attachment of reply: {}.",
            err
        ))),
    }?;

    let key_expr_ptr = Arc::into_raw(Arc::new(sample.key_expr));
    let result = match env.call_method(
        callback_global_ref,
        "run",
        "(Ljava/lang/String;ZJ[BIIJZ[B)V",
        &[
            JValue::from(&zenoh_id),
            JValue::from(true),
            JValue::from(key_expr_ptr as jlong),
            JValue::from(&byte_array),
            JValue::from(encoding),
            JValue::from(kind),
            JValue::from(timestamp as i64),
            JValue::from(is_valid),
            JValue::from(&attachment_bytes),
        ],
    ) {
        Ok(_) => Ok(()),
        Err(err) => {
            unsafe {
                Arc::from_raw(key_expr_ptr);
            }
            _ = env.exception_describe();
            Err(Error::Jni(format!("On GET callback error: {}", err)))
        }
    };

    _ = env
        .delete_local_ref(zenoh_id)
        .map_err(|err| log::debug!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(byte_array)
        .map_err(|err| log::debug!("Error deleting local ref: {}", err));
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
        .map_err(|err| log::debug!("Error deleting local ref: {}", err));
    _ = env
        .delete_local_ref(byte_array)
        .map_err(|err| log::debug!("Error deleting local ref: {}", err));
    result
}
