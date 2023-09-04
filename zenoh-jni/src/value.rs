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
use jni::{objects::JByteArray, JNIEnv};
use zenoh::{
    buffers::{writer::Writer, ZBuf},
    prelude::{Encoding, HasWriter, KnownEncoding},
    value::Value,
};

pub(crate) fn build_value(payload: &[u8], encoding: KnownEncoding) -> Value {
    let mut zbuf = ZBuf::default();
    let mut writer = zbuf.writer();
    _ = writer.write(payload);
    Value::new(zbuf).encoding(Encoding::Exact(encoding))
}

pub(crate) fn decode_value(env: &JNIEnv<'_>, payload: JByteArray, encoding: i32) -> Result<Value> {
    let payload_len = env
        .get_array_length(&payload)
        .map(|length| length as usize)
        .map_err(|err| Error::Jni(err.to_string()))?;

    let mut buff = vec![0; payload_len];
    env.get_byte_array_region(payload, 0, &mut buff[..])
        .map_err(|err| Error::Jni(err.to_string()))?;

    let buff: Vec<u8> = buff.iter().map(|&x| x as u8).collect();
    let encoding = match KnownEncoding::try_from(encoding as u8) {
        Ok(encoding) => encoding,
        Err(_) => {
            log::debug!("Unable to retrieve encoding. Setting Empty encoding.");
            KnownEncoding::Empty
        }
    };
    Ok(build_value(&buff[..], encoding))
}
