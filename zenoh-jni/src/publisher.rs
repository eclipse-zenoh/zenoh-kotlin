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
use zenoh::{pubsub::Publisher, Wait};

use crate::throw_exception;
use crate::{
    errors::ZResult,
    utils::{decode_byte_array, decode_encoding},
    zerror,
};

/// Performs a PUT operation on a Zenoh publisher via JNI.
///
/// # Parameters
/// - `env`: The JNI environment pointer.
/// - `_class`: The Java class reference (unused).
/// - `payload`: The byte array to be published.
/// - `encoding_id`: The encoding ID of the payload.
/// - `encoding_schema`: Nullable encoding schema string of the payload.
/// - `attachment`: Nullble byte array for the attachment.
/// - `publisher_ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// # Safety
/// - This function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - Assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The publisher pointer remains valid after this function call.
/// - May throw an exception in case of failure, which must be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_putViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
    encoding_id: jint,
    encoding_schema: /*nullable*/ JString,
    attachment: /*nullable*/ JByteArray,
    publisher_ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(publisher_ptr);
    let _ = || -> ZResult<()> {
        let payload = decode_byte_array(&env, payload)?;
        let mut publication = publisher.put(payload);
        let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
        publication = publication.encoding(encoding);
        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            publication = publication.attachment::<Vec<u8>>(attachment)
        };
        publication.wait().map_err(|err| zerror!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(publisher);
}

/// Performs a DELETE operation on a Zenoh publisher via JNI.
///
/// # Parameters
/// - `env`: The JNI environment pointer.
/// - `_class`: The Java class reference (unused).
/// - `attachment`: Nullble byte array for the attachment.
/// - `publisher_ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// # Safety
/// - This function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - Assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The publisher pointer remains valid after this function call.
/// - May throw an exception in case of failure, which must be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_deleteViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    attachment: /*nullable*/ JByteArray,
    publisher_ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(publisher_ptr);
    let _ = || -> ZResult<()> {
        let mut delete = publisher.delete();
        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            delete = delete.attachment::<Vec<u8>>(attachment)
        };
        delete.wait().map_err(|err| zerror!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(publisher)
}

/// Frees the publisher.
///
/// # Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `publisher_ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - After calling this function, the publisher pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    publisher_ptr: *const Publisher,
) {
    Arc::from_raw(publisher_ptr);
}
