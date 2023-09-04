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

use crate::{
    errors::{Error, Result},
    value::decode_value,
};
use jni::{
    objects::JByteArray,
    sys::{jboolean, jint, jlong},
    JNIEnv,
};
use uhlc::{Timestamp, ID, NTP64};
use zenoh::{
    prelude::{KeyExpr, SampleKind},
    sample::Sample,
};

/// Attempts to reconstruct a Zenoh [Sample] from the Java/Kotlin fields specified.
pub(crate) fn decode_sample(
    env: &mut JNIEnv,
    key_expr: KeyExpr<'static>,
    payload: JByteArray,
    encoding: jint,
    sample_kind: jint,
    timestamp_enabled: jboolean,
    timestamp_ntp_64: jlong,
) -> Result<Sample> {
    let value = decode_value(env, payload, encoding)?;
    let mut sample = Sample::new(key_expr, value);
    sample.kind = decode_sample_kind(sample_kind)?;
    sample.timestamp = if timestamp_enabled != 0 {
        Some(Timestamp::new(NTP64(timestamp_ntp_64 as u64), ID::rand()))
    } else {
        None
    };
    Ok(sample)
}

/// Converts a Java/Kotlin Integer into a [SampleKind].
pub(crate) fn decode_sample_kind(sample_kind: jint) -> Result<SampleKind> {
    match SampleKind::try_from(sample_kind as u64) {
        Ok(kind) => Ok(kind),
        Err(sample_kind) => Err(Error::Jni(format!(
            "Unable to process sample kind {}.",
            sample_kind,
        ))),
    }
}
