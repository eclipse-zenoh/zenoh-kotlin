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
    sys::jint,
    JNIEnv,
};
use zenoh::{
    prelude::Wait,
    publisher::Publisher,
    sample::{SampleBuilderTrait, ValueBuilderTrait},
};

use crate::throw_exception;
use crate::{
    errors::{Error, Result},
    utils::{decode_byte_array, decode_encoding},
};

/// Performs a put operation on a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `payload`: The payload to be published, represented as a Java byte array (`JByteArray`).
/// - `encoding`: The encoding type of the payload.
/// - `encoded_attachment`: Optional encoded attachment. May be null.
/// - `ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The ownership of the publisher is not transferred, and it is safe to continue using the publisher
///   after this function call.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_putViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
    encoding_id: jint,
    encoding_schema: JString,
    encoded_attachment: JByteArray,
    ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(ptr);
    let _ = || -> Result<()> {
        let payload = decode_byte_array(&env, payload)?;
        let mut publication = publisher.put(payload);
        let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
        publication = publication.encoding(encoding);
        if !encoded_attachment.is_null() {
            let attachment = decode_byte_array(&env, encoded_attachment)?;
            publication = publication.attachment::<Vec<u8>>(attachment)
        };
        publication
            .wait()
            .map_err(|err| Error::Session(err.to_string()))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(publisher);
}

/// Performs a DELETE operation on a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `encoded_attachment`: Optional encoded attachment. May be null.
/// - `ptr`: The raw pointer to the [Zenoh publisher](Publisher).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The ownership of the publisher is not transferred, and it is safe to continue using the publisher
///   after this function call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_deleteViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    encoded_attachment: JByteArray,
    ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(ptr);
    let _ = || -> Result<()> {
        let mut delete = publisher.delete();
        if !encoded_attachment.is_null() {
            let attachment = decode_byte_array(&env, encoded_attachment)?;
            delete = delete.attachment::<Vec<u8>>(attachment)
        };
        delete
            .wait()
            .map_err(|err| Error::Session(format!("{}", err)))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(publisher)
}

/// Frees the memory associated with a Zenoh publisher raw pointer via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the publisher pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const Publisher,
) {
    Arc::from_raw(ptr);
}
