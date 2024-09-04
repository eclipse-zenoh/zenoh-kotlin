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
    errors::{self, Result},
    jni_error, session_error, throw_exception,
    utils::decode_byte_array,
};
use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jstring},
    JNIEnv,
};
use zenoh::config::ZenohId;
use zenoh_protocol::core::ZenohIdProto;

/// Returns the string representation of a ZenohID.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZenohID_toStringViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    zenoh_id: JByteArray,
) -> jstring {
    || -> Result<JString> {
        let bytes = decode_byte_array(&env, zenoh_id)?;
        let zenohIdProto =
            ZenohIdProto::try_from(bytes.as_slice()).map_err(|err| session_error!(err))?;
        let zenohid = ZenohId::from(zenohIdProto);
        env.new_string(zenohid.to_string())
            .map_err(|err| jni_error!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JString::default()
    })
    .as_raw()
}

/// Function avialable for testing purposes. Creates a default ZenohId, returning
/// its internal little endian byte array representation.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZenohID_getDefaultViaJNI(
    mut env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    {
        let id = ZenohIdProto::default();
        env.byte_array_from_slice(&id.to_le_bytes())
            .map_err(|err| jni_error!(err))
    }
    .unwrap_or_else(|err: errors::Error| {
        throw_exception!(env, err);
        JByteArray::default()
    })
    .as_raw()
}
