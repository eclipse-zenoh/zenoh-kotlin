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

use crate::{errors::ZResult, throw_exception, utils::decode_byte_array, zerror};
use jni::{
    objects::{JByteArray, JClass, JString},
    sys::jstring,
    JNIEnv,
};
use zenoh::session::ZenohId;

/// Returns the string representation of a ZenohID.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIZenohId_toStringViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    zenoh_id: JByteArray,
) -> jstring {
    || -> ZResult<JString> {
        let bytes = decode_byte_array(&env, zenoh_id)?;
        let zenohid = ZenohId::try_from(bytes.as_slice()).map_err(|err| zerror!(err))?;
        env.new_string(zenohid.to_string())
            .map_err(|err| zerror!(err))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        JString::default()
    })
    .as_raw()
}
