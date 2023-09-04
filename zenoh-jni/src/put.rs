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
use crate::sample::decode_sample_kind;
use crate::value::decode_value;

use jni::objects::JByteArray;
use jni::sys::jint;
use jni::JNIEnv;
use std::ops::Deref;
use std::sync::Arc;
use zenoh::prelude::r#sync::*;

/// Performs a `put` operation in the Zenoh session, propagating eventual errors.
///
/// Parameters:
/// - `env`: A mutable reference to the JNI environment.
/// - `key_expr`: The [KeyExpr] to use for the put operation.
/// - `session`: An [Session] to use for the put operation.
/// - `payload`: The payload to send through the network.
/// - `encoding`: The [Encoding] of the put operation.
/// - `congestion_control`: The [CongestionControl] mechanism specified.
/// - `priority`: The [Priority] mechanism specified.
/// - `sample_kind`: The [SampleKind] of the put operation.
///
/// Returns:
/// - A `Result` indicating the result of the `get` operation, with an [Error] in case of failure.
///
#[allow(clippy::too_many_arguments)]
pub(crate) fn on_put(
    env: &mut JNIEnv,
    key_expr: &Arc<KeyExpr<'static>>,
    session: &Arc<Session>,
    payload: JByteArray,
    encoding: jint,
    congestion_control: jint,
    priority: jint,
    sample_kind: jint,
) -> Result<()> {
    let value = decode_value(env, payload, encoding)?;
    let sample_kind = decode_sample_kind(sample_kind)?;
    let congestion_control = match decode_congestion_control(congestion_control) {
        Ok(congestion_control) => congestion_control,
        Err(err) => {
            log::warn!(
                "Error decoding congestion control: '{}'. Using default...",
                err
            );
            CongestionControl::default()
        }
    };

    let priority = match decode_priority(priority) {
        Ok(priority) => priority,
        Err(err) => {
            log::warn!("Error decoding priority: '{}'. Using default...", err);
            Priority::default()
        }
    };

    let key_expr_clone = key_expr.deref().clone();
    match session
        .put(key_expr_clone, value.to_owned())
        .kind(sample_kind)
        .congestion_control(congestion_control)
        .priority(priority)
        .res()
    {
        Ok(_) => {
            log::trace!("Put on '{key_expr}' with value '{value}' and encoding '{}'. Kind: '{sample_kind}', Congestion control: '{congestion_control:?}', Priority: '{priority:?}'", value.encoding);
            Ok(())
        }
        Err(err) => Err(Error::Session(format!("{}", err))),
    }
}

pub(crate) fn decode_priority(priority: jint) -> Result<Priority> {
    match Priority::try_from(priority as u8) {
        Ok(priority) => Ok(priority),
        Err(err) => Err(Error::Session(format!("Error retrieving priority: {err}."))),
    }
}

pub(crate) fn decode_congestion_control(congestion_control: jint) -> Result<CongestionControl> {
    match congestion_control {
        0 => Ok(CongestionControl::Block),
        1 => Ok(CongestionControl::Drop),
        _value => Err(Error::Session(format!(
            "Unknown congestion control '{_value}'."
        ))),
    }
}
