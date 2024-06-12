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
    sys::{jboolean, jint},
    JNIEnv,
};
use zenoh::{
    key_expr::KeyExpr,
    prelude::Wait,
    publisher::Publisher,
    sample::{QoSBuilderTrait, SampleBuilderTrait, ValueBuilderTrait},
    session::{Session, SessionDeclarations},
};

use crate::{
    errors::{Error, Result},
    key_expr::process_kotlin_key_expr,
    utils::{decode_byte_array, decode_encoding},
};
use crate::{
    throw_exception,
    utils::{decode_congestion_control, decode_priority},
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

/// Declares a Zenoh publisher via JNI.
///
/// Parameters:
/// - `env`: A mutable reference to the JNI environment.
/// - `key_expr_ptr`: Raw pointer to the [KeyExpr] to be used for the publisher.
/// - `key_expr_str`: String representation of the [KeyExpr] to be used for the publisher.
///     It is only considered when the key_expr_ptr parameter is null, meaning the function is
///     receiving a key expression that was not declared.
/// - `session_ptr`: Raw pointer to the Zenoh [Session] to be used for the publisher.
/// - `congestion_control`: The [zenoh::publisher::CongestionControl] configuration as an ordinal.
/// - `priority`: The [zenoh::core::Priority] configuration as an ordinal.
/// - `is_express`: The express config of the publisher (see [zenoh::prelude::QoSBuilderTrait]).
///
/// Returns:
/// - A [Result] containing a raw pointer to the declared Zenoh publisher ([Publisher]) in case of success,
///   or an [Error] variant in case of failure.
///
/// Safety:
/// - The returned raw pointer should be stored appropriately and later freed using [Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI].
///
pub(crate) unsafe fn declare_publisher(
    env: &mut JNIEnv,
    key_expr_ptr: *const KeyExpr<'static>,
    key_expr_str: JString,
    session_ptr: *const Session,
    congestion_control: jint,
    priority: jint,
    is_express: jboolean,
) -> Result<*const Publisher<'static>> {
    let session = Arc::from_raw(session_ptr);
    let key_expr = process_kotlin_key_expr(env, &key_expr_str, key_expr_ptr)?;
    let congestion_control = decode_congestion_control(congestion_control)?;
    let priority = decode_priority(priority)?;
    let result = session
        .declare_publisher(key_expr)
        .congestion_control(congestion_control)
        .priority(priority)
        .express(is_express != 0)
        .wait();
    std::mem::forget(session);
    match result {
        Ok(publisher) => Ok(Arc::into_raw(Arc::new(publisher))),
        Err(err) => Err(Error::Session(err.to_string())),
    }
}

/// Modifies the congestion control policy of a running Publisher through JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Publisher class (unused but required).
/// - `congestion_control`: The [zenoh::publisher::CongestionControl] value to be set.
/// - `ptr`: Pointer to the publisher.
///
/// Safety:
/// - This function is maked as unsafe due to raw pointer manipulation.
/// - This function is NOT thread safe; if there were to be multiple threads calling this function
///   concurrently while providing the same Publisher pointer, the result will be non deterministic.
///
/// Throws:
/// - An exception in case the congestion control fails to be decoded.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_setCongestionControlViaJNI(
    env: &mut JNIEnv,
    _class: JClass,
    congestion_control: jint,
    ptr: *const Publisher<'static>,
) {
    let congestion_control = match decode_congestion_control(congestion_control) {
        Ok(congestion_control) => congestion_control,
        Err(err) => {
            _ = err.throw_on_jvm(env);
            return;
        }
    };
    tracing::debug!("Setting publisher congestion control with '{congestion_control:?}'.");
    unsafe {
        let mut publisher = core::ptr::read(ptr);
        publisher.set_congestion_control(congestion_control);
        core::ptr::write(ptr as *mut _, ())
    }
}

/// Modifies the priority policy of a running Publisher through JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Publisher class (unused but required).
/// - `priority`: The [zenoh::core::Priority] value to be set.
/// - `ptr`: Pointer to the publisher.
///
/// Safety:
/// - This function is maked as unsafe due to raw pointer manipulation.
/// - This function is NOT thread safe; if there were to be multiple threads calling this function
///   concurrently while providing the same Publisher pointer, the result will be non deterministic.
///
/// Throws:
/// - An exception in case the priority fails to be decoded.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_setPriorityViaJNI(
    env: &mut JNIEnv,
    _class: JClass,
    priority: jint,
    ptr: *const Publisher<'static>,
) {
    let priority = match decode_priority(priority) {
        Ok(priority) => priority,
        Err(err) => {
            _ = err.throw_on_jvm(env);
            return;
        }
    };
    tracing::debug!("Setting publisher priority with '{priority:?}'.");
    unsafe {
        let mut publisher = core::ptr::read(ptr);
        publisher.set_priority(priority);
        core::ptr::write(ptr as *mut _, ());
    }
}

/// Performs a DELETE operation via JNI using the specified Zenoh publisher.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `encoded_attachment`: Optional encoded attachment. May be null.
/// - `publisher`: The Zenoh [Publisher].
///
/// Returns:
/// - A [Result] indicating the success or failure of the operation.
///
fn perform_delete(
    env: &JNIEnv,
    encoded_attachment: JByteArray,
    publisher: Arc<Publisher>,
) -> Result<()> {
    let mut delete = publisher.delete();
    if !encoded_attachment.is_null() {
        let attachment = decode_byte_array(env, encoded_attachment)?;
        delete = delete.attachment::<Vec<u8>>(attachment)
    };
    delete
        .wait()
        .map_err(|err| Error::Session(format!("{}", err)))
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
    match perform_delete(&env, encoded_attachment, publisher.clone()) {
        Ok(_) => {}
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                tracing::error!(
                    "Unable to throw exception on WRITE operation failure: {}",
                    err
                )
            });
        }
    };
    std::mem::forget(publisher)
}
