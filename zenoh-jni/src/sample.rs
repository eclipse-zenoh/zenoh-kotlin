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
    objects::{JByteArray, JClass},
    sys::{jboolean, jbyte, jint, jlong},
    JNIEnv,
};
use uhlc::{Timestamp, ID, NTP64};
use zenoh::{
    prelude::{KeyExpr, SampleKind},
    sample::{QoS, Sample},
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
    qos: jbyte,
) -> Result<Sample> {
    let value = decode_value(env, payload, encoding)?;
    let mut sample = Sample::new(key_expr, value);
    sample.kind = decode_sample_kind(sample_kind)?;
    sample.timestamp = if timestamp_enabled != 0 {
        Some(Timestamp::new(NTP64(timestamp_ntp_64 as u64), ID::rand()))
    } else {
        None
    };
    sample.qos = qos_from_jbyte(qos);
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

pub fn qos_from_jbyte(qos: jbyte) -> QoS {
    unsafe { std::mem::transmute::<jbyte, QoS>(qos) }
}

pub fn qos_into_jbyte(qos: QoS) -> jbyte {
    unsafe { std::mem::transmute::<QoS, jbyte>(qos) }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_prelude_QoS_getPriorityViaJNI(
    _env: JNIEnv,
    _class: JClass,
    qos: jbyte,
) -> jint {
    qos_from_jbyte(qos).priority() as jint
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_prelude_QoS_getCongestionControlViaJNI(
    _env: JNIEnv,
    _class: JClass,
    qos: jbyte,
) -> jint {
    qos_from_jbyte(qos).congestion_control() as jint
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_prelude_QoS_getExpressdViaJNI(
    _env: JNIEnv,
    _class: JClass,
    qos: jbyte,
) -> jboolean {
    qos_from_jbyte(qos).express() as jboolean
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_prelude_QoS_00024Companion_getDefaultViaJNI(
    _env: JNIEnv,
    _class: JClass,
) -> jbyte {
    qos_into_jbyte(QoS::default())
}
